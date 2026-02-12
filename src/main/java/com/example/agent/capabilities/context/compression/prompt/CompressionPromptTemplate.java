package com.example.agent.capabilities.context.compression.prompt;

/**
 * 压缩提示词模板。
 *
 * <p>用途：集中管理 LLM 压缩模板，避免模板散落在编排代码中。</p>
 */
public final class CompressionPromptTemplate {

    private CompressionPromptTemplate() {
    }

    /**
     * 构建压缩提示词模板。
     *
     * @return 模板内容
     */
    public static String template() {
        return "你是上下文压缩助手。请对输入内容进行压缩，保留任务相关事实与结论。\n"
                + "要求：\n"
                + "1) 输出必须是 JSON 对象；\n"
                + "2) 字段必须包含 summary、summaryVersion；\n"
                + "3) summary 使用简体中文，禁止包含敏感原文。\n"
                + "输出示例：{\"summary\":\"...\",\"summaryVersion\":\"v1\"}";
    }
}

