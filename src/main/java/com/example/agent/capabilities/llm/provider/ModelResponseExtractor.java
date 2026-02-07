package com.example.agent.capabilities.llm.provider;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 模型响应提取器。
 *
 * <p>用途：统一解析不同模型接口返回结构中的文本与令牌字段。</p>
 */
@Component
public class ModelResponseExtractor {

    /**
     * 从兼容接口响应提取文本内容。
     *
     * @param response 响应映射
     * @return 文本内容，未命中时返回空字符串
     */
    public String extractOpenAiContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> msg && msg.get("content") instanceof String content) {
                    return content;
                }
                Object content = choice.get("text");
                if (content instanceof String text) {
                    return text;
                }
            }
        }
        return "";
    }

    /**
     * 从兼容接口响应提取令牌统计。
     *
     * @param response 响应映射
     * @param key usage 字段键
     * @return 令牌数量，未命中时返回 0
     */
    public int extractOpenAiTokens(Map<String, Object> response, String key) {
        if (response == null || key == null || key.isBlank()) {
            return 0;
        }
        Object usage = response.get("usage");
        if (usage instanceof Map<?, ?> usageMap && usageMap.get(key) instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 从原生接口响应提取文本内容。
     *
     * @param response 响应映射
     * @return 文本内容，未命中时返回空字符串
     */
    public String extractOllamaContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object message = response.get("message");
        if (message instanceof Map<?, ?> msg && msg.get("content") instanceof String content) {
            return content;
        }
        Object responseText = response.get("response");
        if (responseText instanceof String text) {
            return text;
        }
        return "";
    }

    /**
     * 从原生接口响应提取令牌统计。
     *
     * @param response 响应映射
     * @param key 字段键
     * @return 令牌数量，未命中时返回 0
     */
    public int extractOllamaTokens(Map<String, Object> response, String key) {
        if (response != null && key != null && response.get(key) instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}

