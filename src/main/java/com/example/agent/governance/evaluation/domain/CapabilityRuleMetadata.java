package com.example.agent.governance.evaluation.domain;

/**
 * 能力评估规则元数据。
 */
public class CapabilityRuleMetadata {

    /**
     * 规则标识。
     */
    private final String ruleId;

    /**
     * 规则类型。
     */
    private final CapabilityRuleType ruleType;

    /**
     * 规则版本。
     */
    private final String version;

    /**
     * 规则优先级。
     */
    private final int priority;

    /**
     * 规则默认启用状态。
     */
    private final boolean enabled;

    public CapabilityRuleMetadata(String ruleId,
                                  CapabilityRuleType ruleType,
                                  String version,
                                  int priority,
                                  boolean enabled) {
        this.ruleId = ruleId;
        this.ruleType = ruleType;
        this.version = version;
        this.priority = priority;
        this.enabled = enabled;
    }

    public String getRuleId() {
        return ruleId;
    }

    public CapabilityRuleType getRuleType() {
        return ruleType;
    }

    public String getVersion() {
        return version;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

