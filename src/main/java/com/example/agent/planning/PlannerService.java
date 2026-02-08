package com.example.agent.planning;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextAssembler;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.EvidencePack;
import com.example.agent.capabilities.context.EvidencePackService;
import com.example.agent.capabilities.context.EvidenceStats;
import com.example.agent.capabilities.context.PromptAssemblyInput;
import com.example.agent.governance.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationResult;
import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.provider.ModelRequest;
import com.example.agent.capabilities.llm.provider.ModelResponse;
import com.example.agent.capabilities.llm.provider.ModelScene;
import com.example.agent.capabilities.llm.tooling.ModelToolChoice;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.model.StepPolicy;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

/**
 * 规划服务，负责基于任务生成可执行步骤。
 * <p>用途：根据输入问题与上下文选择规划策略并生成步骤。
 * <p>输入：任务请求与租户上下文。
 * <p>输出：规划结果对象。
 * <p>边界：当禁用回退且规划失败时抛出异常。
 * <p>示例：
 * <pre>{@code
 * PlanResult plan = plannerService.plan(request, tenantContext);
 * }</pre>
 */
@Service
public class PlannerService {

    /**
     * 日志记录器。
     * <p>示例：记录规划生成结果与摘要。
     */
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    /**
     * TOOL 步骤参数校验开关，开启后缺参会触发回退。
     */
    @Value("${agent.planner.strict-tool-arguments:false}")
    private boolean strictToolArguments;


    /**
     * 模型调用服务。
     * <p>示例：调用模型生成规划步骤。
     */
    private final ModelInvocationService modelInvocationService;
    /**
     * 工具解析器。
     * <p>示例：将工具配置注入模型请求。
     */
    private final ModelToolResolver modelToolResolver;
    /**
     * 提示词装配器。
     * <p>示例：生成结构化消息列表。
     */
    private final PromptAssembler promptAssembler;
    private final JsonOutputRepairService jsonOutputRepairService;
    /**
     * 规划相关配置。
     * <p>示例：控制是否启用模型规划。
     */
    private final PlannerProperties plannerProperties;
    /**
     * 能力边界评估器。
     * <p>示例：根据风险决定是否需要审批。
     */
    private final CapabilityBoundaryEvaluator capabilityBoundaryEvaluator;
    /**
     * 序列化工具。
     * <p>示例：将上下文转换为 {@code JSON}。
     */
    private final ObjectMapper objectMapper;
    /**
     * 上下文装配器。
     * <p>示例：构建提示词所需的上下文片段。
     */
    private final ContextAssembler contextAssembler;
    /**
     * 上下文事件发布器。
     * <p>示例：发布提示词装配阶段事件。
     */
    private final ContextEventPublisher contextEventPublisher;

    /**
     * 构造规划服务。
     *
     * <p>输入：模型调用服务、工具解析器与配置对象。
     * <p>输出：初始化后的规划服务。
     * <p>示例：
     * <pre>{@code
     * new PlannerService(invocationService, toolResolver, promptAssembler, props, evaluator, mapper, assembler, publisher);
     * }</pre>
     *
     * @param modelInvocationService 模型调用服务
     * @param modelToolResolver 工具解析器
     * @param promptAssembler 提示词装配器
     * @param plannerProperties 规划配置
     * @param capabilityBoundaryEvaluator 能力评估器
     * @param objectMapper 序列化工具
     * @param contextAssembler 上下文装配器
     * @param contextEventPublisher 上下文事件发布器
     */
    public PlannerService(ModelInvocationService modelInvocationService,
                          ModelToolResolver modelToolResolver,
                          PromptAssembler promptAssembler,
                          PlannerProperties plannerProperties,
                          CapabilityBoundaryEvaluator capabilityBoundaryEvaluator,
                          ObjectMapper objectMapper,
                          ContextAssembler contextAssembler,
                          ContextEventPublisher contextEventPublisher,
                          JsonOutputRepairService jsonOutputRepairService) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.plannerProperties = plannerProperties;
        this.capabilityBoundaryEvaluator = capabilityBoundaryEvaluator;
        this.objectMapper = objectMapper;
        this.contextAssembler = contextAssembler;
        this.contextEventPublisher = contextEventPublisher;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 生成规划结果。
     *
     * <p>输入：任务请求与租户上下文。
     * <p>输出：规划结果对象。
     * <p>边界：会转调带上下文的方法，保持统一逻辑。
     * <p>示例：
     * <pre>{@code
     * PlanResult plan = plan(request, tenantContext);
     * }</pre>
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 规划结果
     */
    public PlanResult plan(TaskRequest request, TenantContext tenantContext) {
        return plan(request, tenantContext, null, null);
    }

    /**
     * 带运行上下文的规划入口，用于发布模型事件。
     *
     * <p>输入：任务请求、租户上下文与链路标识。
     * <p>输出：规划结果对象。
     * <p>边界：当模型规划失败且禁用回退时抛出异常。
     * <p>示例：
     * <pre>{@code
     * PlanResult plan = plan(request, tenantContext, workflowId, seqCounter);
     * }</pre>
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 规划结果
     */
    public PlanResult plan(TaskRequest request,
                           TenantContext tenantContext,
                           String workflowId,
                           AtomicLong seqCounter) {
        String planId = UUID.randomUUID().toString();
        String query = request != null ? request.getQuery() : null;
        Map<String, Object> context = request != null && request.getContext() != null
                ? new HashMap<>(request.getContext())
                : new HashMap<>();
        if (request != null && request.getToolChoice() != null && !context.containsKey("toolChoice")) {
            context.put("toolChoice", request.getToolChoice());
        }
        // 评估能力边界，决定是否需要审批或推荐策略。
        CapabilityEvaluationResult evaluation = evaluateCapability(request, context, tenantContext, workflowId,
                seqCounter);
        applyEvaluationToContext(context, evaluation);

        // 优先尝试模型规划，失败则回退规则规划。
        if (plannerProperties.isLlmEnabled()) {
            PlanResult llmPlan = tryLlmPlan(request, tenantContext, workflowId, seqCounter, context, planId);
            if (llmPlan != null) {
                applyApprovalRequirement(llmPlan, request, evaluation);
                return llmPlan;
            }
        }

        if (!plannerProperties.isFallbackEnabled()) {
            throw new IllegalStateException("planner_fallback_disabled");
        }
        // 规则规划作为兜底策略。
        PlanResult fallback = buildHeuristicPlan(planId, query, context, tenantContext);
        applyApprovalRequirement(fallback, request, evaluation);
        return fallback;
    }

