package com.example.agent.api.http.config;

import com.example.agent.api.http.response.ResponseMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API 响应层配置。
 * <p>用途：集中管理对外响应模式默认值，避免各控制器分散硬编码。
 */
@Component
@ConfigurationProperties(prefix = "agent.api.response")
public class ApiResponseProperties {

    /**
     * 默认响应模式。
     */
    private ResponseMode defaultMode = ResponseMode.COMPACT;

    public ResponseMode getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(ResponseMode defaultMode) {
        this.defaultMode = defaultMode == null ? ResponseMode.COMPACT : defaultMode;
    }
}
