package com.example.agent.capabilities.memory.vector;

import com.example.agent.capabilities.memory.config.MemoryVectorProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 基于哈希的轻量级嵌入生成器，用于无外部模型时的兜底实现。
 */
@Component
@ConditionalOnMissingBean(EmbeddingService.class)
public class HashEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(HashEmbeddingService.class);

    private final MemoryVectorProperties properties;

    public HashEmbeddingService(MemoryVectorProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Float> embed(String text) {
        int configuredDimension = properties != null ? properties.getDimension() : 0;
        int dimension = configuredDimension > 0 ? configuredDimension : 1;
        if (configuredDimension <= 0) {
            log.warn("嵌入维度配置非法，回退到最小维度, configuredDimension={}, fallbackDimension={}",
                    configuredDimension, dimension);
        }
        float[] vector = new float[dimension];
        if (text != null) {
            for (String token : text.split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                int hash = token.hashCode();
                int index = Math.floorMod(hash, dimension);
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
