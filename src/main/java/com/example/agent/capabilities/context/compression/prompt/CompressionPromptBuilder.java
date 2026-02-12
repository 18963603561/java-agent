package com.example.agent.capabilities.context.compression.prompt;

import com.example.agent.capabilities.context.compression.application.model.LlmCompressionCommand;

/**
 * 压缩提示词构建器。
 */
public interface CompressionPromptBuilder {

    /**
     * 构建压缩提示词。
     *
     * @param command 压缩命令
     * @return 提示词
     */
    String buildPrompt(LlmCompressionCommand command);
}

