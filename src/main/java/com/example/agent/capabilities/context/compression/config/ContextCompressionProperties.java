package com.example.agent.budget.trim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上下文压缩触发配置，用于控制预算联动压缩策略。
 */
@Component
@ConfigurationProperties(prefix = "agent.context.compression")
public class ContextCompressionProperties {

    /**
     * 是否启用预算触发压缩。
     */
    private boolean enabled = true;

    /**
     * 是否在总预算超限时触发压缩。
     */
    private boolean triggerOverTotalBudget = true;

    /**
     * 是否在分区预算超限时触发压缩。
     */
    private boolean triggerOverSectionBudget = true;

    /**
     * 压缩触发最小间隔（秒），用于防抖。
     */
    private int minIntervalSeconds = 30;

    /**
     * 压缩模式配置。
     */
    private Mode mode = new Mode();

    /**
     * 压缩触发配置。
     */
    private Trigger trigger = new Trigger();

    /**
     * 历史窗口配置。
     */
    private Window window = new Window();

    /**
     * LLM 压缩配置。
     */
    private Llm llm = new Llm();

    /**
     * 运行基线配置。
     */
    private Baseline baseline = new Baseline();

    /**
     * 灰度发布配置。
     */
    private Rollout rollout = new Rollout();

    /**
     * 质量门禁配置。
     */
    private QualityGate qualityGate = new QualityGate();

    /**
     * 回滚治理配置。
     */
    private Rollback rollback = new Rollback();

    /**
     * 紧急控制配置。
     */
    private Emergency emergency = new Emergency();

    public boolean isEnabled() {
        return trigger != null ? trigger.isEnabled() : enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.trigger.setEnabled(enabled);
    }

    public boolean isTriggerOverTotalBudget() {
        return trigger != null ? trigger.isOverTotalBudgetEnabled() : triggerOverTotalBudget;
    }

    public void setTriggerOverTotalBudget(boolean triggerOverTotalBudget) {
        this.triggerOverTotalBudget = triggerOverTotalBudget;
        this.trigger.setOverTotalBudgetEnabled(triggerOverTotalBudget);
    }

    public boolean isTriggerOverSectionBudget() {
        return trigger != null ? trigger.isOverSectionBudgetEnabled() : triggerOverSectionBudget;
    }

    public void setTriggerOverSectionBudget(boolean triggerOverSectionBudget) {
        this.triggerOverSectionBudget = triggerOverSectionBudget;
        this.trigger.setOverSectionBudgetEnabled(triggerOverSectionBudget);
    }

    public int getMinIntervalSeconds() {
        return trigger != null ? trigger.getMinIntervalSeconds() : minIntervalSeconds;
    }

    public void setMinIntervalSeconds(int minIntervalSeconds) {
        this.minIntervalSeconds = minIntervalSeconds;
        this.trigger.setMinIntervalSeconds(minIntervalSeconds);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? new Mode() : mode;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger == null ? new Trigger() : trigger;
    }

    public Window getWindow() {
        return window;
    }

    public void setWindow(Window window) {
        this.window = window == null ? new Window() : window;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm == null ? new Llm() : llm;
    }

    public Baseline getBaseline() {
        return baseline;
    }

    public void setBaseline(Baseline baseline) {
        this.baseline = baseline == null ? new Baseline() : baseline;
    }

    public Rollout getRollout() {
        return rollout;
    }

    public void setRollout(Rollout rollout) {
        this.rollout = rollout == null ? new Rollout() : rollout;
    }

    public QualityGate getQualityGate() {
        return qualityGate;
    }

    public void setQualityGate(QualityGate qualityGate) {
        this.qualityGate = qualityGate == null ? new QualityGate() : qualityGate;
    }

    public Rollback getRollback() {
        return rollback;
    }

    public void setRollback(Rollback rollback) {
        this.rollback = rollback == null ? new Rollback() : rollback;
    }

    public Emergency getEmergency() {
        return emergency;
    }

    public void setEmergency(Emergency emergency) {
        this.emergency = emergency == null ? new Emergency() : emergency;
    }

    /**
     * 压缩模式配置。
     */
    public static class Mode {

        /**
         * 压缩模式类型（rule/llm/hybrid）。
         */
        private String type = "rule";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * 压缩触发配置。
     */
    public static class Trigger {

        /**
         * 是否启用压缩。
         */
        private boolean enabled = true;

        /**
         * 是否启用总预算触发。
         */
        private boolean overTotalBudgetEnabled = true;

        /**
         * 是否启用分段预算触发。
         */
        private boolean overSectionBudgetEnabled = true;

