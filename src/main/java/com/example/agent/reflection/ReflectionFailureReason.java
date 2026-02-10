package com.example.agent.reflection;

/**
 * 反思失败原因码。
 *
 * <p>用途：统一描述反思失败场景，便于日志检索、指标聚合与后续扩展。</p>
 */
public enum ReflectionFailureReason {

    /**
     * 无失败。
     */
    NONE,

    /**
     * 模型返回为空。
     */
    LLM_EMPTY_RESPONSE,

    /**
     * 模型输出解析失败。
     */
    LLM_PARSE_ERROR,

    /**
     * 模型输出修复失败。
     */
    LLM_REPAIR_FAILED,

    /**
     * 模型调用异常。
     */
    LLM_INVOCATION_ERROR,

    /**
     * 禁止回退到规则反思。
     */
    FALLBACK_DISABLED
}

