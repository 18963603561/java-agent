package com.example.agent.model;

import java.util.List;

/**
 * 提示词组装结果。
 */
public class PromptBundle {

    /**
     * 消息列表。
     */
    private List<PromptMessage> messages;

    /**
     * 模板标识。
     */
    private String templateId;

    /**
     * 预估令牌数。
     */
    private Integer estimatedTokens;

    /**
     * 被裁剪的段落标识。
     */
    private List<String> truncatedSections;

    public List<PromptMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<PromptMessage> messages) {
        this.messages = messages;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public Integer getEstimatedTokens() {
        return estimatedTokens;
    }

    public void setEstimatedTokens(Integer estimatedTokens) {
        this.estimatedTokens = estimatedTokens;
    }

    public List<String> getTruncatedSections() {
        return truncatedSections;
    }

    public void setTruncatedSections(List<String> truncatedSections) {
        this.truncatedSections = truncatedSections;
    }
}