        /**
         * 最小压缩间隔秒数。
         */
        private int minIntervalSeconds = 30;

        /**
         * 压缩触发比例阈值。
         */
        private Double compressionTriggerRatio = 0.85D;

        /**
         * 压缩目标比例阈值。
         */
        private Double compressionTargetRatio = 0.70D;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isOverTotalBudgetEnabled() {
            return overTotalBudgetEnabled;
        }

        public void setOverTotalBudgetEnabled(boolean overTotalBudgetEnabled) {
            this.overTotalBudgetEnabled = overTotalBudgetEnabled;
        }

        public boolean isOverSectionBudgetEnabled() {
            return overSectionBudgetEnabled;
        }

        public void setOverSectionBudgetEnabled(boolean overSectionBudgetEnabled) {
            this.overSectionBudgetEnabled = overSectionBudgetEnabled;
        }

        public int getMinIntervalSeconds() {
            return minIntervalSeconds;
        }

        public void setMinIntervalSeconds(int minIntervalSeconds) {
            this.minIntervalSeconds = minIntervalSeconds;
        }

        public Double getCompressionTriggerRatio() {
            return compressionTriggerRatio;
        }

        public void setCompressionTriggerRatio(Double compressionTriggerRatio) {
            this.compressionTriggerRatio = compressionTriggerRatio;
        }

        public Double getCompressionTargetRatio() {
            return compressionTargetRatio;
        }

        public void setCompressionTargetRatio(Double compressionTargetRatio) {
            this.compressionTargetRatio = compressionTargetRatio;
        }
    }

    /**
     * 历史窗口配置。
     */
    public static class Window {

        /**
         * 首段保留条数。
         */
        private int primersCount = 2;

        /**
         * 尾段保留条数。
         */
        private int recentsCount = 6;

        public int getPrimersCount() {
            return primersCount;
        }

        public void setPrimersCount(int primersCount) {
            this.primersCount = primersCount;
        }

        public int getRecentsCount() {
            return recentsCount;
        }

        public void setRecentsCount(int recentsCount) {
            this.recentsCount = recentsCount;
        }
    }

    /**
     * LLM 压缩配置。
     */
    public static class Llm {

        /**
         * 模型场景名。
         */
        private String scene = "context_compress";

        /**
         * 超时毫秒。
         */
        private long timeoutMs = 3000L;

        /**
         * 重试次数。
         */
        private int retry = 0;

        /**
         * 失败降级策略（rule/none）。
         */
        private String fallback = "rule";

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getRetry() {
            return retry;
        }

        public void setRetry(int retry) {
            this.retry = retry;
        }

        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }
    }

    /**
     * 运行基线配置。
     */
    public static class Baseline {

        /**
         * 默认主模式。
         */
        private String defaultMode = "hybrid";

        /**
         * 默认触发比例阈值。
         */
        private Double defaultTriggerRatio = 0.75D;

        /**
         * 默认目标比例阈值。
         */
        private Double defaultTargetRatio = 0.375D;

        public String getDefaultMode() {
            return defaultMode;
        }

        public void setDefaultMode(String defaultMode) {
            this.defaultMode = defaultMode;
        }

        public Double getDefaultTriggerRatio() {
            return defaultTriggerRatio;
        }

        public void setDefaultTriggerRatio(Double defaultTriggerRatio) {
            this.defaultTriggerRatio = defaultTriggerRatio;
        }

        public Double getDefaultTargetRatio() {
            return defaultTargetRatio;
        }

        public void setDefaultTargetRatio(Double defaultTargetRatio) {
            this.defaultTargetRatio = defaultTargetRatio;
        }
    }

    /**
     * 灰度发布配置。
     */
    public static class Rollout {

        /**
         * 是否启用双轨灰度。
         */
        private boolean dualTrackEnabled;

        /**
         * 灰度策略版本。
         */
        private String version = "v1";

        /**
         * 全局灰度比例。
         */
        private Double globalRatio = 1D;

        /**
         * 灰度租户白名单。
         */
        private java.util.List<String> tenantWhitelist = new java.util.ArrayList<>();

        /**
         * 灰度场景白名单。
         */
        private java.util.List<String> sceneWhitelist = new java.util.ArrayList<>();

        public boolean isDualTrackEnabled() {
            return dualTrackEnabled;
        }

