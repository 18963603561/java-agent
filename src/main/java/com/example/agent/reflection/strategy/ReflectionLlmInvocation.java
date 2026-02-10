package com.example.agent.reflection.strategy;

import com.example.agent.capabilities.llm.contract.ModelResponse;
import java.util.Map;

/**
 * LLM 反思调用结果对象。
 * <p>用途：承载提示词、调用元数据与模型响应，作为编排层与判定层之间的数据契约。</p>
 */
public record ReflectionLlmInvocation(String prompt, Map<String, Object> metadata, ModelResponse response) {

    /**
     * 获取模型标识。
     *
     * @return 模型标识；无响应时返回 null
     */
    public String modelId() {
        return response != null ? response.getModelId() : null;
    }

    /**
     * 判断是否存在可解析的模型文本响应。
     *
     * @return true 表示响应存在且非空白
     */
    public boolean hasResponseContent() {
        return response != null && response.getContent() != null && !response.getContent().isBlank();
    }
}

