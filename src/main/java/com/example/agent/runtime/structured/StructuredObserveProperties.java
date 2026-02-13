package com.example.agent.runtime.structured;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 结构化结果观测配置。
 *
 * <p>用途：控制结构化指标与日志的开关与采样比例。</p>
 */
@Component
@ConfigurationProperties(prefix = "agent.structured.observe")
public class StructuredObserveProperties {

    /**
     * 是否启用观测能力。
     */
    private boolean enable = true;

    /**
     * 指标采样比例。
     */
    private double sampleRate = 1.0D;

    /**
     * 日志采样比例。
     */
    private double logSampleRate = 1.0D;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public double getLogSampleRate() {
        return logSampleRate;
    }

    public void setLogSampleRate(double logSampleRate) {
        this.logSampleRate = logSampleRate;
    }
}