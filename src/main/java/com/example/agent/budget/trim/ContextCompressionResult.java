package com.example.agent.budget.trim;

import com.example.agent.capabilities.context.model.ContextSnapshot;

/**
 * 上下文压缩结果，用于回传触发情况与估算指标。
 */
public class ContextCompressionResult {

    /**
     * 更新后的上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 是否触发压缩。
     */
    private boolean triggered;

    /**
     * 是否因冷却时间跳过。
     */
    private boolean skippedCooldown;

    /**
     * 触发原因。
     */
    private String triggerReason;

    /**
     * 裁剪前的令牌估算。
     */
    private Integer beforeTokens;

    /**
     * 裁剪后的令牌估算。
     */
    private Integer afterTrimTokens;

    /**
     * 压缩后的令牌估算。
     */
    private Integer afterCompressTokens;

    /**
     * 压缩耗时（毫秒）。
     */
    private Long durationMs;

    /**
     * 摘要版本。
     */
    private String summaryVersion;

    /**
     * 压缩后仍超预算。
     */
    private boolean stillOverBudget;

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public boolean isSkippedCooldown() {
        return skippedCooldown;
    }

    public void setSkippedCooldown(boolean skippedCooldown) {
        this.skippedCooldown = skippedCooldown;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public Integer getBeforeTokens() {
        return beforeTokens;
    }

    public void setBeforeTokens(Integer beforeTokens) {
        this.beforeTokens = beforeTokens;
    }

    public Integer getAfterTrimTokens() {
        return afterTrimTokens;
    }

    public void setAfterTrimTokens(Integer afterTrimTokens) {
        this.afterTrimTokens = afterTrimTokens;
    }

    public Integer getAfterCompressTokens() {
        return afterCompressTokens;
    }

    public void setAfterCompressTokens(Integer afterCompressTokens) {
        this.afterCompressTokens = afterCompressTokens;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(String summaryVersion) {
        this.summaryVersion = summaryVersion;
    }

    public boolean isStillOverBudget() {
        return stillOverBudget;
    }

    public void setStillOverBudget(boolean stillOverBudget) {
        this.stillOverBudget = stillOverBudget;
    }
}
