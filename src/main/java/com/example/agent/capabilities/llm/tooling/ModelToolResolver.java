package com.example.agent.capabilities.llm.tooling;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.llm.contract.ModelToolDefinition;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.tools.model.ToolDefinition;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.tools.ToolCatalogService;
import com.example.agent.capabilities.tools.ToolQuery;
import com.example.agent.capabilities.tools.ToolSummary;
import com.example.agent.capabilities.tools.skill.SkillDefinition;
import com.example.agent.capabilities.tools.skill.SkillRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型工具解析器，负责注入工具定义与选择策略。
 */
@Component
public class ModelToolResolver {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(ModelToolResolver.class);

    /**
     * 工具注册表。
     */
    private final ToolRegistry toolRegistry;
    /**
     * 技能注册表。
     */
    private final SkillRegistry skillRegistry;
    /**
     * 工具目录服务。
     */
    private final ToolCatalogService toolCatalogService;
    /**
     * 序列化工具。
     */
    private final ObjectMapper objectMapper;
    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;
    /**
     * 工具治理上下文映射器。
     */
    private final ToolingContextMapper toolingContextMapper;

    /**
     * 工具注入模式配置。
     */
    @Value("${agent.tool.inject-mode:summary}")
    private String toolInjectMode;

    /**
     * 工具摘要最大注入数量。
     */
    @Value("${agent.tool.max-summaries:200}")
    private int maxSummaries;

    public ModelToolResolver(ToolRegistry toolRegistry,
                             SkillRegistry skillRegistry,
                             ToolCatalogService toolCatalog,
                             ObjectMapper objectMapper,
                             MetricsPublisher metricsPublisher,
                             ToolingContextMapper toolingContextMapper) {
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.toolCatalogService = toolCatalog;
        this.objectMapper = objectMapper;
        this.metricsPublisher = metricsPublisher;
        this.toolingContextMapper = toolingContextMapper;
    }

    /**
     * 将工具定义与选择策略注入模型请求。
     *
     * @param request 模型请求
     * @param taskContext LLM 任务上下文
     * @param stepInput 步骤输入
     */
    public void applyTooling(ModelRequest request, LlmTaskContext taskContext, Map<String, Object> stepInput) {
        applyTooling(request, taskContext, stepInput, false);
    }

    /**
     * 将工具定义与选择策略注入模型请求，支持强制注入完整 schema。
     *
     * <p>用途：规划场景需要完整工具参数定义时使用。</p>
     *
     * @param request 模型请求
     * @param taskContext LLM 任务上下文
     * @param stepInput 步骤输入
     * @param forceFullSchema 是否强制使用完整 schema
     */
    public void applyTooling(ModelRequest request,
                             LlmTaskContext taskContext,
                             Map<String, Object> stepInput,
                             boolean forceFullSchema) {
        if (request == null) {
            return;
        }
        long startNs = System.nanoTime();
        ToolInjectMode injectMode = forceFullSchema ? ToolInjectMode.FULL : resolveInjectMode();
        ModelToolingContext toolingContext = toolingContextMapper.toToolingContext(taskContext, stepInput);
        String tenantId = toolingContext.getTenantId();
        ModelToolChoice explicitChoice = toolingContext.getExplicitToolChoice();
        if (toolingContext.isDisableTools()) {
            request.setToolChoice(ModelToolChoice.none());
            request.setTools(List.of());
            log.info("工具已禁用, 租户={}", tenantId);
            return;
        }
        String skillName = toolingContext.getSkillName();
        SkillDefinition skillDefinition = resolveSkillDefinition(skillName);
        ToolingConstraints constraints = toolingContextMapper.toConstraints(skillDefinition);
        SkillToolPolicy skillToolPolicy = constraints.getSkillToolPolicy();
        List<String> allowedTools = skillToolPolicy.hasAllowTools() ? skillToolPolicy.getAllowTools() : null;
        ModelToolChoice skillChoice = skillToolPolicy.getToolChoice();

        List<ModelToolDefinition> tools = request.getTools();
        boolean hasTools = tools != null && !tools.isEmpty();
        if (!hasTools) {
            tools = resolveTools(injectMode);
        }
        if (allowedTools != null) {
            // 技能约束优先收敛可用工具，避免无关工具进入模型上下文。
            tools = filterTools(tools, allowedTools);
        }
        if (injectMode == ToolInjectMode.SUMMARY) {
            tools = limitSummaries(tools);
        }
        if (!hasTools || allowedTools != null) {
            if (tools != null && (!tools.isEmpty() || allowedTools != null)) {
                request.setTools(tools);
            }
        }

        if (skillChoice != null) {
            request.setToolChoice(skillChoice);
        } else if (request.getToolChoice() == null) {
            ModelToolChoice choice = explicitChoice;
            if (choice != null) {
                request.setToolChoice(choice);
            } else if (request.getTools() != null && !request.getTools().isEmpty()) {
                request.setToolChoice(ModelToolChoice.auto());
            }
        }

        if (request.getToolChoice() != null
                && request.getToolChoice().getMode() == ModelToolChoice.Mode.SPECIFIED
                && StringUtils.hasText(request.getToolChoice().getToolName())) {
            ModelToolDefinition specified = resolveToolByName(request.getToolChoice().getToolName(), injectMode, tenantId);
            if (specified != null) {
                request.setTools(List.of(specified));
            }
        }
        logToolInjection(request.getTools(), injectMode, tenantId, startNs);
    }

    

