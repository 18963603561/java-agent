package com.example.agent.capabilities.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量存储配置，用于控制向量检索与存储行为。
 */
@Component
@ConfigurationProperties(prefix = "agent.memory.vector")
public class MemoryVectorProperties {

    private boolean enabled;
    private String baseUrl = "http://localhost:6333";
    private String collection = "memory_records";
    private int dimension = 256;
    private int timeoutSeconds = 5;
    private int topK = 5;
    private double scoreThreshold;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }
}
