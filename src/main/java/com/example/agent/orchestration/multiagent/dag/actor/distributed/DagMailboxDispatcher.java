package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import com.example.agent.orchestration.multiagent.dag.actor.DagNodeActor;
import com.example.agent.orchestration.multiagent.dag.actor.recovery.DagDeadLetterMessage;
import com.example.agent.orchestration.multiagent.dag.actor.recovery.DagDeadLetterRepository;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DAG 邮箱分发器。
 *
 * <p>用途：负责从传输层拉取消息并投递到本地节点 Actor，统一处理 ack/nack。</p>
 */
@Component
public class DagMailboxDispatcher {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagMailboxDispatcher.class);

    /**
     * 分布式配置。
     */
    private final DagDistributedProperties distributedProperties;

    /**
     * 传输层实现。
     */
    private final DagMailboxTransport mailboxTransport;

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    /**
     * 死信仓储。
     */
    private final DagDeadLetterRepository deadLetterRepository;

    /**
     * 本地节点执行回调注册表。
     */
    private final Map<String, Consumer<com.example.agent.orchestration.multiagent.dag.actor.DagMessage>> handlers = new ConcurrentHashMap<>();

    public DagMailboxDispatcher(DagDistributedProperties distributedProperties,
                                DagMailboxTransport mailboxTransport,
                                MetricsPublisher metricsPublisher,
                                DagDeadLetterRepository deadLetterRepository) {
        this.distributedProperties = distributedProperties;
        this.mailboxTransport = mailboxTransport;
        this.metricsPublisher = metricsPublisher;
        this.deadLetterRepository = deadLetterRepository;
    }

    /**
     * 注册节点消息处理器。
     */
    public void registerHandler(String dagRunId, String nodeId, DagNodeActor actor) {
        if (!StringUtils.hasText(dagRunId) || !StringUtils.hasText(nodeId) || actor == null) {
            return;
        }
        String handlerKey = buildHandlerKey(dagRunId, nodeId);
        // 关键逻辑：注册后统一由分发器触发 actor 消费消息。
        handlers.put(handlerKey, actor::processMessage);
    }

    /**
     * 注销节点消息处理器。
     */
    public void unregisterHandler(String dagRunId, String nodeId) {
        if (!StringUtils.hasText(dagRunId) || !StringUtils.hasText(nodeId)) {
            return;
        }
        handlers.remove(buildHandlerKey(dagRunId, nodeId));
    }

    /**
     * 分发指定运行的待消费消息。
     */
    public int dispatchDagRun(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return 0;
        }
        // 关键逻辑：先从运输层按实例拉取批量消息，避免跨实例抢占。
        List<DagMessageEnvelope> envelopes = mailboxTransport.poll(distributedProperties.getInstanceId(),
                distributedProperties.getPollBatchSize());
        if (envelopes == null || envelopes.isEmpty()) {
            return 0;
        }
        // 关键逻辑：记录当前待分发消息量，支撑 mailbox lag 可观测性。
        metricsPublisher.recordSummary("dag.mailbox.lag", envelopes.size());
        int dispatched = 0;
        for (DagMessageEnvelope envelope : envelopes) {
            if (envelope == null || !dagRunId.equals(envelope.getDagRunId())) {
                continue;
            }
            String handlerKey = buildHandlerKey(envelope.getDagRunId(), envelope.getNodeId());
            Consumer<com.example.agent.orchestration.multiagent.dag.actor.DagMessage> handler = handlers.get(handlerKey);
            // 关键逻辑：未命中本地处理器时将消息重投递，等待目标节点处理器上线。
            if (handler == null) {
                // 关键逻辑：超限消息直接进入死信，避免无限重投递。
                if (envelope.getDeliveryAttempt() >= distributedProperties.getMaxDeliveryAttempts()) {
                    writeDeadLetter(envelope, "handler_missing_exhausted");
                    mailboxTransport.ack(envelope.getEnvelopeId(), distributedProperties.getInstanceId());
                    metricsPublisher.incrementWithTags("dag.dispatch.dlq", "reason", "handler_missing");
                    continue;
                }
                long backoffMs = calculateBackoff(envelope.getDeliveryAttempt());
                mailboxTransport.nack(envelope.getEnvelopeId(),
                        distributedProperties.getInstanceId(),
                        backoffMs,
                        "handler_missing");
                metricsPublisher.incrementWithTags("dag.dispatch.nack", "reason", "handler_missing");
                continue;
            }
            try {
                // 关键逻辑：执行本地 handler 消费消息，成功后 ack。
                handler.accept(envelope.getMessage());
                mailboxTransport.ack(envelope.getEnvelopeId(), distributedProperties.getInstanceId());
                metricsPublisher.increment("dag.dispatch.ack");
                dispatched++;
            } catch (Exception exception) {
                // 关键逻辑：超过重试上限的异常消息进入死信。
                if (envelope.getDeliveryAttempt() >= distributedProperties.getMaxDeliveryAttempts()) {
                    writeDeadLetter(envelope, exception.getClass().getSimpleName());
                    mailboxTransport.ack(envelope.getEnvelopeId(), distributedProperties.getInstanceId());
                    metricsPublisher.incrementWithTags("dag.dispatch.dlq", "reason", "handler_error");
                    continue;
                }
                // 关键逻辑：消费异常时按退避策略重投递。
                long backoffMs = calculateBackoff(envelope.getDeliveryAttempt());
                mailboxTransport.nack(envelope.getEnvelopeId(),
                        distributedProperties.getInstanceId(),
                        backoffMs,
                        exception.getClass().getSimpleName());
                metricsPublisher.incrementWithTags("dag.dispatch.nack", "reason", "handler_error");
                log.error("DAG消息分发失败, dagRunId={}, nodeId={}, envelopeId={}",
                        envelope.getDagRunId(),
                        envelope.getNodeId(),
                        envelope.getEnvelopeId(),
                        exception);
            }
        }
        return dispatched;
    }

    /**
     * 查询某次运行待消费消息快照。
     */
    public List<DagMessageEnvelope> listPending(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return Collections.emptyList();
        }
        return mailboxTransport.listPendingByDagRun(dagRunId);
    }

    /**
     * 返回当前处理器数量。
     */
    public int handlerCount() {
        return handlers.size();
    }

    /**
     * 发送消息到分布式通道。
     */
    public void send(DagMessageEnvelope envelope) {
        if (envelope == null || !StringUtils.hasText(envelope.getEnvelopeId())) {
            return;
        }
        if (envelope.getCreatedAt() == null) {
            envelope.setCreatedAt(Instant.now());
        }
        mailboxTransport.send(envelope);
        metricsPublisher.increment("dag.dispatch.sent");
    }

    /**
     * 返回处理器视图。
     */
    public Map<String, Integer> handlerSummary() {
        Map<String, Integer> summary = new HashMap<>();
        summary.put("handlerCount", handlers.size());
        return summary;
    }

    /**
     * 计算退避时长。
     */
    private long calculateBackoff(int attempt) {
        long minBackoff = Math.max(1L, distributedProperties.getNackBackoffMinMs());
        long maxBackoff = Math.max(minBackoff, distributedProperties.getNackBackoffMaxMs());
        long factor = 1L << Math.max(0, attempt - 1);
        return Math.min(maxBackoff, minBackoff * factor);
    }

    /**
     * 构建处理器键。
     */
    private String buildHandlerKey(String dagRunId, String nodeId) {
        return dagRunId + ":" + nodeId;
    }

    /**
     * 写入死信记录。
     */
    private void writeDeadLetter(DagMessageEnvelope envelope, String reason) {
        if (deadLetterRepository == null || envelope == null) {
            return;
        }
        DagDeadLetterMessage deadLetterMessage = new DagDeadLetterMessage();
        deadLetterMessage.setDeadLetterId(envelope.getEnvelopeId());
        deadLetterMessage.setDagRunId(envelope.getDagRunId());
        deadLetterMessage.setWorkflowId(envelope.getWorkflowId());
        deadLetterMessage.setNodeId(envelope.getNodeId());
        deadLetterMessage.setReason(reason);
        deadLetterMessage.setDeliveryAttempt(envelope.getDeliveryAttempt());
        deadLetterMessage.setEnvelope(envelope);
        deadLetterRepository.save(deadLetterMessage);
    }
}
