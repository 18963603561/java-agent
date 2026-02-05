package com.example.agent.capabilities.memory;

import java.util.List;

/**
 * 嵌入向量生成服务接口。
 */
public interface EmbeddingService {

    /**
     * 生成文本向量。
     *
     * @param text 输入文本
     * @return 向量数据
     */
    List<Float> embed(String text);
}
