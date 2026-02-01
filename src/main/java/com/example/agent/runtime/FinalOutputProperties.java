package com.example.agent.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 鏈€缁堣緭鍑烘彁绀鸿瘝閰嶇疆锛岀敤浜庢帶鍒舵姽瑕佸瓧绗︽暟涓婇檺銆?
 */
@Component
@ConfigurationProperties(prefix = "agent.final-output")
public class FinalOutputProperties {

    /**
     * 鏈€缁堣緭鍑烘彁绀鸿瘝涓憳瑕佹湁鏁堝瓧绗︽暟涓婇檺銆?
     */
    private int promptSummaryMaxChars = 800;

    public int getPromptSummaryMaxChars() {
        return promptSummaryMaxChars;
    }

    public void setPromptSummaryMaxChars(int promptSummaryMaxChars) {
        this.promptSummaryMaxChars = promptSummaryMaxChars;
    }
}
