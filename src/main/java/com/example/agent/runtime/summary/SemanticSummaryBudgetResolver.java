package com.example.agent.runtime.summary;

import org.springframework.stereotype.Component;

/**
 * 语义摘要预算解析器。
 *
 * <p>用途：根据场景与全局配置生成摘要长度预算。</p>
 * <p>输入：摘要构建输入与场景对象。</p>
 * <p>输出：摘要预算对象。</p>
 */
@Component
public class SemanticSummaryBudgetResolver {

    /**
     * 摘要基础配置。
     */
    private final StepSummaryProperties summaryProperties;

    /**
     * 场景化配置。
     */
    private final SemanticSummaryScenarioProperties scenarioProperties;

    public SemanticSummaryBudgetResolver(StepSummaryProperties summaryProperties,
                                         SemanticSummaryScenarioProperties scenarioProperties) {
        this.summaryProperties = summaryProperties;
        this.scenarioProperties = scenarioProperties;
    }

    /**
     * 解析摘要预算。
     *
     * @param input 摘要输入
     * @param scenario 场景对象
     * @return 摘要预算
     */
    public SemanticSummaryBudget resolveBudget(StepSummaryBuildInput input,
                                               SemanticSummaryScenario scenario) {
        // 解析场景策略配置。
        SemanticSummaryScenarioProperties.ScenarioPolicy policy = resolveScenarioPolicy(scenario);
        // 解析摘要文本最大字符数。
        int maxChars = resolveLimit(policy != null ? policy.getMaxChars() : null, resolveDefaultMaxChars());
        // 解析高亮列表最大数量。
        int maxHighlights = resolveLimit(policy != null ? policy.getMaxHighlights() : null, resolveDefaultListLimit());
        // 解析未解决问题列表最大数量。
        int maxOpenQuestions = resolveLimit(policy != null ? policy.getMaxOpenQuestions() : null, resolveDefaultListLimit());
        // 解析风险列表最大数量。
        int maxRisks = resolveLimit(policy != null ? policy.getMaxRisks() : null, resolveDefaultListLimit());
        // 解析来源引用最大数量。
        int maxSourceRefs = resolveLimit(policy != null ? policy.getMaxSourceRefs() : null, resolveDefaultListLimit());
        // 解析请求级覆盖配置。
        SummaryRequestOverrides overrides = SummaryRequestOverrides.fromInput(input);
        // 应用请求级覆盖并返回摘要预算对象。
        return applyOverrides(overrides, maxChars, maxHighlights, maxOpenQuestions, maxRisks, maxSourceRefs);
    }

    private SemanticSummaryBudget applyOverrides(SummaryRequestOverrides overrides,
                                                 int maxChars,
                                                 int maxHighlights,
                                                 int maxOpenQuestions,
                                                 int maxRisks,
                                                 int maxSourceRefs) {
        // 设计意图：仅对提供的字段覆盖，未提供的字段沿用场景预算。
        // 判断覆盖配置是否为空，空时直接返回场景预算。
        if (overrides == null) {
            // 返回当前预算对象，避免空指针。
            return new SemanticSummaryBudget(maxChars, maxHighlights, maxOpenQuestions, maxRisks, maxSourceRefs);
        }
        // 读取最大字符数覆盖。
        Integer overrideMaxChars = overrides.getMaxChars();
        // 判断最大字符数覆盖是否存在，存在时应用覆盖值。
        if (overrideMaxChars != null) {
            // 应用最大字符数覆盖，负值归零。
            maxChars = Math.max(0, overrideMaxChars);
        }
        // 读取列表最大条目数覆盖。
        Integer overrideListItems = overrides.getMaxListItems();
        // 判断列表最大条目数覆盖是否存在，存在时应用到各列表预算。
        if (overrideListItems != null) {
            // 计算统一列表上限，负值归零。
            int limit = Math.max(0, overrideListItems);
            // 应用列表上限到高亮列表。
            maxHighlights = limit;
            // 应用列表上限到未解决问题列表。
            maxOpenQuestions = limit;
            // 应用列表上限到风险列表。
            maxRisks = limit;
            // 应用列表上限到来源引用列表。
            maxSourceRefs = limit;
        }
        // 返回应用覆盖后的预算对象。
        return new SemanticSummaryBudget(maxChars, maxHighlights, maxOpenQuestions, maxRisks, maxSourceRefs);
    }

    private SemanticSummaryScenarioProperties.ScenarioPolicy resolveScenarioPolicy(SemanticSummaryScenario scenario) {
        // 判断场景配置是否为空，空时返回空策略。
        if (scenarioProperties == null) {
            // 返回空策略，交由默认配置处理。
            return null;
        }
        // 调用场景配置解析策略。
        return scenarioProperties.resolvePolicy(scenario);
    }

    private int resolveDefaultMaxChars() {
        // 判断基础配置是否为空，空时返回 0 表示无限制。
        if (summaryProperties == null) {
            // 返回默认值，避免空指针。
            return 0;
        }
        // 返回基础配置的最大字符数。
        return summaryProperties.getMaxChars();
    }

    private int resolveDefaultListLimit() {
        // 判断基础配置是否为空，空时返回 0 表示无限制。
        if (summaryProperties == null) {
            // 返回默认值，避免空指针。
            return 0;
        }
        // 返回基础配置的最大列表数量。
        return summaryProperties.getMaxListItems();
    }

    private int resolveLimit(Integer preferred, int fallback) {
        // 判断是否存在优先配置，存在时使用优先值。
        if (preferred != null) {
            // 返回优先配置值，负值转为 0。
            return Math.max(0, preferred);
        }
        // 返回兜底配置值，负值转为 0。
        return Math.max(0, fallback);
    }
}
