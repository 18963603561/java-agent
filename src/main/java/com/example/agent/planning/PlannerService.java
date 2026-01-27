package com.example.agent.planning;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.runtime.StepRequest;
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

/**
 * 规划服务，负责基于任务生成可执行步骤。
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    private final ModelInvocationService modelInvocationService;
    private final PlannerProperties plannerProperties;
    private final ObjectMapper objectMapper;

    public PlannerService(ModelInvocationService modelInvocationService,
                          PlannerProperties plannerProperties,
                          ObjectMapper objectMapper) {
        this.modelInvocationService = modelInvocationService;
        this.plannerProperties = plannerProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成规划结果。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 规划结果
     */
    public PlanResult plan(TaskRequest request, TenantContext tenantContext) {
        return plan(request, tenantContext, null, null);
    }

    /**
     * 带运行上下文的规划入口，用于发布 LLM 事件。
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

        if (plannerProperties.isLlmEnabled()) {
            PlanResult llmPlan = tryLlmPlan(request, tenantContext, workflowId, seqCounter, context, planId);
            if (llmPlan != null) {
                return llmPlan;
            }
        }

        if (!plannerProperties.isFallbackEnabled()) {
            throw new IllegalStateException("planner_fallback_disabled");
        }
        return buildHeuristicPlan(planId, query, context, tenantContext);
    }

    private PlanResult tryLlmPlan(TaskRequest request,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  Map<String, Object> context,
                                  String planId) {
        try {
            String prompt = buildPlanPrompt(request, context);
            ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.PLANNER);
            ModelResponse response = modelInvocationService.invoke(
                    modelRequest,
                    ModelScene.PLANNER,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    "plan",
                    Map.of("planId", planId)
            );
            if (response == null || response.getContent() == null) {
                return null;
            }
            PlanParsingResult parsed = parsePlan(response.getContent(), request, context);
            if (parsed == null || parsed.steps == null || parsed.steps.isEmpty()) {
                return null;
            }
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

    private PlanResult buildHeuristicPlan(String planId,
                                          String query,
                                          Map<String, Object> context,
                                          TenantContext tenantContext) {
        double complexityScore = estimateComplexity(query);
        String cognitiveStrategy = resolveCognitiveStrategy(context, complexityScore);
        String executionStrategy = resolveExecutionStrategy(context, complexityScore);
        String mode = context.get("mode") instanceof String value ? value.toLowerCase(Locale.ROOT) : "";
        String strategy = context.get("strategy") instanceof String value ? value.toLowerCase(Locale.ROOT) : "";

        List<StepRequest> steps = new ArrayList<>();
        List<Map<String, Object>> planSteps = new ArrayList<>();
        List<Map<String, String>> dependencies = new ArrayList<>();

        String previousStepKey = null;
        String thoughtStepKey = null;
        if (needsThoughtTree(cognitiveStrategy, complexityScore)) {
            thoughtStepKey = "step-1";
            Map<String, Object> thoughtInput = new HashMap<>();
            thoughtInput.put("prompt", query);
            thoughtInput.put("stepKey", thoughtStepKey);
            thoughtInput.put("critical", Boolean.TRUE);
            thoughtInput.put("strategy", cognitiveStrategy);
            steps.add(new StepRequest("THOUGHT_TREE", thoughtInput));
            planSteps.add(Map.of("id", thoughtStepKey, "type", "THOUGHT_TREE", "name", "thought-tree"));
            previousStepKey = thoughtStepKey;
        }

        if ("multi_agent".equals(strategy) || "multi-agent".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(new StepRequest("MULTI_AGENT", input));
            planSteps.add(Map.of("id", stepKey, "type", "MULTI_AGENT", "name", "multi-agent"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

        if ("debate".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("topic", query);
            input.put("stepKey", stepKey);
            steps.add(new StepRequest("DEBATE", input));
            planSteps.add(Map.of("id", stepKey, "type", "DEBATE", "name", "debate"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

        if ("deep_research".equals(mode) || "research".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(new StepRequest("RESEARCH", input));
            planSteps.add(Map.of("id", stepKey, "type", "RESEARCH", "name", "research"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

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
        steps.add(new StepRequest("TOOL", toolInput));
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

    private String buildPlanPrompt(TaskRequest request, Map<String, Object> context) {
        Map<String, Object> promptContext = new HashMap<>();
        promptContext.put("query", request != null ? request.getQuery() : null);
        promptContext.put("context", context);
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(promptContext);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
                你是任务规划器，请基于输入生成可执行步骤。
                输出要求：仅输出 JSON，字段包含 summary 和 steps。
                steps 每项包含 type、input，可选 tool、dependsOn。
                PLAN_CONTEXT_JSON:%s
                """.formatted(contextJson);
    }

    private PlanParsingResult parsePlan(String content, TaskRequest request, Map<String, Object> context)
            throws Exception {
        Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });
        Object stepsObj = root.get("steps");
        if (!(stepsObj instanceof List<?> stepList)) {
            return null;
        }
        List<StepRequest> steps = new ArrayList<>();
        for (Object item : stepList) {
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
            steps.add(new StepRequest(type, input));
        }
        String summary = root.get("summary") instanceof String value ? value : "llm-plan";
        return new PlanParsingResult(summary, steps);
    }

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

    private static class PlanParsingResult {
        private final String summary;
        private final List<StepRequest> steps;

        private PlanParsingResult(String summary, List<StepRequest> steps) {
            this.summary = summary;
            this.steps = steps;
        }
    }
}
