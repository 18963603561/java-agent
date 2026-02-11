package com.example.agent.orchestration.multiagent.dag.actor.runtime;

import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagNode;
import com.example.agent.orchestration.multiagent.dag.actor.DagBackpressurePolicy;
import com.example.agent.orchestration.multiagent.dag.actor.DagNodeActor;
import com.example.agent.orchestration.multiagent.dag.actor.DagNodeExecutionStatus;
import com.example.agent.orchestration.multiagent.dag.actor.DagNodeRuntimeState;
import com.example.agent.orchestration.multiagent.dag.actor.DagSupervisorPolicy;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagDistributedProperties;
import com.example.agent.orchestration.multiagent.model.ReasonCode;
import com.example.agent.orchestration.multiagent.model.RunStatus;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeLeaseService;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeRuntimeSnapshot;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagNodeAttemptRecord;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagRuntimeStateRepository;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.orchestration.multiagent.observability.MultiAgentEventKeys;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * DAG 节点执行服务。
 *
 * <p>用途：承载节点执行主流程、失败重试、状态迁移与节点级事件发布。</p>
 * <p>边界条件：执行前必须拿到租约，执行中任何异常都按重试策略或失败策略收敛。</p>
 */
public class DagNodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DagNodeExecutionService.class);
    private static final String FAILURE_TOPIC_PREFIX = "FAIL";

    private final WorkspaceSyncService workspaceSyncService;
    private final MultiAgentEventPublisher eventPublisher;
    private final DagAuditService dagAuditService;
    private final DagSupervisorPolicy dagSupervisorPolicy;
    private final DagBackpressurePolicy dagBackpressurePolicy;
    private final DagDistributedProperties dagDistributedProperties;
    private final DagNodeLeaseService dagNodeLeaseService;
    private final DagRuntimeStateRepository dagRuntimeStateRepository;
    private final DagDependencyPropagationService dagDependencyPropagationService;

    /**
     * 构造节点执行服务。
     */
    public DagNodeExecutionService(WorkspaceSyncService workspaceSyncService,
                                   MultiAgentEventPublisher eventPublisher,
                                   DagAuditService dagAuditService,
                                   DagSupervisorPolicy dagSupervisorPolicy,
                                   DagBackpressurePolicy dagBackpressurePolicy,
                                   DagDistributedProperties dagDistributedProperties,
                                   DagNodeLeaseService dagNodeLeaseService,
                                   DagRuntimeStateRepository dagRuntimeStateRepository,
                                   DagDependencyPropagationService dagDependencyPropagationService) {
        this.workspaceSyncService = workspaceSyncService;
        this.eventPublisher = eventPublisher;
        this.dagAuditService = dagAuditService;
        this.dagSupervisorPolicy = dagSupervisorPolicy;
        this.dagBackpressurePolicy = dagBackpressurePolicy;
        this.dagDistributedProperties = dagDistributedProperties;
        this.dagNodeLeaseService = dagNodeLeaseService;
        this.dagRuntimeStateRepository = dagRuntimeStateRepository;
        this.dagDependencyPropagationService = dagDependencyPropagationService;
    }

    /**
     * 执行单个节点。
     *
     * @param context 节点执行上下文
     */
    public void executeNode(NodeExecutionContext context) {
        if (context == null || context.node() == null || context.runtimeState() == null) {
            return;
        }
        String nodeId = context.node().getNodeId();
        String roleId = context.node().getRole() != null ? context.node().getRole().getRoleId() : nodeId;

        boolean leased = dagNodeLeaseService.acquire(context.dagRunId(),
                nodeId,
                dagDistributedProperties.getInstanceId(),
                dagDistributedProperties.getLeaseTtlMs());
        if (!leased) {
            log.warn("DAG节点租约申请失败, dagRunId={}, nodeId={}, owner={}",
                    context.dagRunId(),
                    nodeId,
                    dagDistributedProperties.getInstanceId());
            return;
        }

        int maxAttempt = Math.max(1, dagSupervisorPolicy.getMaxRetriesPerNode() + 1);
        for (int attempt = 1; attempt <= maxAttempt; attempt++) {
            DagNodeAttemptRecord attemptRecord = new DagNodeAttemptRecord();
            attemptRecord.setDagRunId(context.dagRunId());
            attemptRecord.setWorkflowId(context.workflowId());
            attemptRecord.setNodeId(nodeId);
            attemptRecord.setRoleId(roleId);
            attemptRecord.setAttempt(attempt);
            Instant attemptStartedAt = Instant.now();
            attemptRecord.setStartedAt(attemptStartedAt);

            try {
                // 关键逻辑：记录节点开始事件，保证重试轨迹在审计与流事件中可追踪。
                log.info("DAG节点执行开始, workflowId={}, dagRunId={}, nodeId={}, roleId={}, attempt={}",
                        context.workflowId(),
                        context.dagRunId(),
                        nodeId,
                        roleId,
                        attempt);
                eventPublisher.publishDagNodeStarted(context.tenantContext(),
                        context.workflowId(),
                        context.seqCounter(),
                        nodeId,
                        roleId,
                        context.dagRunId(),
                        attempt);

                assertNoInjectedFailureTopic(context.node());

                // 关键逻辑：节点产物写入工作区后再发布更新事件，确保消费者可见一致。
                for (String topic : context.node().getProduces()) {
                    if (!StringUtils.hasText(topic)) {
                        continue;
                    }
                    workspaceSyncService.append(context.workflowId(),
                            topic,
                            Map.of(MultiAgentEventKeys.NODE_ID, nodeId,
                                    MultiAgentEventKeys.ROLE_ID, roleId));
                    eventPublisher.publishWorkspaceUpdated(context.tenantContext(),
                            context.workflowId(),
                            context.seqCounter(),
                            topic,
                            "dag-actor");
                }

                if (!context.runtimeState().trySucceed()) {
                    throw new IllegalStateException("节点状态流转失败, nodeId=" + nodeId);
                }

                saveRuntimeSnapshot(context.dagRunId(),
                        context.workflowId(),
                        nodeId,
                        context.runtimeState(),
                        attempt,
                        attempt);
                attemptRecord.setStatus("SUCCEEDED");
                attemptRecord.setCompletedAt(Instant.now());
                attemptRecord.setDurationMs(Math.max(0L,
                        attemptRecord.getCompletedAt().toEpochMilli() - attemptStartedAt.toEpochMilli()));
                dagAuditService.saveNodeAttempt(attemptRecord);

                context.completedNodes().add(nodeId);
                eventPublisher.publishDagNodeCompleted(context.tenantContext(),
                        context.workflowId(),
                        context.seqCounter(),
                        nodeId,
                        roleId,
                        context.node().getProduces(),
                        context.dagRunId(),
                        attempt);

                dagDependencyPropagationService.propagateDependencies(context.dagRunId(),
                        context.workflowId(),
                        context.tenantContext(),
                        nodeId,
                        context.downstreamMap(),
                        context.actorMap(),
                        context.messageVersion());
                return;
            } catch (Exception exception) {
                if (context.runtimeState().getStatus() == DagNodeExecutionStatus.SUCCEEDED) {
                    log.warn("DAG节点已成功，忽略后续异常, workflowId={}, dagRunId={}, nodeId={}",
                            context.workflowId(),
                            context.dagRunId(),
                            nodeId,
                            exception);
                    return;
                }

                if (attempt < maxAttempt) {
                    // 关键逻辑：重试前强制校准状态机到可重试路径，失败即直接收敛为节点失败。
                    if (!prepareRetryState(context,
                            exception,
                            attempt,
                            attemptRecord,
                            attemptStartedAt,
                            nodeId,
                            roleId)) {
                        return;
                    }
                    continue;
                }

                recordNodeFailure(context,
                        nodeId,
                        roleId,
                        exception,
                        attempt,
                        attemptRecord,
                        attemptStartedAt,
                        ReasonCode.NODE_FAILED);
                return;
            }
        }
    }

    /**
     * 尝试进入重试状态。
     *
     * @return true 表示已进入重试路径
     */
    private boolean prepareRetryState(NodeExecutionContext context,
                                      Exception exception,
                                      int attempt,
                                      DagNodeAttemptRecord attemptRecord,
                                      Instant attemptStartedAt,
                                      String nodeId,
                                      String roleId) {
        if (!context.runtimeState().tryFail() && context.runtimeState().getStatus() != DagNodeExecutionStatus.FAILED) {
            recordNodeFailure(context,
                    nodeId,
                    roleId,
                    exception,
                    attempt,
                    attemptRecord,
                    attemptStartedAt,
                    ReasonCode.STATE_TRANSITION_FAILED);
            return false;
        }

        if (!context.runtimeState().tryReadyAfterFailure()) {
            recordNodeFailure(context,
                    nodeId,
                    roleId,
                    exception,
                    attempt,
                    attemptRecord,
                    attemptStartedAt,
                    ReasonCode.STATE_TRANSITION_FAILED);
            return false;
        }

        if (!context.runtimeState().tryStart()) {
            recordNodeFailure(context,
                    nodeId,
                    roleId,
                    exception,
                    attempt,
                    attemptRecord,
                    attemptStartedAt,
                    ReasonCode.STATE_TRANSITION_FAILED);
            return false;
        }

        long backoffMs = calculateBackoffMs(attempt);
        // 关键逻辑：重试前发布统一重试事件并等待退避，避免瞬时抖动放大。
        eventPublisher.publishDagNodeRetrying(context.tenantContext(),
                context.workflowId(),
                context.seqCounter(),
                nodeId,
                roleId,
                attempt,
                backoffMs,
                exception.getClass().getSimpleName(),
                context.dagRunId(),
                ReasonCode.NODE_RETRY.code());
        log.warn("DAG节点执行失败并重试, workflowId={}, dagRunId={}, nodeId={}, attempt={}, maxAttempt={}, backoffMs={}",
                context.workflowId(),
                context.dagRunId(),
                nodeId,
                attempt,
                Math.max(1, dagSupervisorPolicy.getMaxRetriesPerNode() + 1),
                backoffMs,
                exception);

        sleepQuietly(backoffMs);
        attemptRecord.setStatus("RETRYING");
        attemptRecord.setReasonCode(ReasonCode.NODE_RETRY.code());
        attemptRecord.setReasonMessage(exception.getMessage());
        attemptRecord.setCompletedAt(Instant.now());
        attemptRecord.setDurationMs(Math.max(0L,
                attemptRecord.getCompletedAt().toEpochMilli() - attemptStartedAt.toEpochMilli()));
        dagAuditService.saveNodeAttempt(attemptRecord);
        saveRuntimeSnapshot(context.dagRunId(),
                context.workflowId(),
                nodeId,
                context.runtimeState(),
                attempt,
                attempt);
        return true;
    }

    /**
     * 记录节点失败并发布失败事件。
     */
    private void recordNodeFailure(NodeExecutionContext context,
                                   String nodeId,
                                   String roleId,
                                   Exception exception,
                                   int attempt,
                                   DagNodeAttemptRecord attemptRecord,
                                   Instant attemptStartedAt,
                                   ReasonCode reasonCode) {
        context.runtimeState().forceFail();
        long failedCount = context.totalFailureCounter().incrementAndGet();
        String errorText = exception == null ? "unknown" : String.valueOf(exception.getMessage());
        String failureReason = nodeId + ":" + errorText;
        synchronized (context.failures()) {
            context.failures().add(failureReason);
        }

        eventPublisher.publishDagNodeFailed(context.tenantContext(),
                context.workflowId(),
                context.seqCounter(),
                nodeId,
                roleId,
                errorText,
                context.dagRunId(),
                attempt,
                reasonCode.code());
        eventPublisher.publishTeamStatus(context.tenantContext(),
                context.workflowId(),
                context.seqCounter(),
                RunStatus.DAG_NODE_FAILED.code(),
                Map.of(MultiAgentEventKeys.NODE_ID, nodeId,
                        MultiAgentEventKeys.ROLE_ID, roleId,
                        "failedCount", failedCount,
                        "maxFailures", dagSupervisorPolicy.getMaxFailures(),
                        "activeNodes", context.activeNodes().get()));
        log.error("DAG节点执行失败, workflowId={}, dagRunId={}, nodeId={}, roleId={}, failedCount={}",
                context.workflowId(),
                context.dagRunId(),
                nodeId,
                roleId,
                failedCount,
                exception);

        saveRuntimeSnapshot(context.dagRunId(),
                context.workflowId(),
                nodeId,
                context.runtimeState(),
                attempt,
                attempt);
        attemptRecord.setStatus("FAILED");
        attemptRecord.setReasonCode(reasonCode.code());
        attemptRecord.setReasonMessage(errorText);
        attemptRecord.setCompletedAt(Instant.now());
        attemptRecord.setDurationMs(Math.max(0L,
                attemptRecord.getCompletedAt().toEpochMilli() - attemptStartedAt.toEpochMilli()));
        dagAuditService.saveNodeAttempt(attemptRecord);
    }

    /**
     * 注入失败主题检测。
     */
    private void assertNoInjectedFailureTopic(DagNode node) {
        if (node == null || node.getProduces() == null) {
            return;
        }
        for (String topic : node.getProduces()) {
            if (!StringUtils.hasText(topic)) {
                continue;
            }
            if (topic.toUpperCase().startsWith(FAILURE_TOPIC_PREFIX)) {
                throw new IllegalStateException("Injected failure topic: " + topic);
            }
        }
    }

    /**
     * 计算退避时长。
     */
    private long calculateBackoffMs(int attempt) {
        long minBackoff = dagBackpressurePolicy.getBackoffMinMs();
        long maxBackoff = dagBackpressurePolicy.getBackoffMaxMs();
        long exponential = minBackoff * (1L << Math.max(0, attempt - 1));
        return Math.min(exponential, maxBackoff);
    }

    /**
     * 静默等待。
     */
    private void sleepQuietly(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 保存节点运行态快照。
     */
    private void saveRuntimeSnapshot(String dagRunId,
                                     String workflowId,
                                     String nodeId,
                                     DagNodeRuntimeState runtimeState,
                                     int attempt,
                                     long version) {
        if (runtimeState == null) {
            return;
        }
        DagNodeRuntimeSnapshot snapshot = new DagNodeRuntimeSnapshot();
        snapshot.setDagRunId(dagRunId);
        snapshot.setWorkflowId(workflowId);
        snapshot.setNodeId(nodeId);
        snapshot.setStatus(runtimeState.getStatus() == null ? null : runtimeState.getStatus().name());
        snapshot.setRemainingDependencies(runtimeState.getRemainingDependencies());
        snapshot.setAttempt(attempt);
        snapshot.setVersion(version);
        snapshot.setUpdatedAt(Instant.now());
        dagRuntimeStateRepository.save(snapshot);
    }

    /**
     * 节点执行上下文。
     */
    public record NodeExecutionContext(String workflowId,
                                       String dagRunId,
                                       TenantContext tenantContext,
                                       AtomicLong seqCounter,
                                       DagNode node,
                                       DagNodeRuntimeState runtimeState,
                                       List<String> completedNodes,
                                       List<String> failures,
                                       AtomicLong totalFailureCounter,
                                       AtomicLong activeNodes,
                                       Map<String, List<String>> downstreamMap,
                                       Map<String, DagNodeActor> actorMap,
                                       AtomicLong messageVersion) {
    }
}
