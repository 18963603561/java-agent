package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import com.example.agent.governance.evaluation.CapabilityRiskLevel;

/**
 * 能力评估策略规则。
 */
public interface CapabilityStrategyRule {

    /**
     * 规则标识。
     *
     * @return 规则标识
     */
    String ruleId();

    /**
     * 规则版本。
     *
     * @return 版本号
     */
    default String version() {
        return "v1";
    }

    /**
     * 规则优先级，数值越小优先级越高。
     *
     * @return 优先级
     */
    default int priority() {
        return 100;
    }

    /**
     * 规则默认启用状态。
     *
     * @return 是否启用
     */
    default boolean enabled() {
        return true;
    }

    /**
     * 计算推荐策略。
     *
     * @param input 评估输入
     * @param complexityScore 复杂度评分
     * @param riskLevel 风险等级
     * @param properties 评估配置
     * @return 推荐策略，未命中时返回空
     */
    String resolve(CapabilityEvaluationInput input,
                   double complexityScore,
                   CapabilityRiskLevel riskLevel,
                   CapabilityEvaluationProperties properties);
}
