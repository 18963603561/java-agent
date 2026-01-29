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

    /**
     * 获取消息列表。
     *
     * @return 消息列表
     */
    public List<PromptMessage> getMessages() {
        return messages;
    }

    /**
     * 设置消息列表。
     *
     * @param messages 消息列表
     */
    public void setMessages(List<PromptMessage> messages) {
        this.messages = messages;
    }

    /**
     * 获取模板标识。
     *
     * @return 模板标识
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * 设置模板标识。
     *
     * @param templateId 模板标识
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
     * 获取预估令牌数。
     *
     * @return 预估令牌数
     */
    public Integer getEstimatedTokens() {
        return estimatedTokens;
    }

    /**
     * 设置预估令牌数。
     *
     * @param estimatedTokens 预估令牌数
     */
    public void setEstimatedTokens(Integer estimatedTokens) {
        this.estimatedTokens = estimatedTokens;
    }

    /**
     * 获取被裁剪的段落标识。
     *
     * @return 被裁剪的段落标识
     */
    public List<String> getTruncatedSections() {
        return truncatedSections;
    }

    /**
     * 设置被裁剪的段落标识。
     *
     * @param truncatedSections 被裁剪的段落标识
     */
    public void setTruncatedSections(List<String> truncatedSections) {
        this.truncatedSections = truncatedSections;
    }
}
