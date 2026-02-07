package com.example.agent.capabilities.llm.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型远程调用配置。
 *
 * <p>用途：统一承载远程模型调用的通用 HTTP 参数，避免适配器重复定义配置字段。</p>
 */
@Component
@ConfigurationProperties(prefix = "agent.model.http")
public class ModelProviderHttpProperties {

    /**
     * 远程调用超时时间（秒）。
     */
    private long timeoutSeconds = 120;

    /**
     * 默认采样温度。
     */
    private double temperature = 0.2;

    /**
     * 全局接口密钥，模型未单独配置时使用。
     */
    private String apiKey;

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}

