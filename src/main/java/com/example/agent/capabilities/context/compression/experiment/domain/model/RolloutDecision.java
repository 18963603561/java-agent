package com.example.agent.capabilities.context.compression.experiment.domain.model;

/**
 * 双轨灰度决策结果。
 *
 * <p>用途：表达当前压缩请求是否进入双轨实验及命中原因，便于审计与观测复用。</p>
 */
public class RolloutDecision {

    /**
     * 是否启用双轨。
     */
    private boolean dualTrackEnabled;

    /**
     * 策略版本。
     */
    private String rolloutVersion;

    /**
     * 决策原因编码。
     */
    private String reason;

    /**
     * 命中的租户标识。
     */
    private String tenantId;

    /**
     * 命中的场景标识。
     */
    private String scene;

    /**
     * 生效比例阈值。
     */
    private Double ratio;

    /**
     * 稳定分桶键。
     */
    private String bucketKey;

    public boolean isDualTrackEnabled() {
        return dualTrackEnabled;
    }

    public void setDualTrackEnabled(boolean dualTrackEnabled) {
        this.dualTrackEnabled = dualTrackEnabled;
    }

    public String getRolloutVersion() {
        return rolloutVersion;
    }

    public void setRolloutVersion(String rolloutVersion) {
        this.rolloutVersion = rolloutVersion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public Double getRatio() {
        return ratio;
    }

    public void setRatio(Double ratio) {
        this.ratio = ratio;
    }

    public String getBucketKey() {
        return bucketKey;
    }

    public void setBucketKey(String bucketKey) {
        this.bucketKey = bucketKey;
    }
}
