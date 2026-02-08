package com.example.agent.capabilities.memory.support;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 记忆文本工具类，统一提供文本裁剪与匹配等通用能力。
 */
public final class MemoryTextUtils {

    private MemoryTextUtils() {
    }

    /**
     * 返回首个非空白文本。
     *
     * @param first 首选文本
     * @param second 兜底文本
     * @return 首个非空白文本，均为空时返回 null
     */
    public static String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return null;
    }

    /**
     * 按最大字符数裁剪文本。
     *
     * @param text 原始文本
     * @param maxChars 最大字符数
     * @return 裁剪后的文本
     */
    public static String trimText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }

    /**
     * 以忽略大小写方式判断文本是否包含目标片段。
     *
     * @param source 原始文本
     * @param target 目标片段
     * @return 是否包含目标片段
     */
    public static boolean safeLowercaseContains(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
    }
}
