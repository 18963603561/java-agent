package com.example.agent.governance.approval;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 高风险工具审批配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.tool.approval")
public class ApprovalProperties {

    /**
     * 是否启用工具审批。
     */
    private boolean enabled = false;

    /**
     * 高风险工具名称列表。
     */
    private List<String> highRiskTools = new ArrayList<>();

    /**
     * 审批等待超时秒数。
     */
    private int timeoutSeconds = 300;

    /**
     * 待审批请求的内存保留时长（秒）。
     */
    private int pendingTtlSeconds = 1800;

    /**
     * 待审批请求的最大缓存数量。
     */
    private int pendingMaxSize = 2000;

    /**
     * 待审批缓存清理周期（秒）。
     */
    private int cleanupIntervalSeconds = 30;

    /**
     * 获取是否启用工具审批。
     *
     * @return 是否启用工具审批
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getHighRiskTools() {
        return highRiskTools;
    }

    public void setHighRiskTools(List<String> highRiskTools) {
        this.highRiskTools = highRiskTools;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getPendingTtlSeconds() {
        return pendingTtlSeconds;
    }

    public void setPendingTtlSeconds(int pendingTtlSeconds) {
        this.pendingTtlSeconds = pendingTtlSeconds;
    }

    public int getPendingMaxSize() {
        return pendingMaxSize;
    }

    public void setPendingMaxSize(int pendingMaxSize) {
        this.pendingMaxSize = pendingMaxSize;
    }

    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public void setCleanupIntervalSeconds(int cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }
}
