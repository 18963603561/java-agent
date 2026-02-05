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
}
