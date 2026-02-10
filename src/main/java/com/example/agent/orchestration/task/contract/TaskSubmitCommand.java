package com.example.agent.orchestration.task.contract;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import java.util.Map;

/**
 * 任务提交命令。
 * <p>用途：承载编排层执行任务所需的输入数据，避免直接依赖传输层 DTO。
 * <p>输入：查询内容、会话标识、技能名、上下文、幂等键、工具选择、执行模式与等待超时。
 * <p>输出：作为任务编排入口参数，不直接产出业务结果。
 * <p>边界：当执行模式为空时由调用方或编排服务兜底为异步模式。
 */
public class TaskSubmitCommand {

    /**
     * 用户查询内容。
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
     * 业务上下文。
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
    private TaskExecutionMode executionMode;

    /**
     * 同步等待超时毫秒。
     */
    private Long waitTimeoutMs;

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

    public TaskExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(TaskExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    public Long getWaitTimeoutMs() {
        return waitTimeoutMs;
    }

    public void setWaitTimeoutMs(Long waitTimeoutMs) {
        this.waitTimeoutMs = waitTimeoutMs;
    }
}

