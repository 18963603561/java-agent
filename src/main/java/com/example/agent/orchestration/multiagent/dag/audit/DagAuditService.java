package com.example.agent.orchestration.multiagent.dag.audit;

import com.example.agent.orchestration.multiagent.dag.domain.port.DagAuditRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * DAG 审计服务。
 *
 * <p>用途：封装审计写入与查询动作，降低运行时与存储实现耦合。</p>
 */
@Service
public class DagAuditService {

    private final DagAuditRepository repository;

    public DagAuditService(DagAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * 保存运行主记录。
     */
    public void saveRunRecord(DagRunAuditRecord record) {
        repository.saveRunRecord(record);
    }

    /**
     * 保存节点尝试记录。
     */
    public void saveNodeAttempt(DagNodeAttemptRecord record) {
        repository.saveNodeAttempt(record);
    }

    /**
     * 保存依赖事件记录。
     */
    public void saveDependencyEvent(DagDependencyEventRecord record) {
        repository.saveDependencyEvent(record);
    }

    /**
     * 保存背压记录。
     */
    public void saveBackpressureRecord(DagBackpressureRecord record) {
        repository.saveBackpressureRecord(record);
    }

    /**
     * 查询审计快照。
     */
    public Optional<DagAuditSnapshot> findSnapshot(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return Optional.empty();
        }
        return repository.findSnapshot(dagRunId);
    }

    /**
     * 按工作流查询运行主记录。
     */
    public List<DagRunAuditRecord> findRunRecordsByWorkflow(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return List.of();
        }
        return repository.findRunRecordsByWorkflow(workflowId);
    }

    /**
     * 创建运行开始记录。
     */
    public DagRunAuditRecord buildRunStartRecord(String dagRunId,
                                                 String workflowId,
                                                 String tenantId,
                                                 String failurePolicy,
                                                 int maxFailures,
                                                 int maxRetriesPerNode,
                                                 Instant startedAt) {
        DagRunAuditRecord record = new DagRunAuditRecord();
        record.setDagRunId(dagRunId);
        record.setWorkflowId(workflowId);
        record.setTenantId(tenantId);
        record.setFailurePolicy(failurePolicy);
        record.setMaxFailures(maxFailures);
        record.setMaxRetriesPerNode(maxRetriesPerNode);
        record.setStatus("RUNNING");
        record.setStartedAt(startedAt);
        return record;
    }

    /**
     * 结束运行记录。
     */
    public DagRunAuditRecord completeRunRecord(DagRunAuditRecord runRecord,
                                               String status,
                                               List<String> failures,
                                               Instant completedAt) {
        if (runRecord == null) {
            return null;
        }
        runRecord.setStatus(status);
        runRecord.setFailures(failures);
        runRecord.setCompletedAt(completedAt);
        return runRecord;
    }
}