        public void setDualTrackEnabled(boolean dualTrackEnabled) {
            this.dualTrackEnabled = dualTrackEnabled;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Double getGlobalRatio() {
            return globalRatio;
        }

        public void setGlobalRatio(Double globalRatio) {
            this.globalRatio = globalRatio;
        }

        public java.util.List<String> getTenantWhitelist() {
            return tenantWhitelist;
        }

        public void setTenantWhitelist(java.util.List<String> tenantWhitelist) {
            this.tenantWhitelist = tenantWhitelist == null ? new java.util.ArrayList<>() : tenantWhitelist;
        }

        public java.util.List<String> getSceneWhitelist() {
            return sceneWhitelist;
        }

        public void setSceneWhitelist(java.util.List<String> sceneWhitelist) {
            this.sceneWhitelist = sceneWhitelist == null ? new java.util.ArrayList<>() : sceneWhitelist;
        }
    }

    /**
     * 质量门禁配置。
     */
    public static class QualityGate {

        /**
         * 质量门禁版本。
         */
        private String version = "v1";

        /**
         * 最低质量分阈值，区间 [0,1]。
         */
        private Double minScore = 0.75D;

        /**
         * 最大解析失败率阈值，区间 [0,1]。
         */
        private Double maxParseFailureRate = 0.03D;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Double getMinScore() {
            return minScore;
        }

        public void setMinScore(Double minScore) {
            this.minScore = minScore;
        }

        public Double getMaxParseFailureRate() {
            return maxParseFailureRate;
        }

        public void setMaxParseFailureRate(Double maxParseFailureRate) {
            this.maxParseFailureRate = maxParseFailureRate;
        }
    }

    /**
     * 回滚治理配置。
     */
    public static class Rollback {

        /**
         * 回滚策略版本。
         */
        private String version = "v1";

        /**
         * 是否启用自动回滚。
         */
        private boolean autoEnabled = true;

        /**
         * 触发窗口分钟数。
         */
        private int triggerWindowMinutes = 10;

        /**
         * 触发窗口最小样本数。
         */
        private int triggerWindowMinSamples = 5;

        /**
         * 最大失败率阈值。
         */
        private Double maxFailureRate = 0.40D;

        /**
         * 最大低分率阈值。
         */
        private Double maxLowScoreRate = 0.40D;

        /**
         * 最大超时率阈值。
         */
        private Double maxTimeoutRate = 0.30D;

        /**
         * 回滚恢复等待分钟数。
         */
        private int recoveryWaitMinutes = 10;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public boolean isAutoEnabled() {
            return autoEnabled;
        }

        public void setAutoEnabled(boolean autoEnabled) {
            this.autoEnabled = autoEnabled;
        }

        public int getTriggerWindowMinutes() {
            return triggerWindowMinutes;
        }

        public void setTriggerWindowMinutes(int triggerWindowMinutes) {
            this.triggerWindowMinutes = triggerWindowMinutes;
        }

        public int getTriggerWindowMinSamples() {
            return triggerWindowMinSamples;
        }

        public void setTriggerWindowMinSamples(int triggerWindowMinSamples) {
            this.triggerWindowMinSamples = triggerWindowMinSamples;
        }

        public Double getMaxFailureRate() {
            return maxFailureRate;
        }

        public void setMaxFailureRate(Double maxFailureRate) {
            this.maxFailureRate = maxFailureRate;
        }

        public Double getMaxLowScoreRate() {
            return maxLowScoreRate;
        }

        public void setMaxLowScoreRate(Double maxLowScoreRate) {
            this.maxLowScoreRate = maxLowScoreRate;
        }

        public Double getMaxTimeoutRate() {
            return maxTimeoutRate;
        }

        public void setMaxTimeoutRate(Double maxTimeoutRate) {
            this.maxTimeoutRate = maxTimeoutRate;
        }

        public int getRecoveryWaitMinutes() {
            return recoveryWaitMinutes;
        }

        public void setRecoveryWaitMinutes(int recoveryWaitMinutes) {
            this.recoveryWaitMinutes = recoveryWaitMinutes;
        }
    }

    /**
     * 紧急控制配置。
     */
    public static class Emergency {

        /**
         * 是否强制使用规则模式。
         */
        private boolean forceRuleMode;

        /**
         * 是否禁用压缩能力。
         */
        private boolean disableCompression;

        /**
         * 是否绕过质量门禁。
         */
        private boolean bypassQualityGate;

        public boolean isForceRuleMode() {
            return forceRuleMode;
        }

        public void setForceRuleMode(boolean forceRuleMode) {
            this.forceRuleMode = forceRuleMode;
        }

        public boolean isDisableCompression() {
            return disableCompression;
        }

        public void setDisableCompression(boolean disableCompression) {
            this.disableCompression = disableCompression;
        }

        public boolean isBypassQualityGate() {
            return bypassQualityGate;
        }

        public void setBypassQualityGate(boolean bypassQualityGate) {
            this.bypassQualityGate = bypassQualityGate;
        }
    }
}
