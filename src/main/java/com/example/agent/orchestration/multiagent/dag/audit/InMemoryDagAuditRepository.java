package com.example.agent.orchestration.multiagent.dag.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * DAG 审计内存仓储实现。
 *
 * <p>用途：提供无外部依赖的默认审计存储，实现快速接入与测试验证。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agent.multiagent.storage", name = "mode", havingValue = "inmemory")
public class InMemoryDagAuditRepository implements DagAuditRepository {

    private final Map<String, DagRunAuditRecord> runRecords = new ConcurrentHashMap<>();
    private final Map<String, List<DagNodeAttemptRecord>> attemptRecords = new ConcurrentHashMap<>();
    private final Map<String, List<DagDependencyEventRecord>> dependencyRecords = new ConcurrentHashMap<>();
    private final Map<String, List<DagBackpressureRecord>> backpressureRecords = new ConcurrentHashMap<>();

    @Override
    public void saveRunRecord(DagRunAuditRecord record) {
        if (record == null || !StringUtils.hasText(record.getDagRunId())) {
            return;
        }
        runRecords.put(record.getDagRunId(), copyRunRecord(record));
    }

    @Override
    public void saveNodeAttempt(DagNodeAttemptRecord record) {
        if (record == null || !StringUtils.hasText(record.getDagRunId())) {
            return;
        }
        attemptRecords.computeIfAbsent(record.getDagRunId(), ignore -> new CopyOnWriteArrayList<>())
                .add(copyAttemptRecord(record));
    }

    @Override
    public void saveDependencyEvent(DagDependencyEventRecord record) {
        if (record == null || !StringUtils.hasText(record.getDagRunId())) {
            return;
        }
        dependencyRecords.computeIfAbsent(record.getDagRunId(), ignore -> new CopyOnWriteArrayList<>())
                .add(copyDependencyRecord(record));
    }

    @Override
    public void saveBackpressureRecord(DagBackpressureRecord record) {
        if (record == null || !StringUtils.hasText(record.getDagRunId())) {
            return;
        }
        backpressureRecords.computeIfAbsent(record.getDagRunId(), ignore -> new CopyOnWriteArrayList<>())
                .add(copyBackpressureRecord(record));
    }

    @Override
    public Optional<DagRunAuditRecord> findRunRecord(String dagRunId) {
        DagRunAuditRecord record = runRecords.get(dagRunId);
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(copyRunRecord(record));
    }

    @Override
    public List<DagRunAuditRecord> findRunRecordsByWorkflow(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return Collections.emptyList();
        }
        List<DagRunAuditRecord> result = new ArrayList<>();
        for (DagRunAuditRecord record : runRecords.values()) {
            if (!workflowId.equals(record.getWorkflowId())) {
                continue;
            }
            result.add(copyRunRecord(record));
        }
        return result;
    }

    @Override
    public List<DagNodeAttemptRecord> findAttempts(String dagRunId) {
        return copyList(attemptRecords.get(dagRunId), this::copyAttemptRecord);
    }

    @Override
    public List<DagDependencyEventRecord> findDependencyEvents(String dagRunId) {
        return copyList(dependencyRecords.get(dagRunId), this::copyDependencyRecord);
    }

    @Override
    public List<DagBackpressureRecord> findBackpressureRecords(String dagRunId) {
        return copyList(backpressureRecords.get(dagRunId), this::copyBackpressureRecord);
    }

    private DagRunAuditRecord copyRunRecord(DagRunAuditRecord source) {
        DagRunAuditRecord target = new DagRunAuditRecord();
        target.setDagRunId(source.getDagRunId());
        target.setWorkflowId(source.getWorkflowId());
        target.setTenantId(source.getTenantId());
        target.setStatus(source.getStatus());
        target.setFailurePolicy(source.getFailurePolicy());
        target.setMaxFailures(source.getMaxFailures());
        target.setMaxRetriesPerNode(source.getMaxRetriesPerNode());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setFailures(source.getFailures());
        return target;
    }

    private DagNodeAttemptRecord copyAttemptRecord(DagNodeAttemptRecord source) {
        DagNodeAttemptRecord target = new DagNodeAttemptRecord();
        target.setDagRunId(source.getDagRunId());
        target.setWorkflowId(source.getWorkflowId());
        target.setNodeId(source.getNodeId());
        target.setRoleId(source.getRoleId());
        target.setAttempt(source.getAttempt());
        target.setStatus(source.getStatus());
        target.setReasonCode(source.getReasonCode());
        target.setReasonMessage(source.getReasonMessage());
        target.setStartedAt(source.getStartedAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setDurationMs(source.getDurationMs());
        return target;
    }

    private DagDependencyEventRecord copyDependencyRecord(DagDependencyEventRecord source) {
        DagDependencyEventRecord target = new DagDependencyEventRecord();
        target.setDagRunId(source.getDagRunId());
        target.setWorkflowId(source.getWorkflowId());
        target.setMessageId(source.getMessageId());
        target.setMessageType(source.getMessageType());
        target.setFromNode(source.getFromNode());
        target.setToNode(source.getToNode());
        target.setTopic(source.getTopic());
        target.setDeliveryStatus(source.getDeliveryStatus());
        target.setReason(source.getReason());
        target.setQueueSize(source.getQueueSize());
        target.setCapacity(source.getCapacity());
        target.setOccurredAt(source.getOccurredAt());
        return target;
    }

    private DagBackpressureRecord copyBackpressureRecord(DagBackpressureRecord source) {
        DagBackpressureRecord target = new DagBackpressureRecord();
        target.setDagRunId(source.getDagRunId());
        target.setWorkflowId(source.getWorkflowId());
        target.setNodeId(source.getNodeId());
        target.setReason(source.getReason());
        target.setQueueSize(source.getQueueSize());
        target.setCapacity(source.getCapacity());
        target.setDelayMs(source.getDelayMs());
        target.setOccurredAt(source.getOccurredAt());
        return target;
    }

    private <T> List<T> copyList(List<T> source, java.util.function.Function<T, T> copyFn) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> copied = new ArrayList<>();
        for (T item : source) {
            copied.add(copyFn.apply(item));
        }
        return copied;
    }
}
