package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;

/**
 * 模型客户端接口。
 *
 * <p>用途：统一抽象模型调用能力，屏蔽底层提供商实现差异。
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

