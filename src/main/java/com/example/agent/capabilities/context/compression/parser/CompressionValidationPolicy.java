package com.example.agent.capabilities.context.compression.parser;

/**
 * 压缩响应校验策略。
 */
public interface CompressionValidationPolicy {

    /**
     * 校验解析结果。
     *
     * @param result 解析结果
     */
    void validate(CompressionParseResult result);
}

