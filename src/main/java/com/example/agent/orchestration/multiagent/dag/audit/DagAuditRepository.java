package com.example.agent.orchestration.multiagent.dag.audit;

import java.util.List;
import java.util.Optional;

/**
 * DAG 审计仓储接口。
 *
 * <p>用途：定义 DAG 运行审计数据的写入与查询契约，便于切换不同存储实现。</p>
 */
public interface DagAuditRepository {

    /**
     * 保存运行主记录。
     */
    void saveRunRecord(DagRunAuditRecord record);

    /**
     * 保存节点尝试记录。
     */
    void saveNodeAttempt(DagNodeAttemptRecord record);

    /**
     * 保存依赖事件记录。
     */
    void saveDependencyEvent(DagDependencyEventRecord record);

    /**
     * 保存背压记录。
     */
    void saveBackpressureRecord(DagBackpressureRecord record);

    /**
     * 查询运行主记录。
     */
    Optional<DagRunAuditRecord> findRunRecord(String dagRunId);

    /**
     * 按工作流查询运行主记录。
     */
    List<DagRunAuditRecord> findRunRecordsByWorkflow(String workflowId);

    /**
     * 查询节点尝试记录。
     */
    List<DagNodeAttemptRecord> findAttempts(String dagRunId);

    /**
     * 查询依赖事件记录。
     */
    List<DagDependencyEventRecord> findDependencyEvents(String dagRunId);

    /**
     * 查询背压记录。
     */
    List<DagBackpressureRecord> findBackpressureRecords(String dagRunId);

    /**
     * 汇总查询审计快照。
     */
    default Optional<DagAuditSnapshot> findSnapshot(String dagRunId) {
        Optional<DagRunAuditRecord> runRecord = findRunRecord(dagRunId);
        if (runRecord.isEmpty()) {
            return Optional.empty();
        }
        DagAuditSnapshot snapshot = new DagAuditSnapshot();
        snapshot.setRunRecord(runRecord.get());
        snapshot.setAttempts(findAttempts(dagRunId));
        snapshot.setDependencyEvents(findDependencyEvents(dagRunId));
        snapshot.setBackpressureRecords(findBackpressureRecords(dagRunId));
        return Optional.of(snapshot);
    }
}
