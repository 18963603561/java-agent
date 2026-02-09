package com.example.agent.governance.evaluation.domain;

/**
 * 能力评估规则绑定对象。
 *
 * @param <T> 规则类型
 */
public class CapabilityRuleBinding<T> {

    /**
     * 规则实例。
     */
    private final T rule;

    /**
     * 规则元数据。
     */
    private final CapabilityRuleMetadata metadata;

    public CapabilityRuleBinding(T rule, CapabilityRuleMetadata metadata) {
        this.rule = rule;
        this.metadata = metadata;
    }

    public T getRule() {
        return rule;
    }

    public CapabilityRuleMetadata getMetadata() {
        return metadata;
    }
}

