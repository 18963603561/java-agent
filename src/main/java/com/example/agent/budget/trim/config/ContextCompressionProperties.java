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
}
