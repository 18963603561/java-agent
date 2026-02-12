package com.example.agent.capabilities.context.compression.parser;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认压缩响应校验策略。
 */
@Component
public class DefaultCompressionValidationPolicy implements CompressionValidationPolicy {

    private static final int MAX_SUMMARY_CHARS = 2000;

    @Override
    public void validate(CompressionParseResult result) {
        if (result == null) {
            return;
        }
        // 摘要非空校验：缺失摘要时标记失败并记录违规原因。
        if (!StringUtils.hasText(result.getSummary())) {
            result.setSuccess(false);
            result.setFailureReason("LLM_EMPTY");
            result.addViolation("summary_missing");
            return;
        }
        // 摘要长度校验：超长摘要视为违规，避免污染后续上下文构建。
        if (result.getSummary().length() > MAX_SUMMARY_CHARS) {
            result.setSuccess(false);
            result.setFailureReason("LLM_POLICY_REJECTED");
            result.addViolation("summary_too_long");
            return;
        }
        if (!StringUtils.hasText(result.getSummaryVersion())) {
            result.setSummaryVersion("v1");
        }
    }
}

