package com.example.agent.planning;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.context.ContextAssembler;
import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.PromptAssemblyInput;
import com.example.agent.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.evaluation.CapabilityEvaluationInput;
import com.example.agent.evaluation.CapabilityEvaluationResult;
import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextPruneResult;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.runtime.StepRequest;
import com.example.agent.streaming.ContextEventPublisher;
import com.example.agent.streaming.ContextSnapshotStage;
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
    private final ModelToolResolver modelToolResolver;
    private final PromptAssembler promptAssembler;
    private final PlannerProperties plannerProperties;
    private final CapabilityBoundaryEvaluator capabilityBoundaryEvaluator;
    private final ObjectMapper objectMapper;
    /**
     * 上下文装配器。
     */
    private final ContextAssembler contextAssembler;
    /**
     * 上下文事件发布器。
     */
    private final ContextEventPublisher contextEventPublisher;

    public PlannerService(ModelInvocationService modelInvocationService,
                          ModelToolResolver modelToolResolver,
                          PromptAssembler promptAssembler,
                          PlannerProperties plannerProperties,
                          CapabilityBoundaryEvaluator capabilityBoundaryEvaluator,
                          ObjectMapper objectMapper,
                          ContextAssembler contextAssembler,
                          ContextEventPublisher contextEventPublisher) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.plannerProperties = plannerProperties;
        this.capabilityBoundaryEvaluator = capabilityBoundaryEvaluator;
        this.objectMapper = objectMapper;
        this.contextAssembler = contextAssembler;
        this.contextEventPublisher = contextEventPublisher;
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
        CapabilityEvaluationResult evaluation = evaluateCapability(request, context, tenantContext, workflowId,
                seqCounter);
        applyEvaluationToContext(context, evaluation);

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
        PlanResult fallback = buildHeuristicPlan(planId, query, context, tenantContext);
        applyApprovalRequirement(fallback, request, evaluation);
        return fallback;
    }

    private CapabilityEvaluationResult evaluateCapability(TaskRequest request,
                                                          Map<String, Object> context,
                                                          TenantContext tenantContext,
                                                          String workflowId,
                                                          AtomicLong seqCounter) {
        if (capabilityBoundaryEvaluator == null || !capabilityBoundaryEvaluator.isEnabled()) {
            return null;
        }
        CapabilityEvaluationInput input = new CapabilityEvaluationInput();
        input.setTaskDescription(request != null ? request.getQuery() : null);
        input.setPlanSummary(resolvePlanSummary(context));
        input.setToolSummary(resolveToolSummary(context));
        input.setBudgetThresholdTokens(resolveBudgetThreshold(context));
        input.setFailureTypes(resolveFailureTypes(context));
        input.setComplexityScore(estimateComplexity(request != null ? request.getQuery() : null));
        return capabilityBoundaryEvaluator.evaluate(input, tenantContext, workflowId, seqCounter);
    }

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
        StepRequest first = plan.getSteps().get(0);
        markStepRequiresApproval(first, "evaluation");
    }

    private boolean hasExplicitApproval(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return false;
        }
        return request.getContext().containsKey("requiresApproval");
    }

    private boolean hasExplicitApproval(List<StepRequest> steps) {
        if (steps == null) {
            return false;
        }
        for (StepRequest step : steps) {
            if (step == null) {
                continue;
            }
            if (step.getRequiresApproval() != null) {
                return true;
            }
            Map<String, Object> input = step.getInput();
            if (input != null && input.containsKey("requiresApproval")) {
                return true;
            }
        }
        return false;
    }

    private void markStepRequiresApproval(StepRequest step, String source) {
        if (step == null) {
            return;
        }
        step.setRequiresApproval(true);
        step.setApprovalSource(source);
        Map<String, Object> input = step.getInput();
        if (input != null && !input.containsKey("requiresApproval")) {
            input.put("requiresApproval", true);
            input.putIfAbsent("approvalSource", source);
        }
    }

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

    private String resolvePlanSummary(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object summary = context.get("planSummary");
        return summary instanceof String value ? value : null;
    }

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

    private void appendWithComma(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(value);
    }

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

    private PlanResult tryLlmPlan(TaskRequest request,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  Map<String, Object> context,
                                  String planId) {
        try {
            String prompt = buildPlanPrompt(request, context);
            ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.PLANNER);
            applyPromptBundle(modelRequest, prompt, request, context, tenantContext, workflowId, seqCounter);
            modelToolResolver.applyTooling(modelRequest, request, null);
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
        if (isChainOfThoughtRequested(mode, strategy, cognitiveStrategy)) {
            String stepKey = "step-1";
            Map<String, Object> input = new HashMap<>();
            input.put("question", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(new StepRequest("CHAIN_OF_THOUGHT", input));
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

        if (shouldUseReact(context, mode, strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(new StepRequest("REACT", input));
            planSteps.add(Map.of("id", stepKey, "type", "REACT", "name", "react"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            String summary = String.format(Locale.ROOT,
                    "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                    executionStrategy, cognitiveStrategy, complexityScore, steps.size());
            PlanResult result = new PlanResult(planId, summary, steps);
            log.info("瑙勫垝鐢熸垚(ReAct), tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put("planSteps", planSteps);
            context.put("planDependencies", dependencies);
            context.put("executionStrategy", executionStrategy);
            context.put("cognitiveStrategy", cognitiveStrategy);
            return result;
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
        PromptBundle bundle = promptAssembler.build(prompt, request, assemblyContext);
        if (bundle != null && bundle.getMessages() != null) {
            modelRequest.setMessages(bundle.getMessages());
        }
        publishPlanStage(tenantContext, workflowId, seqCounter, assemblyContext, assemblyInput, bundle,
                beforeTokens);
    }

    /**
     * 发布规划提示词装配阶段事件。
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

    private boolean isChainOfThoughtRequested(String mode, String strategy, String cognitiveStrategy) {
        return isChainOfThoughtValue(mode)
                || isChainOfThoughtValue(strategy)
                || isChainOfThoughtValue(cognitiveStrategy);
    }

    private boolean isChainOfThoughtValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "cot".equals(normalized)
                || "chain_of_thought".equals(normalized)
                || "chain-of-thought".equals(normalized);
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
