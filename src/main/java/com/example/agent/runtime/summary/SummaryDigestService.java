package com.example.agent.runtime.summary;

import com.example.agent.runtime.summary.SummaryComputationModels.SummaryLimits;
import com.example.agent.runtime.summary.SummaryComputationModels.TruncationState;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 摘要辅助服务。
 *
 * <p>用途：提供文本截断、摘要字段写入等通用能力。
 * <p>输入：目标映射、字段值、限制配置与截断状态。
 * <p>输出：写入后的目标映射。
 * <p>边界：空值不写入，截断会同步标记状态。
 */
@Service
public class SummaryDigestService {

    /**
     * 写入文本摘要字段。
     *
     * @param target 目标映射
     * @param key 字段名
     * @param value 字段值
     * @param limits 限制
     * @param truncation 截断状态
     */
    public void putTextSummary(Map<String, Object> target,
                               String key,
                               Object value,
                               SummaryLimits limits,
                               TruncationState truncation) {
        if (value == null) {
            return;
        }
        String text = value instanceof String textValue ? textValue : String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        target.put(key, trimText(text, limits, truncation));
    }

    /**
     * 写入结构化摘要字段。
     *
     * @param target 目标映射
     * @param key 字段名
     * @param value 字段值
     */
    public void putStructuredSummary(Map<String, Object> target,
                                     String key,
                                     Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        target.put(key, value);
    }

    /**
     * 截断文本。
     *
     * @param text 文本
     * @param limits 限制
     * @param truncation 截断状态
     * @return 截断后文本
     */
    public static String trimText(String text, SummaryLimits limits, TruncationState truncation) {
        if (text == null) {
            return null;
        }
        int maxChars = limits != null ? limits.getMaxFieldChars() : 0;
        if (maxChars > 0 && text.length() > maxChars) {
            truncation.markTruncated();
            return text.substring(0, maxChars);
        }
        return text;
    }
}
