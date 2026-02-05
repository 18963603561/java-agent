package com.example.agent.budget.token;

import java.util.Map;

/**
 * 预算汇总结果。
 */
public class TokenUsageSummary {

    private String taskId;
    private int totalTokens;
    private double totalCostUsd;
    private Map<String, Integer> byModel;
    private Map<String, Double> byProvider;

    public TokenUsageSummary() {
    }

    public TokenUsageSummary(String taskId, int totalTokens, double totalCostUsd,
                             Map<String, Integer> byModel, Map<String, Double> byProvider) {
        this.taskId = taskId;
        this.totalTokens = totalTokens;
        this.totalCostUsd = totalCostUsd;
        this.byModel = byModel;
        this.byProvider = byProvider;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public double getTotalCostUsd() {
        return totalCostUsd;
    }

    public void setTotalCostUsd(double totalCostUsd) {
        this.totalCostUsd = totalCostUsd;
    }

    public Map<String, Integer> getByModel() {
        return byModel;
    }

    public void setByModel(Map<String, Integer> byModel) {
        this.byModel = byModel;
    }

    public Map<String, Double> getByProvider() {
        return byProvider;
    }

    public void setByProvider(Map<String, Double> byProvider) {
        this.byProvider = byProvider;
    }
}
