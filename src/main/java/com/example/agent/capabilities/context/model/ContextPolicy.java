package com.example.agent.capabilities.context.model;

import java.util.List;

/**
 * 上下文装配策略。
 */
public class ContextPolicy {

    /**
     * 策略标识。
     */
    private String policyId;

    /**
     * 检索优先级顺序。
     */
    private List<String> retrievalPriority;

    /**
     * 裁剪顺序。
     */
    private List<String> pruneOrder;

    /**
     * 证据条目上限。
     */
    private Integer maxEvidenceCount;

    /**
     * 记忆条目上限。
     */
    private Integer maxMemoryCount;

    /**
     * 是否启用敏感信息脱敏。
     */
    private Boolean enableSensitiveMask;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public List<String> getRetrievalPriority() {
        return retrievalPriority;
    }

    public void setRetrievalPriority(List<String> retrievalPriority) {
        this.retrievalPriority = retrievalPriority;
    }

    public List<String> getPruneOrder() {
        return pruneOrder;
    }

    public void setPruneOrder(List<String> pruneOrder) {
        this.pruneOrder = pruneOrder;
    }

    public Integer getMaxEvidenceCount() {
        return maxEvidenceCount;
    }

    public void setMaxEvidenceCount(Integer maxEvidenceCount) {
        this.maxEvidenceCount = maxEvidenceCount;
    }

    public Integer getMaxMemoryCount() {
        return maxMemoryCount;
    }

    public void setMaxMemoryCount(Integer maxMemoryCount) {
        this.maxMemoryCount = maxMemoryCount;
    }

    public Boolean getEnableSensitiveMask() {
        return enableSensitiveMask;
    }

    public void setEnableSensitiveMask(Boolean enableSensitiveMask) {
        this.enableSensitiveMask = enableSensitiveMask;
    }
}