package com.example.agent.model;

/**
 * 模型供应商接口。
 */
public interface ModelProvider {

    /**
     * 调用模型。
     *
     * @param definition 模型定义
     * @param prompt 提示词
     * @return 模型响应
     */
    ModelResponse invoke(ModelDefinition definition, String prompt);
}
