package com.example.agent.orchestration.multiagent.dag.diagnosis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 诊断报告。
 *
 * <p>用途：沉淀单次 DAG 运行的关键诊断指标与建议动作。</p>
 */
public class DagDiagnosisReport {

    private String workflowId;
    private String dagRunId;
    private String status;
    private String failurePolicy;
    private String criticalPathNodeId;
    private String longestWaitNodeId;
    private String retryHotspotNodeId;
    private String backpressureHotspotNodeId;
    private Map<String, Integer> failureReasonDistribution = new HashMap<>();
    private Map<String, Integer> blockingReasonDistribution = new HashMap<>();
    private List<String> recommendations = new ArrayList<>();

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getDagRunId() {
        return dagRunId;
    }

    public void setDagRunId(String dagRunId) {
        this.dagRunId = dagRunId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(String failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    public String getCriticalPathNodeId() {
        return criticalPathNodeId;
    }

    public void setCriticalPathNodeId(String criticalPathNodeId) {
        this.criticalPathNodeId = criticalPathNodeId;
    }

    public String getLongestWaitNodeId() {
        return longestWaitNodeId;
    }

    public void setLongestWaitNodeId(String longestWaitNodeId) {
        this.longestWaitNodeId = longestWaitNodeId;
    }

    public String getRetryHotspotNodeId() {
        return retryHotspotNodeId;
    }

    public void setRetryHotspotNodeId(String retryHotspotNodeId) {
        this.retryHotspotNodeId = retryHotspotNodeId;
    }

    public String getBackpressureHotspotNodeId() {
        return backpressureHotspotNodeId;
    }

    public void setBackpressureHotspotNodeId(String backpressureHotspotNodeId) {
        this.backpressureHotspotNodeId = backpressureHotspotNodeId;
    }

    public Map<String, Integer> getFailureReasonDistribution() {
        return failureReasonDistribution;
    }

    public void setFailureReasonDistribution(Map<String, Integer> failureReasonDistribution) {
        this.failureReasonDistribution = failureReasonDistribution == null
                ? new HashMap<>()
                : new HashMap<>(failureReasonDistribution);
    }

    public Map<String, Integer> getBlockingReasonDistribution() {
        return blockingReasonDistribution;
    }

    public void setBlockingReasonDistribution(Map<String, Integer> blockingReasonDistribution) {
        this.blockingReasonDistribution = blockingReasonDistribution == null
                ? new HashMap<>()
                : new HashMap<>(blockingReasonDistribution);
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations == null ? new ArrayList<>() : new ArrayList<>(recommendations);
    }
}

