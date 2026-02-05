package com.example.agent.runtime.engine;

import java.time.Instant;

/**
 * ReAct 观察记录。
 */
public class ReactObservation {

    /**
     * 观察内容。
     */
    private String content;

    /**
     * 关联工具名称。
     */
    private String tool;

    /**
     * 观察时间。
     */
    private Instant timestamp;

    public ReactObservation() {
    }

    public ReactObservation(String content, String tool, Instant timestamp) {
        this.content = content;
        this.tool = tool;
        this.timestamp = timestamp;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
