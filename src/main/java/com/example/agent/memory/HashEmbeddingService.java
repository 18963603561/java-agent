package com.example.agent.memory;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 基于哈希的轻量级嵌入生成器，用于无外部模型时的兜底实现。
 */
@Component
@ConditionalOnMissingBean(EmbeddingService.class)
public class HashEmbeddingService implements EmbeddingService {

    private final MemoryVectorProperties properties;

    public HashEmbeddingService(MemoryVectorProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Float> embed(String text) {
        int dimension = Math.max(1, properties.getDimension());
        float[] vector = new float[dimension];
        if (text != null) {
            for (String token : text.split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                int hash = token.hashCode();
                int index = Math.abs(hash) % dimension;
                vector[index] += (hash % 2 == 0) ? 1.0f : -1.0f;
            }
        }
        normalize(vector);
        List<Float> result = new ArrayList<>(dimension);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum <= 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