    /**
     * 执行能力边界评估。
     *
     * <p>输入：任务请求、上下文与链路信息。
     * <p>输出：评估结果对象。
     * <p>边界：评估器未启用时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * CapabilityEvaluationResult result = evaluateCapability(request, context, ctx, wfId, seq);
     * }</pre>
     */
    private CapabilityEvaluationResult evaluateCapability(TaskRequest request,
                                                          Map<String, Object> context,
                                                          TenantContext tenantContext,
                                                          String workflowId,
                                                          AtomicLong seqCounter) {
        if (capabilityBoundaryEvaluator == null || !capabilityBoundaryEvaluator.isEnabled()) {
            return null;
        }
        // 构建评估输入，包含问题、工具摘要与预算信息。
        CapabilityEvaluationInput input = new CapabilityEvaluationInput();
        input.setTaskDescription(request != null ? request.getQuery() : null);
        input.setPlanSummary(resolvePlanSummary(context));
        input.setToolSummary(resolveToolSummary(context));
        input.setBudgetThresholdTokens(resolveBudgetThreshold(context));
        input.setFailureTypes(resolveFailureTypes(context));
        input.setComplexityScore(estimateComplexity(request != null ? request.getQuery() : null));
        return capabilityBoundaryEvaluator.evaluate(input, tenantContext, workflowId, seqCounter);
    }

    /**
     * 将评估结果写入上下文。
     *
     * <p>输入：上下文映射与评估结果。
     * <p>输出：无。
     * <p>边界：评估被跳过时不写入。
     * <p>示例：
     * <pre>{@code
     * applyEvaluationToContext(context, evaluation);
     * }</pre>
     */
    private void applyEvaluationToContext(Map<String, Object> context, CapabilityEvaluationResult evaluation) {
        if (context == null || evaluation == null || evaluation.isSkipped()) {
            return;
        }
        if (evaluation.isShouldAskApproval() && !context.containsKey("requiresApproval")) {
            context.put("requiresApproval", true);
            context.putIfAbsent("approvalSource", "evaluation");
        }
        if (!hasExplicitStrategy(context) && evaluation.getRecommendedStrategy() != null) {
            mapStrategyToContext(context, evaluation.getRecommendedStrategy());
        }
        context.put("capabilityScore", evaluation.getComplexityScore());
        context.put("capabilityRisk", evaluation.getRiskLevel() != null
                ? evaluation.getRiskLevel().name()
                : null);
    }

