package com.example.agent.api.http.dto;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 任务提交请求结构。
 */
public class TaskRequest {

    /**
     * 执行模式枚举，当前仅保留字段。
     */
    public enum ExecutionMode {
        ASYNC,
        SYNC
    }

    /**
     * 任务查询内容。
     */
    @NotBlank(message = "查询内容不能为空")
    private String query;

    /**
     * 会话标识，可选。
     */
    private String sessionId;

    /**
     * 技能名称，用于限定可用工具与工具选择策略。
     */
    private String skillName;

    /**
     * 任务上下文，可选。
     */
    private Map<String, Object> context;

    /**
     * 幂等键（可选）。
     */
    private String idempotencyKey;

    /**
     * 工具选择策略，可选。
     */
    private ModelToolChoice toolChoice;

    /**
     * 执行模式，可选，默认按异步处理。
     */
    private ExecutionMode executionMode;

    /**
     * 同步等待超时时间（毫秒），仅在 SYNC 模式生效。
     */
    private Long waitTimeoutMs;

    /**
     * 响应模式，可选值：compact/full。
     * <p>用途：仅影响 HTTP 返回结构，不改变任务执行主流程。
     */
    private String responseMode;

    public TaskRequest() {
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public ModelToolChoice getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(ModelToolChoice toolChoice) {
        this.toolChoice = toolChoice;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    public Long getWaitTimeoutMs() {
        return waitTimeoutMs;
    }

    public void setWaitTimeoutMs(Long waitTimeoutMs) {
        this.waitTimeoutMs = waitTimeoutMs;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }
}
