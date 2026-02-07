package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.ModelDefinition;
import com.example.agent.capabilities.llm.ModelRequest;
import com.example.agent.capabilities.llm.ModelResponse;

/**
 * 模型提供商适配器。
 */
public interface ModelProviderAdapter {

    /**
     * 判断适配器是否支持当前模型定义。
     *
     * @param definition 模型定义
     * @return 是否支持
     */
    boolean supports(ModelDefinition definition);

    /**
     * 执行模型调用。
     *
     * @param definition 模型定义
     * @param request 模型请求
     * @return 模型响应
     */
    ModelResponse invoke(ModelDefinition definition, ModelRequest request);
}

