package com.example.agent.orchestration.multiagent.dag.audit;

import java.util.ArrayList;
import java.util.List;

/**
 * DAG 审计快照。
 *
 * <p>用途：聚合运行主记录、节点尝试记录、依赖记录与背压记录，作为查询返回结构。</p>
 */
public class DagAuditSnapshot {

    private DagRunAuditRecord runRecord;
    private List<DagNodeAttemptRecord> attempts = new ArrayList<>();
    private List<DagDependencyEventRecord> dependencyEvents = new ArrayList<>();
    private List<DagBackpressureRecord> backpressureRecords = new ArrayList<>();

    public DagRunAuditRecord getRunRecord() {
        return runRecord;
    }

    public void setRunRecord(DagRunAuditRecord runRecord) {
        this.runRecord = runRecord;
    }

    public List<DagNodeAttemptRecord> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<DagNodeAttemptRecord> attempts) {
        this.attempts = attempts == null ? new ArrayList<>() : new ArrayList<>(attempts);
    }

    public List<DagDependencyEventRecord> getDependencyEvents() {
        return dependencyEvents;
    }

    public void setDependencyEvents(List<DagDependencyEventRecord> dependencyEvents) {
        this.dependencyEvents = dependencyEvents == null ? new ArrayList<>() : new ArrayList<>(dependencyEvents);
    }

    public List<DagBackpressureRecord> getBackpressureRecords() {
        return backpressureRecords;
    }

    public void setBackpressureRecords(List<DagBackpressureRecord> backpressureRecords) {
        this.backpressureRecords = backpressureRecords == null ? new ArrayList<>() : new ArrayList<>(backpressureRecords);
    }
}