    /**
     * 将审批要求绑定到规划步骤。
     *
     * <p>输入：规划结果、任务请求与评估结果。
     * <p>输出：无。
     * <p>边界：无步骤或已显式指定审批时不处理。
     * <p>示例：
     * <pre>{@code
     * applyApprovalRequirement(plan, request, evaluation);
     * }</pre>
     */
    private void applyApprovalRequirement(PlanResult plan,
                                          TaskRequest request,
                                          CapabilityEvaluationResult evaluation) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return;
        }
        if (evaluation == null || evaluation.isSkipped() || !evaluation.isShouldAskApproval()) {
            return;
        }
        if (hasExplicitApproval(request) || hasExplicitApproval(plan.getSteps())) {
            return;
        }
        StepSpec first = plan.getSteps().get(0);
        markStepRequiresApproval(first, "evaluation");
    }

    /**
     * 判断任务请求是否显式要求审批。
     *
     * <p>输入：任务请求对象。
     * <p>输出：是否存在审批标记。
     * <p>示例：
     * <pre>{@code
     * boolean required = hasExplicitApproval(request);
     * }</pre>
     */
    private boolean hasExplicitApproval(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return false;
        }
        return request.getContext().containsKey("requiresApproval");
    }

    /**
     * 判断步骤列表中是否显式标记审批。
     *
     * <p>输入：步骤列表。
     * <p>输出：是否存在审批标记。
     * <p>示例：
     * <pre>{@code
     * boolean required = hasExplicitApproval(steps);
     * }</pre>
     */
    private boolean hasExplicitApproval(List<StepSpec> steps) {
        if (steps == null) {
            return false;
        }
        for (StepSpec step : steps) {
            if (step == null) {
                continue;
            }
            if (step.getRequiresApproval() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 标记步骤需要审批。
     *
     * <p>输入：步骤对象与来源标识。
     * <p>输出：无。
     * <p>边界：步骤为空时直接返回。
     * <p>示例：
     * <pre>{@code
     * markStepRequiresApproval(step, "evaluation");
     * }</pre>
     */
    private void markStepRequiresApproval(StepSpec step, String source) {
        if (step == null) {
            return;
        }
        step.setRequiresApproval(true);
        step.setApprovalSource(source);
    }

    /**
     * 判断上下文中是否显式指定策略。
     *
     * <p>输入：上下文映射。
     * <p>输出：是否存在策略字段。
     * <p>示例：
     * <pre>{@code
     * boolean explicit = hasExplicitStrategy(context);
     * }</pre>
     */
    private boolean hasExplicitStrategy(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        return context.containsKey("strategy")
                || context.containsKey("mode")
                || context.containsKey("cognitive_strategy")
                || context.containsKey("react")
                || context.containsKey("reactEnabled");
    }

    /**
     * 将推荐策略映射到上下文字段。
     *
     * <p>输入：上下文映射与策略名称。
     * <p>输出：无。
     * <p>边界：策略为空时不处理。
     * <p>示例：
     * <pre>{@code
     * mapStrategyToContext(context, "react");
     * }</pre>
     */
    private void mapStrategyToContext(Map<String, Object> context, String strategy) {
        if (context == null || strategy == null) {
            return;
        }
        String normalized = strategy.toLowerCase(Locale.ROOT);
        if ("thought_tree".equals(normalized) || "tree_of_thoughts".equals(normalized)) {
            context.put("cognitive_strategy", "tree_of_thoughts");
            return;
        }
        if ("debate".equals(normalized)) {
            context.put("strategy", "debate");
            return;
        }
        if ("research".equals(normalized)) {
            context.put("mode", "deep_research");
            context.put("strategy", "research");
            return;
        }
        if ("react".equals(normalized)) {
            context.put("react", true);
        }
    }

    /**
     * 获取上下文中的规划摘要。
     *
     * <p>输入：上下文映射。
     * <p>输出：规划摘要字符串或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String summary = resolvePlanSummary(context);
     * }</pre>
     */
    private String resolvePlanSummary(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object summary = context.get("planSummary");
        return summary instanceof String value ? value : null;
    }

    /**
     * 获取上下文中的工具摘要。
     *
     * <p>输入：上下文映射。
     * <p>输出：工具摘要字符串或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String tools = resolveToolSummary(context);
     * }</pre>
     */
    private String resolveToolSummary(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object tool = context.get("tool");
        Object toolName = context.get("toolName");
        Object fallbackTool = context.get("fallbackTool");
        StringBuilder builder = new StringBuilder();
        if (tool instanceof String value && !value.isBlank()) {
            builder.append(value);
        }
        if (toolName instanceof String value && !value.isBlank()) {
            appendWithComma(builder, value);
        }
        if (fallbackTool instanceof String value && !value.isBlank()) {
            appendWithComma(builder, value);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * 以逗号拼接字符串。
     *
     * <p>输入：字符串构建器与待拼接值。
     * <p>输出：无。
     * <p>示例：
     * <pre>{@code
     * appendWithComma(builder, "toolA");
     * }</pre>
     */
    private void appendWithComma(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(value);
    }

    /**
     * 获取预算阈值配置。
     *
     * <p>输入：上下文映射。
     * <p>输出：阈值整数，默认 {@code 0}。
     * <p>示例：
     * <pre>{@code
     * int threshold = resolveBudgetThreshold(context);
     * }</pre>
     */
    private int resolveBudgetThreshold(Map<String, Object> context) {
        if (context == null) {
            return 0;
        }
        Object threshold = context.get("budgetThresholdTokens");
        if (threshold instanceof Number number) {
            return number.intValue();
        }
        if (threshold instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    /**
     * 解析上下文中的失败类型列表。
     *
     * <p>输入：上下文映射。
     * <p>输出：失败类型列表。
     * <p>边界：解析失败时返回空列表。
     * <p>示例：
     * <pre>{@code
     * List<String> failures = resolveFailureTypes(context);
     * }</pre>
     */
    private List<String> resolveFailureTypes(Map<String, Object> context) {
        if (context == null) {
            return List.of();
        }
        Object failures = context.get("failureTypes");
        if (failures instanceof List<?> list) {
            List<String> output = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String value && !value.isBlank()) {
                    output.add(value);
                }
            }
            return output;
        }
        if (failures instanceof String text && !text.isBlank()) {
            return List.of(text.trim());
        }
        return List.of();
    }

    /**
     * 尝试使用模型生成规划。
     *
     * <p>输入：任务请求、租户上下文与上下文信息。
     * <p>输出：规划结果对象或 {@code null}。
     * <p>边界：模型输出不合法时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * PlanResult plan = tryLlmPlan(request, ctx, wfId, seq, context, planId);
     * }</pre>
     */
    private PlanResult tryLlmPlan(TaskRequest request,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  Map<String, Object> context,
                                  String planId) {
        try {
            // 构造规划提示词并生成模型请求。
            String prompt = buildPlanPrompt(request, context);
            ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.PLANNER);
            applyPromptBundle(modelRequest, prompt, request, context, tenantContext, workflowId, seqCounter);
            // 规划阶段强制注入完整工具 schema，提升参数生成可靠性
            modelToolResolver.applyTooling(modelRequest, request, null, true);
            // 调用模型生成规划内容。
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("planId", planId);
            metadata.put("promptScene", "planner");
            ModelResponse response = modelInvocationService.invoke(
                    modelRequest,
                    ModelScene.PLANNER,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    "plan",
                    metadata
            );
            if (response == null || response.getContent() == null) {
                return null;
            }
            // 解析模型输出为规划步骤。
            String rawContent = response.getContent();
            String parseErrorType = null;
            boolean repairAttempted = false;
            boolean repairSuccess = false;
            PlanParsingResult parsed;
            try {
                parsed = parsePlan(rawContent, request, context);
            } catch (Exception ex) {
                log.warn("规划解析失败, tenantId={}, planId={}, reason={}",
                        tenantContext.getTenantId(), planId, ex.getMessage());
                parsed = null;
                parseErrorType = "json_parse_error";
            }
            if (parsed == null || parsed.steps == null || parsed.steps.isEmpty()) {
                if (parseErrorType == null) {
                    parseErrorType = resolveParseErrorType(rawContent);
                }
                repairAttempted = true;
                PlanParsingResult repaired = tryRepairPlan(rawContent, request, context);
                if (repaired != null && repaired.steps != null && !repaired.steps.isEmpty()) {
                    parsed = repaired;
                    repairSuccess = true;
                }
            }
            if (parsed == null || parsed.steps == null || parsed.steps.isEmpty()) {
                log.warn("规划修复失败, tenantId={}, planId={}", tenantContext.getTenantId(), planId);
                recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), false,
                        parseErrorType, repairAttempted, repairSuccess);
                return null;
            }
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), true, null,
                    repairAttempted, repairSuccess);
            PlanResult result = new PlanResult(planId, parsed.summary, parsed.steps);
            log.info("规划生成(LLM), tenantId={}, planId={}, steps={}",
                    tenantContext.getTenantId(), planId, parsed.steps.size());
            return result;
        } catch (Exception ex) {
            log.warn("规划解析失败, tenantId={}, planId={}, reason={}",
                    tenantContext.getTenantId(), planId, ex.getMessage());
            return null;
        }
    }

    /**
     * 使用规则策略生成规划。
     *
     * <p>输入：规划标识、问题与上下文信息。
     * <p>输出：规划结果对象。
     * <p>边界：问题为空时使用默认复杂度。
     * <p>示例：
     * <pre>{@code
     * PlanResult plan = buildHeuristicPlan(planId, query, context, tenantContext);
     * }</pre>
     */
    private PlanResult buildHeuristicPlan(String planId,
                                          String query,
                                          Map<String, Object> context,
                                          TenantContext tenantContext) {
        double complexityScore = estimateComplexity(query);
        String cognitiveStrategy = resolveCognitiveStrategy(context, complexityScore);
        String executionStrategy = resolveExecutionStrategy(context, complexityScore);
            String mode = context.get("mode") instanceof String value ? value.toLowerCase(Locale.ROOT) : "";
        String strategy = context.get("strategy") instanceof String value ? value.toLowerCase(Locale.ROOT) : "";

        List<StepSpec> steps = new ArrayList<>();
        List<Map<String, Object>> planSteps = new ArrayList<>();
        List<Map<String, String>> dependencies = new ArrayList<>();

        String previousStepKey = null;
        String thoughtStepKey = null;
        // 优先处理显式链式推理策略。
        if (isChainOfThoughtRequested(mode, strategy, cognitiveStrategy)) {
            String stepKey = "step-1";
            Map<String, Object> input = new HashMap<>();
            input.put("question", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("CHAIN_OF_THOUGHT", input));
            planSteps.add(Map.of("id", stepKey, "type", "CHAIN_OF_THOUGHT", "name", "chain-of-thought"));
            String summary = String.format(Locale.ROOT,
                    "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                    executionStrategy, cognitiveStrategy, complexityScore, steps.size());
            PlanResult result = new PlanResult(planId, summary, steps);
            log.info("规划生成(链式推理), tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put("planSteps", planSteps);
            context.put("planDependencies", dependencies);
            context.put("executionStrategy", executionStrategy);
            context.put("cognitiveStrategy", cognitiveStrategy);
            return result;
        }
        // 需要思维树策略时先插入思维树步骤。
        if (needsThoughtTree(cognitiveStrategy, complexityScore)) {
            thoughtStepKey = "step-1";
            Map<String, Object> thoughtInput = new HashMap<>();
            thoughtInput.put("prompt", query);
            thoughtInput.put("stepKey", thoughtStepKey);
            thoughtInput.put("critical", Boolean.TRUE);
            thoughtInput.put("strategy", cognitiveStrategy);
            steps.add(buildStepSpec("THOUGHT_TREE", thoughtInput));
            planSteps.add(Map.of("id", thoughtStepKey, "type", "THOUGHT_TREE", "name", "thought-tree"));
            previousStepKey = thoughtStepKey;
        }

        // 多智能体策略。
        if ("multi_agent".equals(strategy) || "multi-agent".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("MULTI_AGENT", input));
            planSteps.add(Map.of("id", stepKey, "type", "MULTI_AGENT", "name", "multi-agent"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

        // 辩论策略。
        if ("debate".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("topic", query);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("DEBATE", input));
            planSteps.add(Map.of("id", stepKey, "type", "DEBATE", "name", "debate"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

        // 研究策略。
        if ("deep_research".equals(mode) || "research".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("RESEARCH", input));
            planSteps.add(Map.of("id", stepKey, "type", "RESEARCH", "name", "research"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

        // 反应式策略。
        if (shouldUseReact(context, mode, strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("REACT", input));
            planSteps.add(Map.of("id", stepKey, "type", "REACT", "name", "react"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            String summary = String.format(Locale.ROOT,
                    "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                    executionStrategy, cognitiveStrategy, complexityScore, steps.size());
            PlanResult result = new PlanResult(planId, summary, steps);
            log.info("规划生成（ReAct）, tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put("planSteps", planSteps);
            context.put("planDependencies", dependencies);
            context.put("executionStrategy", executionStrategy);
            context.put("cognitiveStrategy", cognitiveStrategy);
            return result;
        }

        if (isToolsDisabled(context)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            input.put("critical", complexityScore >= 0.6);
            input.put("strategy", cognitiveStrategy);
            if (previousStepKey != null) {
                input.put("dependsOn", List.of(previousStepKey));
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            steps.add(buildStepSpec("LLM", input));
            planSteps.add(Map.of("id", stepKey, "type", "LLM", "name", "llm"));

            String summary = String.format(Locale.ROOT,
                    "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                    executionStrategy, cognitiveStrategy, complexityScore, steps.size());
            PlanResult result = new PlanResult(planId, summary, steps);

            log.info("规划生成(大模型), tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put("planSteps", planSteps);
            context.put("planDependencies", dependencies);
            context.put("executionStrategy", executionStrategy);
            context.put("cognitiveStrategy", cognitiveStrategy);
            return result;
        }

        // 默认工具步骤。
        String toolStepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
        Map<String, Object> toolInput = new HashMap<>();
        toolInput.put("query", query);
        toolInput.put("context", context);
        toolInput.put("stepKey", toolStepKey);
        toolInput.put("critical", complexityScore >= 0.6);
        toolInput.put("strategy", cognitiveStrategy);
        Object toolName = context.get("tool");
        if (toolName instanceof String name && !name.isBlank()) {
            toolInput.put("tool", name);
        }
        Object fallbackTool = context.get("fallbackTool");
        if (fallbackTool instanceof String fallbackName && !fallbackName.isBlank()) {
            toolInput.put("fallbackTool", fallbackName);
        }
        if (previousStepKey != null) {
            toolInput.put("dependsOn", List.of(previousStepKey));
            dependencies.add(Map.of("from", previousStepKey, "to", toolStepKey));
        }
        steps.add(buildStepSpec("TOOL", toolInput));
        planSteps.add(Map.of("id", toolStepKey, "type", "TOOL", "name", "tool-exec"));

        String summary = String.format(Locale.ROOT,
                "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                executionStrategy, cognitiveStrategy, complexityScore, steps.size());
        PlanResult result = new PlanResult(planId, summary, steps);

        log.info("规划生成(规则), tenantId={}, planId={}, summary={}",
                tenantContext.getTenantId(), planId, summary);
        context.put("planSteps", planSteps);
        context.put("planDependencies", dependencies);
        context.put("executionStrategy", executionStrategy);
        context.put("cognitiveStrategy", cognitiveStrategy);
        return result;
    }

    /**
     * 构建规划提示词。
     *
     * <p>输入：任务请求与上下文映射。
     * <p>输出：提示词字符串。
     * <p>边界：序列化失败时使用空上下文。
     * <p>示例：
     * <pre>{@code
     * String prompt = buildPlanPrompt(request, context);
     * }</pre>
     */
    private String buildPlanPrompt(TaskRequest request, Map<String, Object> context) {
        Map<String, Object> promptContext = new HashMap<>();
        promptContext.put("query", request != null ? request.getQuery() : null);
        promptContext.put("contextSummary", buildContextSummary(context));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(promptContext);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
                你是任务规划器（planner）。你的任务是：根据 PLAN_CONTEXT_JSON 中的 query 与上下文，生成“最小且可执行”的步骤计划。
                
                【规划原则】
                1) 最小化：必须输出至少 1 个步骤，能少步解决就不要出步骤；能 1 步解决就不要拆 3 步。
                2) 可执行：每个步骤必须能被执行器直接执行（step.type 与 step.input 必须自洽）。
                3) 不编造：不得编造外部数据结果；若需要查询数据源，必须规划工具步骤。
                4) 区分两类问题：
                   - DIRECT：常识解释/概念说明/纯文本生成，不需要工具，必须输出至少 1 个步骤
                   - TOOL：需要外部数据/检索/数据库查询/调用系统接口，必须输出至少 1 个步骤
                
                【steps=[必须输出至少 1 个步骤]】
                - 问题属于 DIRECT（解释类、定义类、改写/总结类等），且无需任何外部数据。
                  summary 写明“无需工具，直接回答”，并可输出 answerMode="DIRECT"。
                
                --------------------------------------------------
                【关键协议（与运行时严格对齐）】
                
                只要输出 steps（即产生任意 step），必须遵守：
                
                1) steps[*].input：
                   - 必须是 object
                   - 必须包含 input.question（字符串，不能为空）
                   - input.question 表示“该步骤正在做什么 / 该步骤要处理的子问题是什么”
                   - 运行时将优先使用 input.question 作为该步骤的问题文本
                
                2) steps[*].tool：
                   - 不是必填，可省略或为空串
                   - 但当 step.type="TOOL" 时，必须保证执行器能定位到具体工具：
                     - 优先使用 steps[*].tool（若填写）
                     - 若 steps[*].tool 为空，则 steps[*].input 必须包含 toolName（字符串，不能为空）
                     - 否则该 TOOL 步骤不可执行（严禁输出）
                
                3) TOOL 步骤 input 规范：
                   - 必须包含：
                     - question: 描述本次工具调用意图（必填）
                     - arguments: object，仅包含该工具需要的字段（避免复制整段 query）
                     - toolName：当 steps[*].tool 为空时必填
                   - 不允许只有工具参数而没有 question
                   - arguments 缺失或为空时，禁止输出 TOOL 步骤
                
                4) 非 TOOL 步骤（LLM/COT/REACT） input 规范：
                   - 必须包含：
                     - question: 该步骤要生成/总结/解释的子问题（必填）
                   - 可包含少量内部处理参数（如去重字段 distinctKey），但禁止塞入长文本或重复上下文。
                
                --------------------------------------------------
                【典型模式：查询 + 汇总 / 统计 / 去重】
                
                当 query 同时包含：
                “多次查询” + “最后汇总/统计/对比/去重/总结”
                
                必须规划为：
                
                Step1..N：多个 TOOL 查询步骤（每个都要有 input.question）  
                StepN+1：一个汇总步骤（FINAL 或 THINK，必须有 input.question）
                
                汇总步骤要求：
                - type 使用 "FINAL"（若执行器不支持可用 "THINK"）
                - tool 可省略或为空串
                - dependsOn 指向所有 TOOL 步骤
                - input.question 清晰描述汇总要求（如：合并结果、按 userId 去重、统计数量并输出摘要）
                
                --------------------------------------------------
                【步骤字段要求】
                - steps[*].type：
                  - 缺省为 "LLM"
                  - 仅在需要文本生成/汇总/内部处理时使用 "FINAL"/"THINK"/"LLM"（以执行器支持为准）
                
                - steps[*].tool：
                  - 可缺省或为空串
                  - 若 type="TOOL" 且 tool 为空，则 input.toolName 必须非空
                
                - steps[*].input：
                  - 必须是 object
                  - 必须包含 question（必填）
                  - TOOL 步骤必须包含 arguments（object）；必要时包含 toolName
                
                - steps[*].dependsOn：
                  - 默认 []
                  - 有依赖时才填写
                
                --------------------------------------------------
                【输出约束】
                输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
                
                字段约束：
                1) summary: string，缺信息填空串；无法给出有效步骤时用 summary 说明原因。
                2) steps: array，缺信息填 []。
                3) steps[*].type: string，缺信息填 "TOOL"。
                4) steps[*].input: object，必须是 object，且必须包含 question。
                5) steps[*].tool: string，可缺省，缺信息填空串。
                6) steps[*].dependsOn: array，可缺省，缺信息填 []。
                
                允许额外字段但不要依赖（推荐）：
                - answerMode: "DIRECT" 或 "TOOL"
                - toolRequired: boolean
                
                当无法确定 action/step 时，输出 steps=[]，summary 写明原因。
                
                最小示例 JSON：
                {"summary":"","steps":[]}
                
                PLAN_CONTEXT_JSON:%s
                """.formatted(contextJson);


    }

    private Map<String, Object> buildContextSummary(Map<String, Object> context) {
        Map<String, Object> summary = new HashMap<>();
        String snapshotId = resolveSnapshotId(context);
        if (StringUtils.hasText(snapshotId)) {
            summary.put("snapshotId", snapshotId);
        }
        Integer tokenBudget = resolveTokenBudget(context);
        if (tokenBudget != null) {
            summary.put("tokenBudget", tokenBudget);
        }
        Integer memoryItems = resolveMemoryItems(context);
        if (memoryItems != null) {
            summary.put("memoryItems", memoryItems);
        }
        List<String> tools = resolveTools(context);
        if (!tools.isEmpty()) {
            summary.put("tools", tools);
        }
        Integer evidenceCount = resolveEvidenceCount(context);
        if (evidenceCount != null) {
            summary.put("evidenceCount", evidenceCount);
        }
        if (summary.isEmpty()) {
            summary.put("summary", "(summary disabled)");
        }
        return summary;
    }

    private String resolveSnapshotId(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get("snapshotId");
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        ContextSnapshot snapshot = resolveContextSnapshot(context);
        if (snapshot != null && StringUtils.hasText(snapshot.getSnapshotId())) {
            return snapshot.getSnapshotId();
        }
        return null;
    }

    private Integer resolveTokenBudget(Map<String, Object> context) {
        ContextBudgetAllocation allocation = resolveContextBudget(context);
        if (allocation != null && allocation.getTotalTokens() != null) {
            return allocation.getTotalTokens();
        }
        return resolveInt(context != null ? context.get("budgetThresholdTokens") : null);
    }

    private Integer resolveMemoryItems(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object memoryObj = context.get("memory");
        if (memoryObj instanceof Map<?, ?> memoryMap) {
            Integer count = resolveInt(memoryMap.get("count"));
            if (count != null) {
                return count;
            }
        }
        ContextSnapshot snapshot = resolveContextSnapshot(context);
        if (snapshot != null && snapshot.getWorkingMemory() != null) {
            return snapshot.getWorkingMemory().getWorkingMemoryItems();
        }
        return null;
    }

    private List<String> resolveTools(Map<String, Object> context) {
        if (context == null) {
            return List.of();
        }
        List<String> tools = new ArrayList<>();
        addToolName(tools, context.get("tool"));
        addToolName(tools, context.get("toolName"));
        Object toolsObj = context.get("tools");
        if (toolsObj instanceof List<?> list) {
            for (Object item : list) {
                addToolName(tools, item);
            }
        }
        return tools;
    }

    private void addToolName(List<String> tools, Object value) {
        if (tools == null || value == null) {
            return;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text) || tools.contains(text)) {
            return;
        }
        tools.add(text);
    }

    private Integer resolveEvidenceCount(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object evidence = context.get(EvidencePackService.CONTEXT_EVIDENCE_PACK);
        if (evidence instanceof EvidencePack pack) {
            EvidenceStats stats = pack.getStats();
            if (stats != null && stats.getResearchCount() != null) {
                return stats.getResearchCount();
            }
            if (stats != null && stats.getTotalCount() != null) {
                return stats.getTotalCount();
            }
            if (pack.getEvidences() != null) {
                return pack.getEvidences().size();
            }
        }
        return null;
    }

    private Integer resolveInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private PlanParsingResult tryRepairPlan(String rawContent, TaskRequest request, Map<String, Object> context) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return null;
        }
        String contextJson;
        try {
            Map<String, Object> promptContext = new HashMap<>();
            promptContext.put("query", request != null ? request.getQuery() : null);
            promptContext.put("contextSummary", buildContextSummary(context));
            contextJson = objectMapper.writeValueAsString(promptContext);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("planner", rawContent, JsonOutputSchema.PLANNER,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        try {
            return parsePlan(repaired, request, context);
        } catch (Exception ex) {
            log.warn("规划修复解析失败, reason={}", ex.getMessage());
            return null;
        }
    }

    private void recordPromptTrace(Map<String, Object> metadata,
                                   String promptText,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   String modelId,
                                   boolean parseSuccess,
                                   String parseErrorType,
                                   boolean repairAttempted,
                                   boolean repairSuccess) {
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace == null) {
            trace = PromptTrace.fromPrompt("planner", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "plan", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "missing_field";
    }

    /**
     * 应用提示词装配器并发布装配阶段事件。
     *
     * <p>输入：模型请求、提示词与上下文信息。
     * <p>输出：无。
     * <p>边界：装配器为空时直接返回。
     * <p>示例：
     * <pre>{@code
     * applyPromptBundle(modelRequest, prompt, request, context, ctx, wfId, seq);
     * }</pre>
     */
    private void applyPromptBundle(ModelRequest modelRequest,
                                   String prompt,
                                   TaskRequest request,
                                   Map<String, Object> context,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter) {
        if (promptAssembler == null || modelRequest == null) {
            return;
        }
        Map<String, Object> assemblyContext = context != null ? new HashMap<>(context) : new HashMap<>();
        PromptAssemblyInput assemblyInput = buildPromptAssemblyInput(prompt, request, assemblyContext);
        Integer beforeTokens = resolveTokenTotal(assemblyInput != null ? assemblyInput.getBudgetUsedTokens() : null);
        if (assemblyInput != null) {
            assemblyContext.put("promptAssemblyInput", assemblyInput);
        }
        // 将提示词转换为消息结构。
        PromptBundle bundle = promptAssembler.build(prompt, request, assemblyContext);
        if (bundle != null && bundle.getMessages() != null) {
            modelRequest.setMessages(bundle.getMessages());
        }
        publishPlanStage(tenantContext, workflowId, seqCounter, assemblyContext, assemblyInput, bundle,
                beforeTokens);
    }

    /**
     * 发布规划提示词装配阶段事件。
     *
     * <p>输入：租户上下文、工作流标识与装配结果。
     * <p>输出：无。
     * <p>边界：事件发布器为空时直接返回。
     * <p>示例：
     * <pre>{@code
     * publishPlanStage(ctx, wfId, seq, context, input, bundle, beforeTokens);
     * }</pre>
     */
    private void publishPlanStage(TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  Map<String, Object> context,
                                  PromptAssemblyInput assemblyInput,
                                  PromptBundle bundle,
                                  Integer beforeTokens) {
        if (contextEventPublisher == null || tenantContext == null || workflowId == null || bundle == null) {
            return;
        }
        ContextSnapshot snapshot = resolveContextSnapshot(context);
        ContextBudgetAllocation allocation = resolveContextBudget(context);
        Integer afterTokens = resolveTokenTotal(assemblyInput != null ? assemblyInput.getBudgetUsedTokens() : null);
        if (afterTokens == null) {
            afterTokens = bundle.getEstimatedTokens();
        }
        List<String> truncatedSections = bundle.getTruncatedSections() != null
                ? bundle.getTruncatedSections()
                : List.of();
        contextEventPublisher.publishSnapshotStage(
                tenantContext,
                workflowId,
                seqCounter,
                snapshot,
                null,
                allocation,
                null,
                null,
                truncatedSections,
                ContextSnapshotStage.PLAN_ASSEMBLED,
                beforeTokens,
                afterTokens);
    }

    /**
     * 计算令牌总数。
     *
     * <p>输入：令牌明细映射。
     * <p>输出：令牌总数或 {@code null}。
     * <p>边界：映射为空时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * Integer total = resolveTokenTotal(tokens);
     * }</pre>
     */
    private Integer resolveTokenTotal(Map<String, Integer> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        Integer total = tokens.get("total");
        if (total != null) {
            return total;
        }
        int sum = 0;
        for (Integer value : tokens.values()) {
            sum += value == null ? 0 : value;
        }
        return sum;
    }

    /**
     * 构建提示词装配输入。
     *
     * <p>输入：提示词、任务请求与上下文映射。
     * <p>输出：装配输入对象或 {@code null}。
     * <p>边界：装配器为空时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * PromptAssemblyInput input = buildPromptAssemblyInput(prompt, request, context);
     * }</pre>
     */
    private PromptAssemblyInput buildPromptAssemblyInput(String prompt,
                                                         TaskRequest request,
                                                         Map<String, Object> context) {
        if (contextAssembler == null) {
            return null;
        }
        ContextSnapshot snapshot = resolveContextSnapshot(context);
        ContextBudgetAllocation allocation = resolveContextBudget(context);
        ContextPruneResult pruneResult = resolveContextPrune(context);
        String tenantId = null;
        String workflowId = null;
        if (snapshot != null && snapshot.getRuntimeMeta() != null) {
            if (snapshot.getRuntimeMeta().getTenantId() != null
                    && !snapshot.getRuntimeMeta().getTenantId().isBlank()) {
                tenantId = snapshot.getRuntimeMeta().getTenantId();
            }
            if (snapshot.getRuntimeMeta().getWorkflowId() != null
                    && !snapshot.getRuntimeMeta().getWorkflowId().isBlank()) {
                workflowId = snapshot.getRuntimeMeta().getWorkflowId();
            }
        }
        if (tenantId == null && request != null && request.getContext() != null) {
            Object value = request.getContext().get("tenantId");
            if (value instanceof String text && !text.isBlank()) {
                tenantId = text;
            }
        }
        if (workflowId == null && context != null && context.get("workflowId") instanceof String text
                && !text.isBlank()) {
            workflowId = text;
        }
        return contextAssembler.assemble(snapshot, allocation, null, pruneResult, null,
                tenantId, workflowId, prompt);
    }

    /**
     * 解析上下文快照对象。
     *
     * <p>输入：上下文映射。
     * <p>输出：上下文快照对象或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * ContextSnapshot snapshot = resolveContextSnapshot(context);
     * }</pre>
     */
    private ContextSnapshot resolveContextSnapshot(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get("contextSnapshot");
        if (value instanceof ContextSnapshot snapshot) {
            return snapshot;
        }
        return null;
    }

    /**
     * 解析上下文预算分配对象。
     *
     * <p>输入：上下文映射。
     * <p>输出：预算分配对象或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * ContextBudgetAllocation allocation = resolveContextBudget(context);
     * }</pre>
     */
    private ContextBudgetAllocation resolveContextBudget(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get("contextBudget");
        if (value instanceof ContextBudgetAllocation allocation) {
            return allocation;
        }
        return null;
    }

    /**
     * 解析上下文裁剪结果对象。
     *
     * <p>输入：上下文映射。
     * <p>输出：裁剪结果对象或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * ContextPruneResult prune = resolveContextPrune(context);
     * }</pre>
     */
    private ContextPruneResult resolveContextPrune(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get("contextPrune");
        if (value instanceof ContextPruneResult pruneResult) {
            return pruneResult;
        }
        return null;
    }

    /**
     * 解析模型规划输出。
     *
     * <p>输入：模型输出内容、任务请求与上下文映射。
     * <p>输出：解析结果对象或 {@code null}。
     * <p>边界：结构不合法时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * PlanParsingResult parsed = parsePlan(content, request, context);
     * }</pre>
     */
    private PlanParsingResult parsePlan(String content, TaskRequest request, Map<String, Object> context)
            throws Exception {
        Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });
        Object stepsObj = root.get("steps");
        if (!(stepsObj instanceof List<?> stepList)) {
            return null;
        }
        List<StepSpec> steps = new ArrayList<>();
        int index = 0;
        for (Object item : stepList) {
            index++;
            if (!(item instanceof Map<?, ?> stepMap)) {
                continue;
            }
            String type = stepMap.get("type") instanceof String typeValue ? typeValue : "TOOL";
            Map<String, Object> input = new HashMap<>();
            if (stepMap.get("input") instanceof Map<?, ?> inputMap) {
                inputMap.forEach((key, value) -> input.put(String.valueOf(key), value));
            }
            if (!input.containsKey("query") && request != null) {
                input.put("query", request.getQuery());
            }
            if (!input.containsKey("context")) {
                input.put("context", context);
            }
            Object toolName = stepMap.get("tool");
            if (toolName instanceof String name && !name.isBlank()) {
                input.putIfAbsent("tool", name);
            }
            if (stepMap.get("dependsOn") instanceof List<?> deps) {
                input.putIfAbsent("dependsOn", deps);
            }
            if (isToolStep(type) && strictToolArguments) {
                String reason = validateToolStepInput(input);
                if (reason != null) {
                    log.warn("规划 TOOL 步骤缺少必要参数, stepIndex={}, reason={}", index, reason);
                    return null;
                }
            }
            steps.add(buildStepSpec(type, input));
        }
        String summary = root.get("summary") instanceof String value ? value : "llm-plan";
        return new PlanParsingResult(summary, steps);
    }

    /**
     * 从动态输入映射构建步骤规格对象。
     *
     * <p>风险点：规划输出可能包含任意字段，必须显式拆分 context/dependsOn/policy，避免参数污染。
     *
     * @param type 步骤类型
     * @param input 输入映射
     * @return 强类型步骤规格
     */
    private StepSpec buildStepSpec(String type, Map<String, Object> input) {
        StepSpec step = new StepSpec();
        step.setStepType(type);
        if (input == null || input.isEmpty()) {
            return step;
        }

        Map<String, Object> arguments = new HashMap<>(input);
        Map<String, Object> context = null;
        Object contextObj = arguments.remove("context");
        if (contextObj instanceof Map<?, ?> map) {
            Map<String, Object> contextMap = new HashMap<>();
            map.forEach((key, value) -> contextMap.put(String.valueOf(key), value));
            context = contextMap;
        }
        step.setContext(context);

        List<String> dependsOn = null;
        Object dependsObj = arguments.remove("dependsOn");
        if (dependsObj instanceof List<?> list && !list.isEmpty()) {
            dependsOn = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    dependsOn.add(String.valueOf(item));
                }
            }
            if (dependsOn.isEmpty()) {
                dependsOn = null;
            }
        }
        step.setDependsOn(dependsOn);

        Object requiresApprovalObj = arguments.remove("requiresApproval");
        Object approvalSourceObj = arguments.remove("approvalSource");
        if (requiresApprovalObj != null || approvalSourceObj != null) {
            StepPolicy policy = new StepPolicy();
            if (requiresApprovalObj instanceof Boolean boolValue) {
                policy.setRequiresApproval(boolValue);
            } else if (requiresApprovalObj instanceof String text && !text.isBlank()) {
                policy.setRequiresApproval(Boolean.parseBoolean(text));
            }
            if (approvalSourceObj != null) {
                policy.setApprovalSource(String.valueOf(approvalSourceObj));
            }
            step.setPolicy(policy);
        }

        step.setArguments(arguments.isEmpty() ? null : arguments);
        return step;
    }

    /**
     * 判断是否为 TOOL 步骤类型。
     *
     * @param type 步骤类型
     * @return 是否为 TOOL
     */
    private boolean isToolStep(String type) {
        return type != null && "TOOL".equalsIgnoreCase(type);
    }

    /**
     * 校验 TOOL 步骤的必要字段是否完整。
     *
     * <p>输入：步骤输入映射。
     * <p>输出：缺失原因，返回 {@code null} 表示校验通过。
     */
    private String validateToolStepInput(Map<String, Object> input) {
        if (input == null) {
            return "input_empty";
        }
        Object tool = input.get("tool");
        Object toolName = input.get("toolName");
        String resolvedTool = tool instanceof String value && StringUtils.hasText(value)
                ? value
                : toolName != null ? toolName.toString() : null;
        if (!StringUtils.hasText(resolvedTool)) {
            return "missing_tool_name";
        }
        Object arguments = input.get("arguments");
        if (!(arguments instanceof Map<?, ?> map) || map.isEmpty()) {
            return "missing_arguments";
        }
        return null;
    }

    /**
     * 粗略估计问题复杂度。
     *
     * <p>输入：问题文本。
     * <p>输出：复杂度分数。
     * <p>边界：文本为空时返回低复杂度。
     * <p>示例：
     * <pre>{@code
     * double score = estimateComplexity(query);
     * }</pre>
     */
    private double estimateComplexity(String query) {
        if (query == null || query.isBlank()) {
            return 0.1;
        }
        String trimmed = query.trim();
        int length = trimmed.length();
        int clauses = trimmed.split("[，。;!?\\s]+").length;
        double lengthScore = Math.min(1.0, length / 200.0);
        double clauseScore = Math.min(0.5, clauses * 0.1);
        return Math.min(1.0, lengthScore + clauseScore);
    }

    /**
     * 解析认知策略。
     *
     * <p>输入：上下文映射与复杂度分数。
     * <p>输出：策略名称字符串。
     * <p>边界：未指定时使用复杂度推断默认策略。
     * <p>示例：
     * <pre>{@code
     * String strategy = resolveCognitiveStrategy(context, score);
     * }</pre>
     */
    private String resolveCognitiveStrategy(Map<String, Object> context, double complexityScore) {
        Object strategy = context.get("strategy");
        if (strategy == null) {
            strategy = context.get("cognitive_strategy");
        }
        if (strategy instanceof String value && !value.isBlank()) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (complexityScore >= 0.7) {
            return "tree_of_thoughts";
        }
        if (complexityScore >= 0.4) {
            return "reflection";
        }
        return "simple";
    }

    /**
     * 解析执行策略。
     *
     * <p>输入：上下文映射与复杂度分数。
     * <p>输出：策略名称字符串。
     * <p>示例：
     * <pre>{@code
     * String strategy = resolveExecutionStrategy(context, score);
     * }</pre>
     */
    private String resolveExecutionStrategy(Map<String, Object> context, double complexityScore) {
        Object strategy = context.get("executionStrategy");
        if (strategy instanceof String value && !value.isBlank()) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (complexityScore >= 0.7) {
            return "sequential";
        }
        return "sequential";
    }

    /**
     * 判断是否需要思维树策略。
     *
     * <p>输入：认知策略与复杂度分数。
     * <p>输出：是否需要思维树。
     * <p>示例：
     * <pre>{@code
     * boolean needed = needsThoughtTree(strategy, score);
     * }</pre>
     */
    private boolean needsThoughtTree(String cognitiveStrategy, double complexityScore) {
        if (cognitiveStrategy == null) {
            return false;
        }
        if ("tree_of_thoughts".equalsIgnoreCase(cognitiveStrategy)
                || "tot".equalsIgnoreCase(cognitiveStrategy)) {
            return true;
        }
        return complexityScore >= 0.8;
    }

    /**
     * 判断是否启用反应式执行模式。
     *
     * <p>输入：上下文映射、模式与策略。
     * <p>输出：是否启用。
     * <p>示例：
     * <pre>{@code
     * boolean enabled = shouldUseReact(context, mode, strategy);
     * }</pre>
     */
    private boolean shouldUseReact(Map<String, Object> context, String mode, String strategy) {
        if ("react".equalsIgnoreCase(mode) || "react".equalsIgnoreCase(strategy)) {
            return true;
        }
        if (context == null) {
            return false;
        }
        Object react = context.get("react");
        if (react == null) {
            react = context.get("reactEnabled");
        }
        if (react instanceof Boolean value) {
            return value;
        }
        if (react instanceof String text && !text.isBlank()) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /**
     * 判断是否显式禁用工具。
     *
     * <p>输入：上下文映射。
     * <p>输出：是否禁用工具。
     */
    private boolean isToolsDisabled(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        if (isTruthy(context.get("disableTools"))) {
            return true;
        }
        ModelToolChoice choice = parseToolChoice(context.get("toolChoice"));
        return choice != null && choice.getMode() == ModelToolChoice.Mode.NONE;
    }

    private ModelToolChoice parseToolChoice(Object raw) {
        if (raw instanceof ModelToolChoice choice) {
            return choice;
        }
        if (raw instanceof String value) {
            return ModelToolChoice.fromString(value);
        }
        if (raw instanceof Map<?, ?> map) {
            String mode = map.get("mode") != null ? map.get("mode").toString() : null;
            if ((mode == null || mode.isBlank()) && map.get("type") != null) {
                mode = map.get("type").toString();
            }
            String name = map.get("toolName") != null ? map.get("toolName").toString() : null;
            if ((name == null || name.isBlank()) && map.get("name") != null) {
                name = map.get("name").toString();
            }
            if (mode != null && "specified".equalsIgnoreCase(mode)) {
                return ModelToolChoice.specified(name);
            }
            ModelToolChoice parsed = ModelToolChoice.fromString(mode);
            if (parsed != null && parsed.getMode() == ModelToolChoice.Mode.SPECIFIED) {
                parsed.setToolName(name);
            }
            return parsed;
        }
        return null;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /**
     * 判断是否显式请求链式推理。
     *
     * <p>输入：模式、策略与认知策略。
     * <p>输出：是否为链式推理。
     * <p>示例：
     * <pre>{@code
     * boolean enabled = isChainOfThoughtRequested(mode, strategy, cognitive);
     * }</pre>
     */
    private boolean isChainOfThoughtRequested(String mode, String strategy, String cognitiveStrategy) {
        return isChainOfThoughtValue(mode)
                || isChainOfThoughtValue(strategy)
                || isChainOfThoughtValue(cognitiveStrategy);
    }

    /**
     * 判断字符串是否表示链式推理。
     *
     * <p>输入：字符串值。
     * <p>输出：是否匹配链式推理关键字。
     * <p>示例：
     * <pre>{@code
     * boolean match = isChainOfThoughtValue("cot");
     * }</pre>
     */
    private boolean isChainOfThoughtValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "cot".equals(normalized)
                || "chain_of_thought".equals(normalized)
                || "chain-of-thought".equals(normalized);
    }

    /**
     * 规划解析结果载体。
     *
     * <p>用途：承载模型解析出的摘要与步骤列表。
     * <p>示例：{@code new PlanParsingResult("summary", steps)}。
     */
    private static class PlanParsingResult {
        private final String summary;
        private final List<StepSpec> steps;

        /**
         * 构造解析结果。
         *
         * <p>输入：摘要与步骤列表。
         * <p>输出：解析结果对象。
         * <p>示例：
         * <pre>{@code
         * new PlanParsingResult("summary", steps);
         * }</pre>
         *
         * @param summary 摘要
         * @param steps 步骤列表
         */
        private PlanParsingResult(String summary, List<StepSpec> steps) {
            this.summary = summary;
            this.steps = steps;
        }
    }
}
