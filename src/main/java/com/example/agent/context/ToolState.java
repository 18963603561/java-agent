package com.example.agent.context;

import com.example.agent.tools.ToolSummary;
import java.util.List;

/**
 * 工具状态信息。
 */
public class ToolState {

    /**
     * 可用工具摘要列表。
     */
    private List<ToolSummary> availableTools;

    /**
     * 已选工具名称列表。
     */
    private List<String> selectedTools;

    /**
     * 最近一次工具调用记录。
     */
    private ToolCallState lastCall;

    /**
     * 最近一次错误信息。
     */
    private String lastError;

    /**
     * 重试次数。
     */
    private Integer retryCount;

    public List<ToolSummary> getAvailableTools() {
        return availableTools;
    }

    public void setAvailableTools(List<ToolSummary> availableTools) {
        this.availableTools = availableTools;
    }

    public List<String> getSelectedTools() {
        return selectedTools;
    }

    public void setSelectedTools(List<String> selectedTools) {
        this.selectedTools = selectedTools;
    }

    public ToolCallState getLastCall() {
        return lastCall;
    }

    public void setLastCall(ToolCallState lastCall) {
        this.lastCall = lastCall;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
}