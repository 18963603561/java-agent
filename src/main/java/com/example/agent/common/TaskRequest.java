package com.example.agent.common;

import com.example.agent.model.ModelToolChoice;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 任务提交请求结构。
 */
public class TaskRequest {

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
     * 幂等键。
     */
    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;

    /**
     * 工具选择策略，可选。
     */
    private ModelToolChoice toolChoice;

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
}
