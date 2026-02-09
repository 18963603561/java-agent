package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;

/**
 * 能力评估风险规则。
 */
public interface CapabilityRiskRule {

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
     * 计算风险分值增量。
     *
     * @param input 评估输入
     * @param complexityScore 复杂度评分
     * @param properties 评估配置
     * @return 风险分值增量
     */
    double score(CapabilityEvaluationInput input,
                 double complexityScore,
                 CapabilityEvaluationProperties properties);
}
