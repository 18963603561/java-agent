package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 进程内邮箱传输实现。
 *
 * <p>用途：作为分布式接口默认实现，支持单进程与测试环境运行。</p>
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "agent.dag.distributed", name = "transport", havingValue = "inprocess")
public class InProcessDagMailboxTransport implements DagMailboxTransport {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(InProcessDagMailboxTransport.class);

    /**
     * 消息存储。
     */
    private final Map<String, DagMessageEnvelope> envelopeStore = new ConcurrentHashMap<>();

    /**
     * 消息插入顺序。
     */
    private final List<String> envelopeOrder = new CopyOnWriteArrayList<>();

    @Override
    public void send(DagMessageEnvelope envelope) {
        // 关键逻辑：空 envelope 或关键键缺失时直接丢弃，避免脏消息污染通道。
        if (envelope == null || !StringUtils.hasText(envelope.getEnvelopeId())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (envelope.getCreatedAt() == null) {
            envelope.setCreatedAt(Instant.now());
        }
        if (envelope.getAvailableAtEpochMs() <= 0) {
            envelope.setAvailableAtEpochMs(now);
        }
        if (envelope.getDeliveryAttempt() <= 0) {
            envelope.setDeliveryAttempt(1);
        }
        envelopeStore.put(envelope.getEnvelopeId(), envelope);
        if (!envelopeOrder.contains(envelope.getEnvelopeId())) {
            envelopeOrder.add(envelope.getEnvelopeId());
        }
    }

    @Override
    public List<DagMessageEnvelope> poll(String instanceId, int batchSize) {
        // 关键逻辑：归一化 batchSize，防止调用方传递无效值。
        int safeBatchSize = Math.max(1, batchSize);
        long now = System.currentTimeMillis();
        List<DagMessageEnvelope> result = new ArrayList<>();
        // 关键逻辑：按发送顺序遍历，保证同分片内消息消费顺序稳定。
        for (String envelopeId : envelopeOrder) {
            if (result.size() >= safeBatchSize) {
                break;
            }
            DagMessageEnvelope envelope = envelopeStore.get(envelopeId);
            if (envelope == null) {
                continue;
            }
            // 关键逻辑：仅拉取归属本实例的消息，避免跨实例抢占。
            if (!StringUtils.hasText(instanceId) || !instanceId.equals(envelope.getOwnerInstanceId())) {
                continue;
            }
            // 关键逻辑：未到可用时间的消息不拉取。
            if (envelope.getAvailableAtEpochMs() > now) {
                continue;
            }
            // 关键逻辑：拉取后立即设置 ackDeadline，防止重复并发消费。
            envelope.setAckDeadlineEpochMs(now + 10_000L);
            result.add(copyEnvelope(envelope));
        }
        return result;
    }

    @Override
    public void ack(String envelopeId, String instanceId) {
        DagMessageEnvelope envelope = envelopeStore.get(envelopeId);
        if (envelope == null) {
            return;
        }
        // 关键逻辑：仅允许 owner 实例 ack，避免跨实例误删。
        if (!instanceId.equals(envelope.getOwnerInstanceId())) {
            log.warn("消息确认实例不匹配, envelopeId={}, owner={}, caller={}",
                    envelopeId,
                    envelope.getOwnerInstanceId(),
                    instanceId);
            return;
        }
        envelopeStore.remove(envelopeId);
        envelopeOrder.remove(envelopeId);
    }

    @Override
    public void nack(String envelopeId, String instanceId, long backoffMs, String reason) {
        DagMessageEnvelope envelope = envelopeStore.get(envelopeId);
        if (envelope == null) {
            return;
        }
        // 关键逻辑：仅 owner 实例允许 nack，保证语义一致。
        if (!instanceId.equals(envelope.getOwnerInstanceId())) {
            return;
        }
        long now = System.currentTimeMillis();
        envelope.setAvailableAtEpochMs(now + Math.max(1L, backoffMs));
        envelope.setDeliveryAttempt(envelope.getDeliveryAttempt() + 1);
        envelope.setAckDeadlineEpochMs(0L);
        log.warn("消息重投递, envelopeId={}, reason={}, nextAvailableAt={}, attempt={}",
                envelopeId,
                reason,
                envelope.getAvailableAtEpochMs(),
                envelope.getDeliveryAttempt());
    }

    @Override
    public List<DagMessageEnvelope> listPendingByDagRun(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return Collections.emptyList();
        }
        // 关键逻辑：返回副本避免外部修改内部消息状态。
        return envelopeStore.values().stream()
                .filter(envelope -> dagRunId.equals(envelope.getDagRunId()))
                .map(this::copyEnvelope)
                .collect(Collectors.toList());
    }

    /**
     * 复制消息信封。
     */
    private DagMessageEnvelope copyEnvelope(DagMessageEnvelope source) {
        DagMessageEnvelope target = new DagMessageEnvelope();
        target.setEnvelopeId(source.getEnvelopeId());
        target.setDagRunId(source.getDagRunId());
        target.setWorkflowId(source.getWorkflowId());
        target.setNodeId(source.getNodeId());
        target.setShardKey(source.getShardKey());
        target.setMessageId(source.getMessageId());
        target.setOwnerInstanceId(source.getOwnerInstanceId());
        target.setAvailableAtEpochMs(source.getAvailableAtEpochMs());
        target.setDeliveryAttempt(source.getDeliveryAttempt());
        target.setAckDeadlineEpochMs(source.getAckDeadlineEpochMs());
        target.setCreatedAt(source.getCreatedAt());
        target.setMessage(source.getMessage());
        return target;
    }
}
