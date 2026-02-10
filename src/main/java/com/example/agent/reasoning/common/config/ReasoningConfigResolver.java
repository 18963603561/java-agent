package com.example.agent.reasoning.common.config;

import com.example.agent.reasoning.thoughttree.ThoughtTreeConfig;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 推理配置解析器。
 *
 * <p>用途：统一合并“系统配置 + 输入覆盖”并输出已校验的策略配置。
 */
@Component
public class ReasoningConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(ReasoningConfigResolver.class);

    private final ReasoningExecutionProperties properties;
    private final ReasoningConfigValidator validator;

    /**
     * 构造推理配置解析器。
     *
     * @param properties 推理执行配置
     * @param validator 配置校验器
     */
    public ReasoningConfigResolver(ReasoningExecutionProperties properties,
                                   ReasoningConfigValidator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    /**
     * 解析 COT 问题长度上限。
     *
     * @return 长度上限
     */
    public int resolveCotMaxQuestionChars() {
        int value = properties.getCot().getMaxQuestionChars();
        int normalized = validator.normalizeCotMaxQuestionChars(value);
        log.debug("推理配置生效, strategy=cot, field=maxQuestionChars, value={}", normalized);
        return normalized;
    }

    /**
     * 解析 COT 步骤摘要长度上限。
     *
     * @return 长度上限
     */
    public int resolveCotMaxStepSummaryChars() {
        int value = properties.getCot().getMaxStepSummaryChars();
        int normalized = validator.normalizeCotStepSummaryChars(value);
        log.debug("推理配置生效, strategy=cot, field=maxStepSummaryChars, value={}", normalized);
        return normalized;
    }

    /**
     * 解析 Debate 结论长度上限。
     *
     * @return 长度上限
     */
    public int resolveDebateMaxConclusionChars() {
        int value = properties.getDebate().getMaxConclusionChars();
        int normalized = validator.normalizeDebateMaxConclusionChars(value);
        log.debug("推理配置生效, strategy=debate, field=maxConclusionChars, value={}", normalized);
        return normalized;
    }

    /**
     * 解析推理编排线程池大小。
     *
     * @return 线程池大小
     */
    public int resolveExecutorPoolSize() {
        int value = properties.getOrchestrator().getExecutorPoolSize();
        int normalized = validator.normalizeExecutorPoolSize(value);
        log.debug("推理配置生效, strategy=orchestrator, field=executorPoolSize, value={}", normalized);
        return normalized;
    }

    /**
     * 解析推理并行默认超时。
     *
     * @return 超时毫秒
     */
    public long resolveParallelTimeoutMillis() {
        long value = properties.getOrchestrator().getParallelTimeoutMillis();
        long normalized = validator.normalizeParallelTimeoutMillis(value);
        log.debug("推理配置生效, strategy=orchestrator, field=parallelTimeoutMillis, value={}", normalized);
        return normalized;
    }

    /**
     * 解析思维树复杂度阈值。
     *
     * @return 阈值
     */
    public double resolveThoughtTreeComplexityThreshold() {
        double value = properties.getSelection().getThoughtTreeComplexityThreshold();
        double normalized = validator.normalizeComplexityThreshold(value);
        log.debug("推理配置生效, strategy=selection, field=thoughtTreeComplexityThreshold, value={}", normalized);
        return normalized;
    }

    /**
     * 解析降级链路。
     *
     * @param primaryStrategy 主策略
     * @return 降级链路（含主策略）
     */
    public java.util.List<String> resolveFallbackOrder(String primaryStrategy) {
        String strategy = normalizeStrategy(primaryStrategy);
        ReasoningExecutionProperties.Selection selection = properties.getSelection();
        java.util.List<String> baseDefault = validator.normalizeFallbackOrder(
                selection.getDefaultFallbackOrder(),
                java.util.List.of("cot", "debate", "thought_tree"),
                "selection.defaultFallbackOrder"
        );

        java.util.List<String> configured = switch (strategy) {
            case "debate" -> validator.normalizeFallbackOrder(
                    selection.getDebateFallbackOrder(),
                    baseDefault,
                    "selection.debateFallbackOrder"
            );
            case "thought_tree" -> validator.normalizeFallbackOrder(
                    selection.getThoughtTreeFallbackOrder(),
                    baseDefault,
                    "selection.thoughtTreeFallbackOrder"
            );
            case "cot" -> validator.normalizeFallbackOrder(
                    selection.getCotFallbackOrder(),
                    baseDefault,
                    "selection.cotFallbackOrder"
            );
            default -> baseDefault;
        };

        log.debug("推理配置生效, strategy=selection, field=fallbackOrder, primaryStrategy={}, value={}",
                strategy,
                configured);
        return configured;
    }

    /**
     * 解析 ThoughtTree 配置。
     *
     * @param input 输入参数
     * @return 已校验配置
     */
    public ThoughtTreeConfig resolveThoughtTreeConfig(Map<String, Object> input) {
        ThoughtTreeConfig config = new ThoughtTreeConfig();
        ReasoningExecutionProperties.ThoughtTree defaults = properties.getThoughtTree();
        config.setMaxDepth(defaults.getMaxDepth());
        config.setBranchingFactor(defaults.getBranchingFactor());
        config.setExplorationBudget(defaults.getExplorationBudget());
        config.setPruningThreshold(defaults.getPruningThreshold());
        config.setBacktrackEnabled(defaults.isBacktrackEnabled());
        config.setEvaluationMethod(defaults.getEvaluationMethod());

        if (input != null && !input.isEmpty()) {
            Integer maxDepth = resolveInteger(input.get("maxDepth"));
            if (maxDepth != null) {
                config.setMaxDepth(maxDepth);
            }
            Integer branchingFactor = resolveInteger(input.get("branchingFactor"));
            if (branchingFactor != null) {
                config.setBranchingFactor(branchingFactor);
            }
            Integer explorationBudget = resolveInteger(input.get("explorationBudget"));
            if (explorationBudget != null) {
                config.setExplorationBudget(explorationBudget);
            }
            Double pruningThreshold = resolveDouble(input.get("pruningThreshold"));
            if (pruningThreshold != null) {
                config.setPruningThreshold(pruningThreshold);
            }
            if (input.get("backtrackEnabled") instanceof Boolean backtrackEnabled) {
                config.setBacktrackEnabled(backtrackEnabled);
            }
            if (input.get("evaluationMethod") instanceof String evaluationMethod
                    && !evaluationMethod.isBlank()) {
                config.setEvaluationMethod(evaluationMethod);
            }
        }

        ThoughtTreeConfig normalized = validator.normalizeThoughtTreeConfig(config);
        log.debug("推理配置生效, strategy=thought_tree, maxDepth={}, branchingFactor={}, explorationBudget={}, pruningThreshold={}, backtrackEnabled={}, evaluationMethod={}",
                normalized.getMaxDepth(),
                normalized.getBranchingFactor(),
                normalized.getExplorationBudget(),
                normalized.getPruningThreshold(),
                normalized.isBacktrackEnabled(),
                normalized.getEvaluationMethod());
        return normalized;
    }

    private Integer resolveInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double resolveDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return "";
        }
        return strategy.trim().toLowerCase();
    }
}