    private SkillDefinition resolveSkillDefinition(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return null;
        }
        List<SkillDefinition> definitions = skillRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        for (SkillDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getName())) {
                continue;
            }
            if (definition.getName().equalsIgnoreCase(skillName)) {
                return definition;
            }
        }
        return null;
    }

    

    private List<ModelToolDefinition> filterTools(List<ModelToolDefinition> tools, List<String> allowedTools) {
        if (tools == null) {
            return List.of();
        }
        if (allowedTools == null) {
            return tools;
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String name : allowedTools) {
            if (StringUtils.hasText(name)) {
                allowed.add(name);
            }
        }
        if (allowed.isEmpty()) {
            return List.of();
        }
        List<ModelToolDefinition> filtered = new ArrayList<>();
        for (ModelToolDefinition tool : tools) {
            if (tool != null && StringUtils.hasText(tool.getName()) && allowed.contains(tool.getName())) {
                filtered.add(tool);
            }
        }
        return filtered;
    }

    private List<ModelToolDefinition> resolveTools(ToolInjectMode injectMode) {
        if (injectMode == ToolInjectMode.FULL) {
            return resolveToolsFromDefinitions();
        }
        return resolveSummaryTools();
    }

    private List<ModelToolDefinition> resolveToolsFromDefinitions() {
        List<ToolDefinition> definitions = toolRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<ModelToolDefinition> tools = new ArrayList<>();
        for (ToolDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getName())) {
                continue;
            }
            JsonNode parameters = definition.getInputSchema() != null
                    ? objectMapper.valueToTree(definition.getInputSchema())
                    : null;
            ModelToolDefinition tool = new ModelToolDefinition(
                    definition.getName(),
                    definition.getDescription(),
                    parameters);
            tool.setTags(definition.getTags());
            tools.add(tool);
        }
        return tools;
    }

    private List<ModelToolDefinition> resolveSummaryTools() {
        if (toolCatalogService == null) {
            return List.of();
        }
        List<ToolSummary> summaries = toolCatalogService.listSummaries(new ToolQuery());
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        List<ModelToolDefinition> tools = new ArrayList<>();
        for (ToolSummary summary : summaries) {
            if (summary == null || !StringUtils.hasText(summary.getToolName())) {
                continue;
            }
            ModelToolDefinition tool = new ModelToolDefinition(
                    summary.getToolName(),
                    summary.getDescription(),
                    null);
            tool.setTags(summary.getTags());
            tool.setCostLevel(summary.getCostLevel());
            tool.setLatencyLevel(summary.getLatencyLevel());
            tool.setAuthScope(summary.getAuthScope());
            tools.add(tool);
        }
        return tools;
    }

    private ModelToolDefinition resolveToolByName(String toolName, ToolInjectMode injectMode, String tenantId) {
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        if (injectMode == ToolInjectMode.SUMMARY) {
            return resolveToolByNameOnDemand(toolName, tenantId);
        }
        return resolveToolByNameFull(toolName);
    }

    private ModelToolDefinition resolveToolByNameFull(String toolName) {
        ToolDefinition definition = toolCatalogService.getDefinition(toolName);
        if (definition == null) {
            for (ToolDefinition item : toolRegistry.listDefinitions()) {
                if (item != null && toolName.equalsIgnoreCase(item.getName())) {
                    definition = item;
                    break;
                }
            }
        }
        if (definition == null) {
            return null;
        }
        JsonNode parameters = definition.getInputSchema() != null
                ? objectMapper.valueToTree(definition.getInputSchema())
                : null;
        ModelToolDefinition tool = new ModelToolDefinition(definition.getName(), definition.getDescription(), parameters);
        tool.setTags(definition.getTags());
        return tool;
    }

    private ModelToolDefinition resolveToolByNameOnDemand(String toolName, String tenantId) {
        long startNs = System.nanoTime();
        Map<String, Object> schema = resolveToolSchema(toolName, tenantId, startNs);
        ToolDefinition definition = toolCatalogService.getDefinition(toolName);
        ModelToolDefinition tool = new ModelToolDefinition(toolName,
                definition != null ? definition.getDescription() : null,
                objectMapper.valueToTree(schema));
        if (definition != null) {
            tool.setTags(definition.getTags());
        }
        return tool;
    }

    private Map<String, Object> resolveToolSchema(String toolName, String tenantId, long startNs) {
        Map<String, Object> schema = toolCatalogService.getToolSchema(toolName);
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        if (schema == null || schema.isEmpty()) {
            if (metricsPublisher != null) {
                metricsPublisher.increment("model_tool_on_demand_schema_not_found_total");
            }
            log.error("按需加载工具结构失败, tenantId={}, toolName={}, durationMs={}, hitOrLoad=not_found",
                    tenantId, toolName, durationMs);
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "TOOL_SCHEMA_NOT_FOUND", "未找到工具输入结构");
        }
        if (metricsPublisher != null) {
            metricsPublisher.increment("model_tool_on_demand_schema_total");
        }
        log.info("按需加载工具结构完成, tenantId={}, toolName={}, durationMs={}, hitOrLoad=cache_or_load",
                tenantId, toolName, durationMs);
        return schema;
    }

    private ToolInjectMode resolveInjectMode() {
        if ("full".equalsIgnoreCase(toolInjectMode)) {
            return ToolInjectMode.FULL;
        }
        return ToolInjectMode.SUMMARY;
    }

    private List<ModelToolDefinition> limitSummaries(List<ModelToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return tools == null ? List.of() : tools;
        }
        int limit = maxSummaries > 0 ? maxSummaries : 200;
        if (tools.size() <= limit) {
            return tools;
        }
        return new ArrayList<>(tools.subList(0, limit));
    }

    private void logToolInjection(List<ModelToolDefinition> tools,
                                  ToolInjectMode injectMode,
                                  String tenantId,
                                  long startNs) {
        if (tools == null) {
            return;
        }
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        log.info("工具注入完成, tenantId={}, mode={}, summaryCount={}, durationMs={}",
                tenantId, injectMode.name().toLowerCase(java.util.Locale.ROOT), tools.size(), durationMs);
        recordSummaryMetrics(tools, injectMode);
    }

    private void recordSummaryMetrics(List<ModelToolDefinition> tools, ToolInjectMode injectMode) {
        if (metricsPublisher == null || injectMode != ToolInjectMode.SUMMARY || tools == null) {
            return;
        }
        for (ModelToolDefinition tool : tools) {
            if (tool != null && tool.getParameters() == null) {
                metricsPublisher.increment("model_tool_injected_summaries_total");
            }
        }
    }

    /**
     * 工具注入模式。
     */
    private enum ToolInjectMode {
        SUMMARY,
        FULL
    }
}
