package com.example.agent.capabilities.context.compression.experiment.domain.model;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;

/**
 * 双轨执行结果。
 *
 * <p>用途：聚合主轨与影子轨执行结果，并携带灰度决策与对比记录。</p>
 */
public class DualTrackExecutionResult {

    /**
     * 是否启用双轨。
     */
    private boolean dualTrackEnabled;

    /**
     * 灰度决策。
     */
    private RolloutDecision rolloutDecision;

    /**
     * 主轨执行结果。
     */
    private CompressionExecutionResult primaryResult;

    /**
     * 影子轨执行结果。
     */
    private CompressionExecutionResult shadowResult;

    /**
     * 对比记录。
     */
    private CompressionComparisonRecord comparisonRecord;

    public boolean isDualTrackEnabled() {
        return dualTrackEnabled;
    }

    public void setDualTrackEnabled(boolean dualTrackEnabled) {
        this.dualTrackEnabled = dualTrackEnabled;
    }

    public RolloutDecision getRolloutDecision() {
        return rolloutDecision;
    }

    public void setRolloutDecision(RolloutDecision rolloutDecision) {
        this.rolloutDecision = rolloutDecision;
    }

    public CompressionExecutionResult getPrimaryResult() {
        return primaryResult;
    }

    public void setPrimaryResult(CompressionExecutionResult primaryResult) {
        this.primaryResult = primaryResult;
    }

    public CompressionExecutionResult getShadowResult() {
        return shadowResult;
    }

    public void setShadowResult(CompressionExecutionResult shadowResult) {
        this.shadowResult = shadowResult;
    }

    public CompressionComparisonRecord getComparisonRecord() {
        return comparisonRecord;
    }

    public void setComparisonRecord(CompressionComparisonRecord comparisonRecord) {
        this.comparisonRecord = comparisonRecord;
    }
}

