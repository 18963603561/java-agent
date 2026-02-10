package com.example.agent.reasoning.common;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JSON 载荷清洗器。
 *
 * <p>用途：统一处理代码块包裹、前后噪声文本与 JSON 片段提取。
 */
@Component
public class JsonPayloadNormalizer {

    /**
     * 清洗并提取 JSON 文本。
     *
     * @param content 原始模型输出
     * @return 清洗后的文本
     */
    public String normalize(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd >= 0) {
                trimmed = trimmed.substring(firstLineEnd + 1);
            } else {
                trimmed = trimmed.substring(3);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
        }
        trimmed = trimmed.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1).trim();
        }
        return trimmed;
    }
}

