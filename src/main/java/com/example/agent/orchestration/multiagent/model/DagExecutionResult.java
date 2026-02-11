package com.example.agent.orchestration.multiagent.model;

import java.util.List;
import java.util.Map;

/**
 * DAG 执行结果。
 *
 * <p>用途：显式承载 DAG 路径执行产物，避免主流程以 Map 传递隐式契约。</p>
 */
public class DagExecutionResult {

    /**
     * DAG 运行标识。
     */
    private String dagRunId;

    /**
     * 执行状态。
     */
    private String status;

    /**
     * DAG 拓扑顺序。
     */
    private List<String> dagOrder = List.of();

    /**
     * 节点视图。
     */
    private List<Map<String, Object>> dagNodes = List.of();

    /**
     * 失败摘要。
     */
    private List<String> failures = List.of();

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

    public List<String> getDagOrder() {
        return dagOrder;
    }

    public void setDagOrder(List<String> dagOrder) {
        this.dagOrder = dagOrder == null ? List.of() : List.copyOf(dagOrder);
    }

    public List<Map<String, Object>> getDagNodes() {
        return dagNodes;
    }

    public void setDagNodes(List<Map<String, Object>> dagNodes) {
        this.dagNodes = dagNodes == null ? List.of() : List.copyOf(dagNodes);
    }

    public List<String> getFailures() {
        return failures;
    }

    public void setFailures(List<String> failures) {
        this.failures = failures == null ? List.of() : List.copyOf(failures);
    }
}

