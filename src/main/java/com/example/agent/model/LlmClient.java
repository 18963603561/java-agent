package com.example.agent.model;

/**
 * 模型客户端接口，用于统一模型调用。
 */
public interface LlmClient {

    /**
     * 调用模型生成结果。
     *
     * @param request 模型请求
     * @return 模型响应
     */
    ModelResponse generate(ModelRequest request);
}
