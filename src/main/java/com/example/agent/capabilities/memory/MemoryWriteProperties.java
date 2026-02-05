package com.example.agent.capabilities.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆写入配置，用于控制自动记忆落盘策略。
 */
@Component
@ConfigurationProperties(prefix = "agent.memory.write")
public class MemoryWriteProperties {

    /**
     * 是否开启自动记忆写入。
     */
    private boolean enabled = true;

    /**
     * 是否保存用户输入。
     */
    private boolean saveUserQuery = true;

    /**
     * 是否保存最终输出。
     */
    private boolean saveFinalOutput = true;

    /**
     * 是否保存观察记录。
     */
    private boolean saveObservation = false;

    /**
     * 记忆内容最大字符数。
     */
    private int maxRecordChars = 2000;

    /**
     * 记忆摘要最大字符数。
     */
    private int maxSummaryChars = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSaveUserQuery() {
        return saveUserQuery;
    }

    public void setSaveUserQuery(boolean saveUserQuery) {
        this.saveUserQuery = saveUserQuery;
    }

    public boolean isSaveFinalOutput() {
        return saveFinalOutput;
    }

    public void setSaveFinalOutput(boolean saveFinalOutput) {
        this.saveFinalOutput = saveFinalOutput;
    }

    public boolean isSaveObservation() {
        return saveObservation;
    }

    public void setSaveObservation(boolean saveObservation) {
        this.saveObservation = saveObservation;
    }

    public int getMaxRecordChars() {
        return maxRecordChars;
    }

    public void setMaxRecordChars(int maxRecordChars) {
        this.maxRecordChars = maxRecordChars;
    }

    public int getMaxSummaryChars() {
        return maxSummaryChars;
    }

    public void setMaxSummaryChars(int maxSummaryChars) {
        this.maxSummaryChars = maxSummaryChars;
    }
}
