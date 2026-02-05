package com.example.agent.capabilities.llm;

import org.springframework.util.StringUtils;

/**
 * 提示词默认值解析器。
 */
public class PromptTemplateResolver {

    /**
     * 解析系统提示内容，处理空白配置兜底。
     *
     * @param configured 配置值
     * @param defaultMessage 默认值
     * @return 最终系统提示
     */
    public String resolveSystemMessage(String configured, String defaultMessage) {
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return defaultMessage;
    }

    /**
     * 解析开发者提示内容，处理空白配置兜底。
     *
     * @param configured 配置值
     * @param defaultMessage 默认值
     * @return 最终开发者提示
     */
    public String resolveDeveloperMessage(String configured, String defaultMessage) {
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return defaultMessage;
    }
}