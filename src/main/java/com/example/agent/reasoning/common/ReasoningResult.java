package com.example.agent.reasoning.common;

import com.example.agent.reasoning.common.result.ReasoningPayload;

/**
 * 推理统一结果对象。
 *
 * <p>用途：统一承载不同推理策略输出，供执行器与上层编排复用。
 */
public class ReasoningResult {

    private final String strategyType;
    private final String summary;
    private final double confidence;
    private final String stopReason;
    private final String status;
    private final String rawRef;
    private final ReasoningPayload payload;

    /**
     * 构造推理结果。
     */
    public ReasoningResult(String strategyType,
                           String summary,
                           double confidence,
                           String stopReason,
                           String status,
                           String rawRef,
                           ReasoningPayload payload) {
        this.strategyType = strategyType;
        this.summary = summary;
        this.confidence = confidence;
        this.stopReason = stopReason;
        this.status = status;
        this.rawRef = rawRef;
        this.payload = payload;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public String getSummary() {
        return summary;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getStopReason() {
        return stopReason;
    }

    public String getStatus() {
        return status;
    }

    public String getRawRef() {
        return rawRef;
    }

    public ReasoningPayload getPayload() {
        return payload;
    }

}
