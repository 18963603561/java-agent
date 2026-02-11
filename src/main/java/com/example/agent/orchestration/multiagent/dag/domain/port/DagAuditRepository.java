package com.example.agent.orchestration.multiagent.dag.domain.port;

import com.example.agent.orchestration.multiagent.dag.audit.DagBackpressureRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditSnapshot;
import com.example.agent.orchestration.multiagent.dag.audit.DagDependencyEventRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagNodeAttemptRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import java.util.List;
import java.util.Optional;

/**
 * DAG 审计仓储端口。
 * <p>用途：抽象运行记录、节点尝试、依赖事件与背压记录的读写能力。</p>
 */
public interface DagAuditRepository {

    void saveRunRecord(DagRunAuditRecord record);

    Optional<DagRunAuditRecord> findRunRecord(String dagRunId);

    List<DagRunAuditRecord> findRunRecordsByWorkflow(String workflowId);

    void saveNodeAttempt(DagNodeAttemptRecord record);

    List<DagNodeAttemptRecord> findAttempts(String dagRunId);

    void saveDependencyEvent(DagDependencyEventRecord record);

    List<DagDependencyEventRecord> findDependencyEvents(String dagRunId);

    void saveBackpressureRecord(DagBackpressureRecord record);

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
