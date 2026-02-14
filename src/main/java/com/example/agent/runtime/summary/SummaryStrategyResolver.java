package com.example.agent.runtime.summary;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 摘要策略解析器。
 *
 * <p>用途：根据请求覆盖、场景策略与全局配置解析步骤摘要构建策略。</p>
 * <p>优先级：请求级覆盖 > 场景策略 > 全局配置 > 默认语义策略。</p>
 */
@Component
public class SummaryStrategyResolver {

    /**
     * 摘要配置。
     */
    private final StepSummaryProperties summaryProperties;

    /**
     * 摘要场景配置。
     */
    private final SemanticSummaryScenarioProperties scenarioProperties;

    public SummaryStrategyResolver(StepSummaryProperties summaryProperties,
                                   SemanticSummaryScenarioProperties scenarioProperties) {
        this.summaryProperties = summaryProperties;
        this.scenarioProperties = scenarioProperties;
    }

    /**
     * 解析摘要策略决策。
     *
     * @param input 摘要构建输入
     * @param scenario 场景对象
     * @return 策略决策
     */
    public SummaryStrategyDecision resolve(StepSummaryBuildInput input, SemanticSummaryScenario scenario) {
        // 解析请求级覆盖配置。
        SummaryRequestOverrides overrides = SummaryRequestOverrides.fromInput(input);
        // 判断请求级是否指定策略，命中时优先返回。
        if (overrides != null && StringUtils.hasText(overrides.getStrategy())) {
            // 解析请求级策略，非法值回退语义策略。
            SummaryBuildStrategy strategy = SummaryBuildStrategy.resolve(overrides.getStrategy(),
                    SummaryBuildStrategy.SEMANTIC);
            // 返回请求级策略决策。
            return SummaryStrategyDecision.of(strategy, SummaryStrategySource.OVERRIDE);
        }

        // 解析场景策略配置。
        SemanticSummaryScenarioProperties.ScenarioPolicy scenarioPolicy = resolveScenarioPolicy(scenario);
        // 判断场景是否指定策略，命中时返回场景策略。
        if (scenarioPolicy != null && StringUtils.hasText(scenarioPolicy.getStrategy())) {
            // 解析场景策略，非法值回退语义策略。
            SummaryBuildStrategy strategy = SummaryBuildStrategy.resolve(scenarioPolicy.getStrategy(),
                    SummaryBuildStrategy.SEMANTIC);
            // 返回场景策略决策。
            return SummaryStrategyDecision.of(strategy, SummaryStrategySource.SCENARIO);
        }

        // 读取全局策略配置。
        String globalStrategy = summaryProperties != null ? summaryProperties.getStrategy() : null;
        // 判断全局是否指定策略，命中时返回全局策略。
        if (StringUtils.hasText(globalStrategy)) {
            // 解析全局策略，非法值回退语义策略。
            SummaryBuildStrategy strategy = SummaryBuildStrategy.resolve(globalStrategy,
                    SummaryBuildStrategy.SEMANTIC);
            // 返回全局策略决策。
            return SummaryStrategyDecision.of(strategy, SummaryStrategySource.GLOBAL);
        }

        // 返回默认语义策略决策。
        return SummaryStrategyDecision.defaultDecision();
    }

    private SemanticSummaryScenarioProperties.ScenarioPolicy resolveScenarioPolicy(SemanticSummaryScenario scenario) {
        // 判断场景配置是否为空，空时返回空策略。
        if (scenarioProperties == null) {
            // 返回空策略，避免空指针。
            return null;
        }
        // 调用场景配置解析策略。
        return scenarioProperties.resolvePolicy(scenario);
    }
}

