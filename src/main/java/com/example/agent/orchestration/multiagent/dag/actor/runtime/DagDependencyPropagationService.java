package com.example.agent.orchestration.multiagent.dag.actor.runtime;

import com.example.agent.orchestration.multiagent.dag.actor.DagMessage;
import com.example.agent.orchestration.multiagent.dag.actor.DagMessageType;
import com.example.agent.orchestration.multiagent.dag.actor.DagNodeActor;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagDistributedProperties;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxDispatcher;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMessageEnvelope;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagShardAssignment;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagShardRouter;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagDependencyEventRecord;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.util.StringUtils;

/**
 * DAG 依赖消息传播服务。
 * <p>用途：统一负责下游依赖消息构造、路由、分发与审计落盘，避免多处重复实现。</p>
 */
public class DagDependencyPropagationService {

    private final DagShardRouter dagShardRouter;
    private final DagDistributedProperties dagDistributedProperties;
    private final DagMailboxDispatcher dagMailboxDispatcher;
    private final DagAuditService dagAuditService;

    /**
     * 构造依赖传播服务。
     */
    public DagDependencyPropagationService(DagShardRouter dagShardRouter,
                                           DagDistributedProperties dagDistributedProperties,
                                           DagMailboxDispatcher dagMailboxDispatcher,
                                           DagAuditService dagAuditService) {
        this.dagShardRouter = dagShardRouter;
        this.dagDistributedProperties = dagDistributedProperties;
        this.dagMailboxDispatcher = dagMailboxDispatcher;
        this.dagAuditService = dagAuditService;
    }

    /**
     * 向所有下游节点传播依赖满足消息。
     *
     * @param dagRunId DAG 运行标识
     * @param workflowId 工作流标识
     * @param tenantContext 租户上下文
     * @param producerNodeId 当前生产节点
     * @param downstreamMap 下游映射
     * @param actorMap 已注册 Actor 映射
     * @param messageVersion 消息版本计数器
     */
    public void propagateDependencies(String dagRunId,
                                      String workflowId,
                                      TenantContext tenantContext,
                                      String producerNodeId,
                                      Map<String, List<String>> downstreamMap,
                                      Map<String, DagNodeActor> actorMap,
                                      AtomicLong messageVersion) {
        // 关键逻辑：逐个下游构造 DEPENDENCY_SATISFIED 消息并统一走 dispatcher 通道。
        for (String downstream : downstreamMap.getOrDefault(producerNodeId, List.of())) {
            // 关键逻辑：未注册本地 Actor 的节点直接跳过，避免发送无效消息。
            if (!actorMap.containsKey(downstream)) {
                continue;
            }
            DagMessage dependencyMessage = new DagMessage(DagMessageType.DEPENDENCY_SATISFIED,
                    workflowId,
                    producerNodeId,
                    downstream,
                    "",
                    messageVersion.incrementAndGet(),
                    Map.of("producerNode", producerNodeId));
            DagShardAssignment shardAssignment = dagShardRouter.assign(
                    tenantContext == null ? null : tenantContext.getTenantId(),
                    workflowId,
                    downstream,
                    dagDistributedProperties.resolveActiveInstancesOrThrow("runtime_send_dependency"));
            DagMessageEnvelope envelope = buildEnvelope(dagRunId,
                    workflowId,
                    downstream,
                    dependencyMessage,
                    shardAssignment,
                    1);
            dagMailboxDispatcher.send(envelope);
            // 关键逻辑：依赖消息投递审计统一为 QUEUED，避免多口径分裂。
            saveDependencyAuditRecord(dagRunId,
                    workflowId,
                    dependencyMessage,
                    "QUEUED",
                    null,
                    0,
                    0);
        }
    }

    /**
     * 构建分布式消息信封。
     */
    private DagMessageEnvelope buildEnvelope(String dagRunId,
                                             String workflowId,
                                             String nodeId,
                                             DagMessage message,
                                             DagShardAssignment shardAssignment,
                                             int deliveryAttempt) {
        DagMessageEnvelope envelope = new DagMessageEnvelope();
        envelope.setEnvelopeId(dagRunId + ":" + nodeId + ":" + message.getMessageId());
        envelope.setDagRunId(dagRunId);
        envelope.setWorkflowId(workflowId);
        envelope.setNodeId(nodeId);
        envelope.setMessageId(message.getMessageId());
        envelope.setShardKey(shardAssignment == null ? "" : shardAssignment.getShardKey());
        envelope.setOwnerInstanceId(shardAssignment == null
                ? dagDistributedProperties.getInstanceId()
                : shardAssignment.getInstanceId());
        envelope.setDeliveryAttempt(deliveryAttempt);
        envelope.setAvailableAtEpochMs(System.currentTimeMillis());
        envelope.setMessage(message);
        envelope.setCreatedAt(Instant.now());
        return envelope;
    }

    /**
     * 保存依赖传播审计记录。
     */
    private void saveDependencyAuditRecord(String dagRunId,
                                           String workflowId,
                                           DagMessage dependencyMessage,
                                           String deliveryStatus,
                                           String overrideReason,
                                           int queueSize,
                                           int capacity) {
        if (dependencyMessage == null) {
            return;
        }
        DagDependencyEventRecord record = new DagDependencyEventRecord();
        record.setDagRunId(dagRunId);
        record.setWorkflowId(workflowId);
        record.setMessageId(dependencyMessage.getMessageId());
        record.setMessageType(dependencyMessage.getType() == null ? null : dependencyMessage.getType().name());
        record.setFromNode(dependencyMessage.getFromNode());
        record.setToNode(dependencyMessage.getToNode());
        record.setTopic(dependencyMessage.getTopic());
        record.setDeliveryStatus(deliveryStatus);
        record.setReason(StringUtils.hasText(overrideReason) ? overrideReason : null);
        record.setQueueSize(queueSize);
        record.setCapacity(capacity);
        record.setOccurredAt(Instant.now());
        dagAuditService.saveDependencyEvent(record);
    }
}

