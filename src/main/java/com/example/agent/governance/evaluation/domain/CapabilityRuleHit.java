package com.example.agent.governance.evaluation.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 能力评估规则命中记录。
 */
public class CapabilityRuleHit {

    /**
     * 命中规则标识。
     */
    private String ruleId;

    /**
     * 命中规则类型。
     */
    private CapabilityRuleType ruleType;

    /**
     * 命中规则版本。
     */
    private String version;

    /**
     * 命中规则优先级。
     */
    private int priority;

    /**
     * 命中摘要。
     */
    private String summary;

    /**
     * 可选详细信息。
     */
    private Map<String, Object> detail;

    public CapabilityRuleHit() {
    }

    public CapabilityRuleHit(String ruleId,
                             CapabilityRuleType ruleType,
                             String version,
                             int priority,
                             String summary,
                             Map<String, Object> detail) {
        this.ruleId = ruleId;
        this.ruleType = ruleType;
        this.version = version;
        this.priority = priority;
        this.summary = summary;
        this.detail = detail != null ? new LinkedHashMap<>(detail) : new LinkedHashMap<>();
    }

    /**
     * 构建风险规则命中记录。
     *
     * @param metadata 规则元数据
     * @param delta 风险增量
     * @param complexityScore 复杂度分
     * @return 命中记录
     */
    public static CapabilityRuleHit riskHit(CapabilityRuleMetadata metadata,
                                            double delta,
                                            double complexityScore) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("delta", delta);
        detail.put("complexityScore", complexityScore);
        return new CapabilityRuleHit(
                metadata.getRuleId(),
                CapabilityRuleType.RISK,
                metadata.getVersion(),
                metadata.getPriority(),
                "delta=" + delta,
                detail);
    }

    /**
     * 构建策略规则命中记录。
     *
     * @param metadata 规则元数据
     * @param strategy 命中策略
     * @param riskLevel 风险等级
     * @param complexityScore 复杂度分
     * @return 命中记录
     */
    public static CapabilityRuleHit strategyHit(CapabilityRuleMetadata metadata,
                                                String strategy,
                                                String riskLevel,
                                                double complexityScore) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("strategy", strategy);
        detail.put("riskLevel", riskLevel);
        detail.put("complexityScore", complexityScore);
        return new CapabilityRuleHit(
                metadata.getRuleId(),
                CapabilityRuleType.STRATEGY,
                metadata.getVersion(),
                metadata.getPriority(),
                "strategy=" + strategy,
                detail);
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public CapabilityRuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(CapabilityRuleType ruleType) {
        this.ruleType = ruleType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }
}

