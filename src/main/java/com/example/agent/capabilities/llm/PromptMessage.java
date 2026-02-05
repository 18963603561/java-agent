package com.example.agent.capabilities.llm;

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

    /**
     * 空构造方法，便于序列化。
     */
    public PromptMessage() {
    }

    /**
     * 构造提示消息。
     *
     * @param role 消息角色
     * @param content 消息内容
     */
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
