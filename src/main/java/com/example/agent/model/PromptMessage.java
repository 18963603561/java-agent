package com.example.agent.model;

/**
 * 提示词消息结构。
 */
public class PromptMessage {

    /**
     * 消息角色。
     */
    private PromptRole role;

    /**
     * 消息内容。
     */
    private String content;

    public PromptMessage() {
    }

    public PromptMessage(PromptRole role, String content) {
        this.role = role;
        this.content = content;
    }

    public PromptRole getRole() {
        return role;
    }

    public void setRole(PromptRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}