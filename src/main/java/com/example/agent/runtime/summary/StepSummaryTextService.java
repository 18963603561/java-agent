package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputFieldExtractor;
import com.example.agent.runtime.summary.SummaryComputationModels.OutputSnapshot;
import com.example.agent.runtime.summary.SummaryComputationModels.SummaryLimits;
import com.example.agent.runtime.summary.SummaryComputationModels.TruncationState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 步骤摘要文本服务。
 *
 * <p>用途：生成步骤摘要文本、工具名解析与错误文本解析。
 * <p>输入：步骤类型、状态、工具名、输出快照与错误对象。
 * <p>输出：摘要文本与错误文本。
 * <p>边界：无有效内容时返回空。
 */
@Service
public class StepSummaryTextService {

    /**
     * 解析工具名称。
     *
     * @param toolName 显式工具名
     * @param output 输出对象
     * @return 工具名称
     */
    public String resolveToolName(String toolName, Object output) {
        if (StringUtils.hasText(toolName)) {
            return toolName;
        }
        if (output instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return RuntimeOutputFieldExtractor.resolveToolName(typed);
        }
        return null;
    }

    /**
     * 解析错误文本。
     *
     * @param error 错误对象
     * @param limits 限制
     * @param truncation 截断状态
     * @return 错误文本
     */
    public String resolveErrorText(Object error,
                                   SummaryLimits limits,
                                   TruncationState truncation) {
        if (error == null) {
            return null;
        }
        String text;
        if (error instanceof Throwable throwable) {
            String message = throwable.getMessage();
            text = message == null
                    ? throwable.getClass().getSimpleName()
                    : throwable.getClass().getSimpleName() + ": " + message;
        } else {
            text = String.valueOf(error);
        }
        return SummaryDigestService.trimText(text, limits, truncation);
    }

    /**
     * 构建步骤摘要文本。
     *
     * @param stepType 步骤类型
     * @param status 状态
     * @param toolName 工具名称
     * @param snapshot 输出快照
     * @param limits 限制
     * @param truncation 截断状态
     * @return 摘要文本
     */
    public String buildStepSummaryText(String stepType,
                                       String status,
                                       String toolName,
                                       OutputSnapshot snapshot,
                                       SummaryLimits limits,
                                       TruncationState truncation) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(stepType)) {
            parts.add("type=" + stepType);
        }
        if (StringUtils.hasText(status)) {
            parts.add("status=" + status);
        }
        if (StringUtils.hasText(toolName)) {
            parts.add("tool=" + toolName);
        }
        if (snapshot != null && snapshot.getKeyCount() > 0) {
            parts.add(String.format(Locale.ROOT, "keyCount=%d", snapshot.getKeyCount()));
        }
        if (snapshot != null && snapshot.getCharCount() > 0) {
            parts.add(String.format(Locale.ROOT, "charCount=%d", snapshot.getCharCount()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return SummaryDigestService.trimText(String.join(", ", parts), limits, truncation);
    }
}
