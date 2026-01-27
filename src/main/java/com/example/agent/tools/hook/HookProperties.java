package com.example.agent.tools.hook;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hook 配置项。
 */
@Component
@ConfigurationProperties(prefix = "agent.hook")
public class HookProperties {

    private boolean enabled = true;
    private List<String> blockedTools = new ArrayList<>();
    /**
     * Hook 配置列表，包含顺序与超时策略。
     */
    private List<HookConfig> hooks = new ArrayList<>();
    /**
     * Hook 超时策略，默认超时放行。
     */
    private HookTimeoutPolicy timeoutPolicy = HookTimeoutPolicy.FAIL_OPEN;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBlockedTools() {
        return blockedTools;
    }

    public void setBlockedTools(List<String> blockedTools) {
        this.blockedTools = blockedTools;
    }

    public List<HookConfig> getHooks() {
        return hooks;
    }

    public void setHooks(List<HookConfig> hooks) {
        this.hooks = hooks;
    }

    public HookTimeoutPolicy getTimeoutPolicy() {
        return timeoutPolicy;
    }

    public void setTimeoutPolicy(HookTimeoutPolicy timeoutPolicy) {
        this.timeoutPolicy = timeoutPolicy;
    }

    /**
     * 单个 Hook 配置。
     */
    public static class HookConfig {
        /**
         * Hook 标识，与 HookHandler.getHookId 对应。
         */
        private String hookId;
        /**
         * Hook 执行顺序，越小越先执行。
         */
        private int order;
        /**
         * Hook 超时时间（毫秒），小于等于 0 表示不启用超时。
         */
        private long timeoutMs;

        public String getHookId() {
            return hookId;
        }

        public void setHookId(String hookId) {
            this.hookId = hookId;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
