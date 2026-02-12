package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认压缩降级策略。
 */
@Component
public class DefaultCompressionFallbackPolicy implements CompressionFallbackPolicy {

    private static final String MODE_LLM = "llm";

    private final ContextCompressionProperties properties;

    public DefaultCompressionFallbackPolicy(ContextCompressionProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean shouldFallback(String mode, CompressionExecutionResult executionResult) {
        // 模式判断：仅 LLM 模式失败时允许进入降级决策。
        if (!MODE_LLM.equalsIgnoreCase(mode)) {
            return false;
        }
        // 成功短路：执行成功时不触发降级。
        if (executionResult != null && executionResult.isSuccess()) {
            return false;
        }
        String fallback = properties != null && properties.getLlm() != null
                ? properties.getLlm().getFallback()
                : null;
        // 策略判断：配置为 rule 时允许降级，其它值视为不降级。
        return StringUtils.hasText(fallback) && "rule".equalsIgnoreCase(fallback.trim());
    }
}



