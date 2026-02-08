package com.example.agent.capabilities.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆策略配置，用于控制自动压缩与分层检索策略的阈值。
 */
@Component
@ConfigurationProperties(prefix = "agent.memory.policy")
public class MemoryPolicyProperties {

    private boolean enabled = true;
    private int sizeThreshold = 50;
    private int tokenThreshold = 2000;
    private long maxAgeSeconds = 3600;
    private long minCompressIntervalSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSizeThreshold() {
        return sizeThreshold;
    }

    public void setSizeThreshold(int sizeThreshold) {
        this.sizeThreshold = sizeThreshold;
    }

    public int getTokenThreshold() {
        return tokenThreshold;
    }

    public void setTokenThreshold(int tokenThreshold) {
        this.tokenThreshold = tokenThreshold;
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public long getMinCompressIntervalSeconds() {
        return minCompressIntervalSeconds;
    }

    public void setMinCompressIntervalSeconds(long minCompressIntervalSeconds) {
        this.minCompressIntervalSeconds = minCompressIntervalSeconds;
    }
}
