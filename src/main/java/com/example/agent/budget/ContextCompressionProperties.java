package com.example.agent.budget;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTriggerOverTotalBudget() {
        return triggerOverTotalBudget;
    }

    public void setTriggerOverTotalBudget(boolean triggerOverTotalBudget) {
        this.triggerOverTotalBudget = triggerOverTotalBudget;
    }

    public boolean isTriggerOverSectionBudget() {
        return triggerOverSectionBudget;
    }

    public void setTriggerOverSectionBudget(boolean triggerOverSectionBudget) {
        this.triggerOverSectionBudget = triggerOverSectionBudget;
    }

    public int getMinIntervalSeconds() {
        return minIntervalSeconds;
    }

    public void setMinIntervalSeconds(int minIntervalSeconds) {
        this.minIntervalSeconds = minIntervalSeconds;
    }
}
