package com.example.agent.capabilities.memory.model;

import java.time.Instant;

/**
 * 记忆记录，包含会话与语义信息。
 */
public class MemoryRecord {

    private String memoryId;
    private String sessionId;
    private String taskId;
    private String content;
    private String summary;
    private String embeddingRef;
    private String tenantId;
    private String layer;
    private Instant createdAt;
    /**
     * 过期时间。
     */
    private Instant expiresAt;
    /**
     * 会话摘要结构化信息，主要用于压缩输出，持久化存储可能不落库。
     */
    private ConversationSummary conversationSummary;
    /**
     * 工作记忆结构化信息，主要用于压缩输出，持久化存储可能不落库。
     */
    private WorkingMemorySummary workingMemorySummary;

    public MemoryRecord() {
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getEmbeddingRef() {
        return embeddingRef;
    }

    public void setEmbeddingRef(String embeddingRef) {
        this.embeddingRef = embeddingRef;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public ConversationSummary getConversationSummary() {
        return conversationSummary;
    }

    public void setConversationSummary(ConversationSummary conversationSummary) {
        this.conversationSummary = conversationSummary;
    }

    public WorkingMemorySummary getWorkingMemorySummary() {
        return workingMemorySummary;
    }

    public void setWorkingMemorySummary(WorkingMemorySummary workingMemorySummary) {
        this.workingMemorySummary = workingMemorySummary;
    }
}
