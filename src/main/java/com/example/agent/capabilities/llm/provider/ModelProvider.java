package com.example.agent.capabilities.llm.provider;

/**
 * 模型提供商接口。
 */
public interface ModelProvider {

    /**
     * 调用模型。
     *
     * @param definition 模型定义
     * @param request 模型请求
     * @return 模型响应
     */
    ModelResponse invoke(ModelDefinition definition, ModelRequest request);
}