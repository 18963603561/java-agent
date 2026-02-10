package com.example.agent.reasoning.common.result;

import java.util.Map;

/**
 * 推理结果载荷接口。
 *
 * <p>用途：统一推理结果强类型载荷，并提供兼容映射能力。
 */
public interface ReasoningPayload {

    /**
     * 转换为兼容映射。
     *
     * @return 映射结果
     */
    Map<String, Object> toMap();
}

