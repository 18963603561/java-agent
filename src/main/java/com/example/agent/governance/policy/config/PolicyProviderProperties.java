package com.example.agent.governance.policy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 策略提供者配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.policy")
public class PolicyProviderProperties {

    /**
     * 提供者类型。
     */
    private String provider = "local";

    /**
     * OPA 相关配置。
     */
    private final Opa opa = new Opa();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Opa getOpa() {
        return opa;
    }

    /**
     * OPA 配置项。
     */
    public static class Opa {

        /**
         * OPA 服务地址。
         */
        private String url = "http://localhost:8181";

        /**
         * OPA 数据路径。
         */
        private String path = "/v1/data/agent/policy/evaluate";

        /**
         * 请求超时毫秒。
         */
        private int timeoutMs = 3000;

        /**
         * 失败是否默认拒绝。
         */
        private boolean failClosed = true;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public boolean isFailClosed() {
            return failClosed;
        }

        public void setFailClosed(boolean failClosed) {
            this.failClosed = failClosed;
        }
    }
}

