package com.example.agent.runtime.model;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import java.util.Map;

/**
 * 运行时任务请求。
 * <p>用途：承载编排层向运行时传递的任务参数，避免运行时直接依赖 HTTP 传输 DTO。
 */
public class RuntimeTaskRequest {

    /**
     * 用户输入问题。
     */
    private String query;

    /**
     * 会话标识。
     */
    private String sessionId;

    /**
     * 技能名称。
     */
    private String skillName;

    /**
     * 运行上下文。
     */
    private Map<String, Object> context;

    /**
     * 幂等键。
     */
    private String idempotencyKey;

    /**
     * 工具选择策略。
     */
    private ModelToolChoice toolChoice;

    /**
     * 执行模式。
     */
    private ExecutionMode executionMode;

    /**
     * 同步等待超时时间。
     */
    private Long waitTimeoutMs;

    public enum ExecutionMode {
        ASYNC,
        SYNC
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
}
