package com.example.agent.capabilities.context.model;

/**
 * 上下文构建指标。
 */
public class BuildMetrics {

    /**
     * 构建耗时毫秒数。
     */
    private Long buildMillis;

    /**
     * 检索数量。
     */
    private Integer retrievalCount;

    /**
     * 工具数量。
     */
    private Integer toolCount;

    public Long getBuildMillis() {
        return buildMillis;
    }

    public void setBuildMillis(Long buildMillis) {
        this.buildMillis = buildMillis;
    }

    public Integer getRetrievalCount() {
        return retrievalCount;
    }

    public void setRetrievalCount(Integer retrievalCount) {
        this.retrievalCount = retrievalCount;
    }

    public Integer getToolCount() {
        return toolCount;
    }

    public void setToolCount(Integer toolCount) {
        this.toolCount = toolCount;
    }
}