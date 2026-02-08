package com.example.agent.planning;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 规划配置，用于控制是否启用模型规划与解析策略。
 */
@Component
@ConfigurationProperties(prefix = "agent.planner")
public class PlannerProperties {

    /**
     * 是否启用模型规划。
     */
    private boolean llmEnabled = true;

    /**
     * 规划失败时是否允许回退到规则规划。
     */
    private boolean fallbackEnabled = true;

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}

