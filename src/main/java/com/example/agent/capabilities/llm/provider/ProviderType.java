package com.example.agent.capabilities.llm.provider;

import java.util.Locale;

/**
 * 模型提供商类型。
 */
public enum ProviderType {

    OPENAI,
    DEEPSEEK,
    OLLAMA,
    UNKNOWN;

    /**
     * 由配置字符串解析提供商类型。
     *
     * @param value 配置值
     * @return 提供商类型
     */
    public static ProviderType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "openai" -> OPENAI;
            case "deepseek" -> DEEPSEEK;
            case "ollama" -> OLLAMA;
            default -> UNKNOWN;
        };
    }
}

