package com.example.agent.capabilities.llm.client.events;

import java.util.Map;

/**
 * LLM 调用元数据接口。
 *
 * <p>用途：为模型调用提供强类型元数据边界，避免直接拼装 Map 协议。
 * <p>输入：业务域对象实现该接口并输出元数据映射。
 * <p>输出：供 {@code ModelInvocationService} 统一消费。
 * <p>边界：实现类应保证返回非空 Map。
 */
public interface LlmInvocationMetadata {

    /**
     * 转换为模型调用元数据映射。
     *
     * @return 元数据映射
     */
    Map<String, Object> toMetadataMap();
}
