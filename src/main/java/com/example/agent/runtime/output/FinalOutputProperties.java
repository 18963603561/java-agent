package com.example.agent.runtime.output;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 最终输出提示词配置，用于控制摘要字符数上限。
 */
@Component
@ConfigurationProperties(prefix = "agent.final-output")
public class FinalOutputProperties {

    /**
     * 最终输出提示词中摘要有效字符数上限。
     */
    private int promptSummaryMaxChars = 800;

    public int getPromptSummaryMaxChars() {
        return promptSummaryMaxChars;
    }

    public void setPromptSummaryMaxChars(int promptSummaryMaxChars) {
        this.promptSummaryMaxChars = promptSummaryMaxChars;
    }
}
