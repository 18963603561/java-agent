package com.example.agent.planning.builder;

import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.PlanningFieldKeys;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.planning.parser.PlanParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规则规划构建器。
 *
 * <p>用途：负责非 LLM 场景下的启发式步骤规划与策略分支生成。
 */
@Component
public class HeuristicPlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(HeuristicPlanBuilder.class);

    private final PlanParser planParser;

    /**
     * 构造规则规划构建器。
     *
     * @param planParser 步骤构建器
     */
    public HeuristicPlanBuilder(PlanParser planParser) {
        this.planParser = planParser;
    }

    /**
     * 生成启发式规划。
     *
     * @param planId 规划标识
     * @param query 查询文本
     * @param planningContext 规划上下文
     * @param tenantId 租户标识
     * @return 规划结果
     */
    public PlanResult build(String planId,
                            String query,
                            PlanningContext planningContext,
                            String tenantId) {
        double complexityScore = estimateComplexity(query);
        String cognitiveStrategy = resolveCognitiveStrategy(planningContext, complexityScore);
        String executionStrategy = resolveExecutionStrategy(planningContext, complexityScore);
        String mode = safeLowercase(planningContext.getString(PlanningContextKeys.MODE));
        String strategy = safeLowercase(planningContext.getString(PlanningContextKeys.STRATEGY));

        List<StepSpec> steps = new ArrayList<>();
        List<Map<String, Object>> planSteps = new ArrayList<>();
        List<Map<String, String>> dependencies = new ArrayList<>();

        String previousStepKey = null;

        if (isChainOfThoughtRequested(mode, strategy, cognitiveStrategy)) {
            String stepKey = "step-1";
            Map<String, Object> input = new HashMap<>();
            input.put(PlanningFieldKeys.QUESTION, query);
            input.put(PlanningFieldKeys.CONTEXT, planningContext.mutableValues());
            input.put(PlanningFieldKeys.STEP_KEY, stepKey);
            steps.add(planParser.buildStepSpec("CHAIN_OF_THOUGHT", input));
            planSteps.add(Map.of(PlanningFieldKeys.ID,
                    stepKey,
                    PlanningFieldKeys.TYPE,
                    "CHAIN_OF_THOUGHT",
                    PlanningFieldKeys.NAME,
                    "chain-of-thought"));
            return finalizePlan(planId,
                    tenantId,
                    "链式推理",
                    planningContext,
                    executionStrategy,
                    cognitiveStrategy,
                    complexityScore,
                    steps,
                    planSteps,
                    dependencies);
        }

        if (needsThoughtTree(cognitiveStrategy, complexityScore)) {
            String thoughtStepKey = "step-1";
            Map<String, Object> thoughtInput = new HashMap<>();
            thoughtInput.put(PlanningFieldKeys.PROMPT, query);
            thoughtInput.put(PlanningFieldKeys.STEP_KEY, thoughtStepKey);
            thoughtInput.put(PlanningFieldKeys.CRITICAL, Boolean.TRUE);
            thoughtInput.put(PlanningContextKeys.STRATEGY, cognitiveStrategy);
            steps.add(planParser.buildStepSpec("THOUGHT_TREE", thoughtInput));
            planSteps.add(Map.of(PlanningFieldKeys.ID,
                    thoughtStepKey,
                    PlanningFieldKeys.TYPE,
                    "THOUGHT_TREE",
                    PlanningFieldKeys.NAME,
                    "thought-tree"));
            previousStepKey = thoughtStepKey;
        }

        if ("multi_agent".equals(strategy) || "multi-agent".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put(PlanningFieldKeys.QUERY, query);
            input.put(PlanningFieldKeys.CONTEXT, planningContext.mutableValues());
            input.put(PlanningFieldKeys.STEP_KEY, stepKey);
            steps.add(planParser.buildStepSpec("MULTI_AGENT", input));
            planSteps.add(Map.of(PlanningFieldKeys.ID,
                    stepKey,
                    PlanningFieldKeys.TYPE,
                    "MULTI_AGENT",
                    PlanningFieldKeys.NAME,
                    "multi-agent"));
            if (previousStepKey != null) {
                dependencies.add(Map.of(PlanningFieldKeys.FROM, previousStepKey, PlanningFieldKeys.TO, stepKey));
            }
            previousStepKey = stepKey;
        }

        if ("debate".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put(PlanningFieldKeys.QUERY, query);
            input.put(PlanningFieldKeys.CONTEXT, planningContext.mutableValues());
            input.put(PlanningFieldKeys.STEP_KEY, stepKey);
            steps.add(planParser.buildStepSpec("DEBATE", input));
            planSteps.add(Map.of(PlanningFieldKeys.ID,
                    stepKey,
                    PlanningFieldKeys.TYPE,
                    "DEBATE",
                    PlanningFieldKeys.NAME,
                    "debate"));
            if (previousStepKey != null) {
                dependencies.add(Map.of(PlanningFieldKeys.FROM, previousStepKey, PlanningFieldKeys.TO, stepKey));
            }
            previousStepKey = stepKey;
        }

        if ("deep_research".equals(mode) || "research".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put(PlanningFieldKeys.QUERY, query);
            input.put(PlanningFieldKeys.CONTEXT, planningContext.mutableValues());
            input.put(PlanningFieldKeys.STEP_KEY, stepKey);
            steps.add(planParser.buildStepSpec("RESEARCH", input));
            planSteps.add(Map.of(PlanningFieldKeys.ID,
                    stepKey,
                    PlanningFieldKeys.TYPE,
                    "RESEARCH",
                    PlanningFieldKeys.NAME,
                    "deep-research"));
            if (previousStepKey != null) {
                dependencies.add(Map.of(PlanningFieldKeys.FROM, previousStepKey, PlanningFieldKeys.TO, stepKey));
            }
            previousStepKey = stepKey;
        }

        if (shouldUseReact(planningContext, mode, strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put(PlanningFieldKeys.QUESTION, query);
            input.put(PlanningFieldKeys.CONTEXT, planningContext.mutableValues());
            input.put(PlanningFieldKeys.STEP_KEY, stepKey);
            input.put(PlanningContextKeys.STRATEGY, cognitiveStrategy);
            steps.add(planParser.buildStepSpec("REACT", input));
            planSteps.add(Map.of(PlanningFieldKeys.ID,
                    stepKey,
                    PlanningFieldKeys.TYPE,
                    "REACT",
                    PlanningFieldKeys.NAME,
                    "react"));
            if (previousStepKey != null) {
                dependencies.add(Map.of(PlanningFieldKeys.FROM, previousStepKey, PlanningFieldKeys.TO, stepKey));
            }
            return finalizePlan(planId,
                    tenantId,
                    "ReAct",
                    planningContext,
                    executionStrategy,
                    cognitiveStrategy,
                    complexityScore,
                    steps,
                    planSteps,
                    dependencies);
        }

        if (isToolsDisabled(planningContext)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put(PlanningFieldKeys.QUESTION, query);
            input.put(PlanningFieldKeys.CONTEXT, planningContext.mutableValues());
            input.put(PlanningFieldKeys.STEP_KEY, stepKey);
            input.put(PlanningContextKeys.STRATEGY, cognitiveStrategy);
            steps.add(planParser.buildStepSpec("LLM", input));
            planSteps.add(Map.of(PlanningFieldKeys.ID,
                    stepKey,
                    PlanningFieldKeys.TYPE,
                    "LLM",
                    PlanningFieldKeys.NAME,
                    "direct-llm"));
            if (previousStepKey != null) {
                dependencies.add(Map.of(PlanningFieldKeys.FROM, previousStepKey, PlanningFieldKeys.TO, stepKey));
            }
            return finalizePlan(planId,
                    tenantId,
                    "大模型直答",
                    planningContext,
                    executionStrategy,
                    cognitiveStrategy,
                    complexityScore,
                    steps,
                    planSteps,
                    dependencies);
        }

        String toolStepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
        Map<String, Object> toolInput = new HashMap<>();
        toolInput.put(PlanningFieldKeys.QUERY, query);
        toolInput.put(PlanningFieldKeys.CONTEXT, planningContext.mutableValues());
        toolInput.put(PlanningFieldKeys.STEP_KEY, toolStepKey);
        toolInput.put(PlanningFieldKeys.CRITICAL, complexityScore >= 0.6);
        toolInput.put(PlanningContextKeys.STRATEGY, cognitiveStrategy);
        String toolName = planningContext.getString(PlanningContextKeys.TOOL);
        if (toolName != null) {
            toolInput.put(PlanningContextKeys.TOOL, toolName);
        }
        String fallbackTool = planningContext.getString(PlanningContextKeys.FALLBACK_TOOL);
        if (fallbackTool != null) {
            toolInput.put(PlanningContextKeys.FALLBACK_TOOL, fallbackTool);
        }
        if (previousStepKey != null) {
            toolInput.put(PlanningFieldKeys.DEPENDS_ON, List.of(previousStepKey));
            dependencies.add(Map.of(PlanningFieldKeys.FROM, previousStepKey, PlanningFieldKeys.TO, toolStepKey));
        }
        steps.add(planParser.buildStepSpec("TOOL", toolInput));
        planSteps.add(Map.of(PlanningFieldKeys.ID,
                toolStepKey,
                PlanningFieldKeys.TYPE,
                "TOOL",
                PlanningFieldKeys.NAME,
                "tool-exec"));
        return finalizePlan(planId,
                tenantId,
                "规则回退",
                planningContext,
                executionStrategy,
                cognitiveStrategy,
                complexityScore,
                steps,
                planSteps,
                dependencies);
    }

    /**
     * 估算复杂度。
     *
     * @param query 查询文本
     * @return 复杂度分值
     */
    public double estimateComplexity(String query) {
        if (query == null || query.isBlank()) {
            return 0.1;
        }
        String trimmed = query.trim();
        int length = trimmed.length();
        int clauses = trimmed.split("[，。！？!?\\s]+").length;
        double lengthScore = Math.min(1.0, length / 200.0);
        double clauseScore = Math.min(0.5, clauses * 0.1);
        return Math.min(1.0, lengthScore + clauseScore);
    }

    private PlanResult finalizePlan(String planId,
                                    String tenantId,
                                    String scene,
                                    PlanningContext planningContext,
                                    String executionStrategy,
                                    String cognitiveStrategy,
                                    double complexityScore,
                                    List<StepSpec> steps,
                                    List<Map<String, Object>> planSteps,
                                    List<Map<String, String>> dependencies) {
        String summary = String.format(Locale.ROOT,
                "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                executionStrategy,
                cognitiveStrategy,
                complexityScore,
                steps.size());
        planningContext.put(PlanningContextKeys.PLAN_STEPS, planSteps);
        planningContext.put(PlanningContextKeys.PLAN_DEPENDENCIES, dependencies);
        planningContext.put(PlanningContextKeys.EXECUTION_STRATEGY, executionStrategy);
        planningContext.put(PlanningContextKeys.COGNITIVE_STRATEGY, cognitiveStrategy);
        log.info("规划生成（{}）, tenantId={}, planId={}, summary={}", scene, tenantId, planId, summary);
        return new PlanResult(planId, summary, steps);
    }

    private String resolveCognitiveStrategy(PlanningContext planningContext, double complexityScore) {
        String strategy = planningContext.getLowercaseString(PlanningContextKeys.STRATEGY);
        if (strategy == null) {
            strategy = planningContext.getLowercaseString(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY);
        }
        if (strategy != null) {
            return strategy;
        }
        if (complexityScore >= 0.7) {
            return "tree_of_thoughts";
        }
        if (complexityScore >= 0.4) {
            return "reflection";
        }
        return "simple";
    }

    private String resolveExecutionStrategy(PlanningContext planningContext, double complexityScore) {
        String strategy = planningContext.getLowercaseString(PlanningContextKeys.EXECUTION_STRATEGY);
        if (strategy != null) {
            return strategy;
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

    private boolean shouldUseReact(PlanningContext planningContext, String mode, String strategy) {
        if ("react".equalsIgnoreCase(mode) || "react".equalsIgnoreCase(strategy)) {
            return true;
        }
        Boolean react = planningContext.getBoolean(PlanningContextKeys.REACT);
        if (react == null) {
            react = planningContext.getBoolean(PlanningContextKeys.REACT_ENABLED);
        }
        return Boolean.TRUE.equals(react);
    }

    private boolean isToolsDisabled(PlanningContext planningContext) {
        Boolean disableTools = planningContext.getBoolean(PlanningContextKeys.DISABLE_TOOLS);
        if (Boolean.TRUE.equals(disableTools)) {
            return true;
        }
        var toolChoice = planningContext.getToolChoice();
        return toolChoice != null && toolChoice.getMode() == com.example.agent.capabilities.llm.contract.ModelToolChoice.Mode.NONE;
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

    private String safeLowercase(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }
}
