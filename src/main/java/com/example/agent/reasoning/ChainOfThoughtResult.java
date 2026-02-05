package com.example.agent.reasoning;

/**
 * 链式推理结果，面向运行时输出结构化摘要。
 */
public class ChainOfThoughtResult {

    /**
     * 最终答案摘要。
     */
    private String finalAnswer;

    /**
     * 实际执行的推理步数。
     */
    private int stepsCount;

    /**
     * 置信度评分，范围建议为 0 到 1。
     */
    private double confidence;

    /**
     * 停止原因，例如 completed 或 max_steps。
     */
    private String stopReason;

    /**
     * 是否完成推理流程。
     */
    private boolean completed;

    /**
     * 推理原始输出引用。
     */
    private String rawRef;

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public int getStepsCount() {
        return stepsCount;
    }

    public void setStepsCount(int stepsCount) {
        this.stepsCount = stepsCount;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }
}
