package com.example.agent.capabilities.llm.client;

/**
 * LLM 调用结果状态。
 *
 * <p>用途：统一标识模型调用成功、失败与可重试失败状态。
 * <p>输入：由模型调用服务在调用完成后写入。
 * <p>输出：供业务层、事件层、指标层统一消费。
 */
public enum LlmResultStatus {

    /**
     * 调用成功。
     */
    SUCCESS,

    /**
     * 调用失败且不可重试。
     */
    FAILED,

    /**
     * 调用失败但可重试。
     */
    RETRIABLE_FAILED;

    /**
     * 转换为事件与日志统一小写状态值。
     *
     * @return 小写状态值
     */
    public String toPayloadValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

