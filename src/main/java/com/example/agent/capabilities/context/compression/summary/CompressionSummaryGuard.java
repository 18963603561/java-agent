package com.example.agent.capabilities.context.compression.summary;

import com.example.agent.security.redaction.RedactionResult;
import com.example.agent.security.redaction.RedactionService;
import com.example.agent.security.redaction.RedactionStage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 压缩摘要治理器。
 */
@Component
public class CompressionSummaryGuard {

    private static final int MAX_SUMMARY_CHARS = 2000;

    private final RedactionService redactionService;

    public CompressionSummaryGuard(RedactionService redactionService) {
        this.redactionService = redactionService;
    }

    /**
     * 执行摘要治理。
     *
     * @param summary 原始摘要
     * @return 治理结果
     */
    public CompressionSummaryGuardResult guard(String summary) {
        CompressionSummaryGuardResult result = new CompressionSummaryGuardResult();
        if (!StringUtils.hasText(summary)) {
            result.setPassed(false);
            result.setFailureReason("LLM_EMPTY");
            return result;
        }
        String trimmed = summary.trim();
        // 长度治理：摘要超限时进行截断，避免压缩结果放大输入成本。
        if (trimmed.length() > MAX_SUMMARY_CHARS) {
            trimmed = trimmed.substring(0, MAX_SUMMARY_CHARS);
        }
        // 脱敏治理：对摘要应用召回阶段脱敏规则，避免敏感信息泄漏。
        if (redactionService != null) {
            RedactionResult redaction = redactionService.apply(trimmed, RedactionStage.RECALL, "compressionSummary");
            // 拒绝策略：命中不可接受规则时标记失败并返回原因。
            if (redaction.isRejected()) {
                result.setPassed(false);
                result.setFailureReason("LLM_POLICY_REJECTED");
                return result;
            }
            trimmed = redaction.getRedactedText();
            result.setRedactedCount(redaction.getRedactedCount());
        }
        result.setPassed(StringUtils.hasText(trimmed));
        result.setSummary(trimmed);
        if (!result.isPassed()) {
            result.setFailureReason("LLM_EMPTY");
        }
        return result;
    }
}

