package com.example.agent.reasoning;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 链式推理配置，用于控制推理步数与事件输出等策略。
 */
@Component
@ConfigurationProperties(prefix = "agent.cot")
public class CotProperties {

    /**
     * 最大推理步数，上限用于防止无限循环。
     */
    private int maxSteps = 4;

    /**
     * 历史步骤摘要最多保留条数，用于控制提示词体量。
     */
    private int maxStepSummaries = 10;

    /**
     * 温度覆盖值，空值表示沿用默认模型温度。
     */
    private Double temperatureOverride;

    /**
     * 模型提示，支持映射到既有场景名称。
     */
    private String modelHint = "reflect";

    /**
     * 是否输出逐步事件，用于调试与观测。
     */
    private boolean emitStepEvents = false;

    /**
     * 最终答案最大长度，超过则截断。
     */
    private int maxFinalAnswerChars = 400;

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public int getMaxStepSummaries() {
        return maxStepSummaries;
    }

    public void setMaxStepSummaries(int maxStepSummaries) {
        this.maxStepSummaries = maxStepSummaries;
    }

    public Double getTemperatureOverride() {
        return temperatureOverride;
    }

    public void setTemperatureOverride(Double temperatureOverride) {
        this.temperatureOverride = temperatureOverride;
    }

    public String getModelHint() {
        return modelHint;
    }

    public void setModelHint(String modelHint) {
        this.modelHint = modelHint;
    }

    public boolean isEmitStepEvents() {
        return emitStepEvents;
    }

    public void setEmitStepEvents(boolean emitStepEvents) {
        this.emitStepEvents = emitStepEvents;
    }

    public int getMaxFinalAnswerChars() {
        return maxFinalAnswerChars;
    }

    public void setMaxFinalAnswerChars(int maxFinalAnswerChars) {
        this.maxFinalAnswerChars = maxFinalAnswerChars;
    }
}
