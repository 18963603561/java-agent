package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import com.example.agent.orchestration.multiagent.dag.actor.DagMessage;
import com.example.agent.orchestration.multiagent.dag.actor.DagMessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Redis 邮箱传输实现。
 * <p>用途：提供跨进程消息发送、拉取、确认与重投递能力，保证与进程内实现语义一致。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agent.dag.distributed", name = "transport", havingValue = "redis")
public class RedisDagMailboxTransport implements DagMailboxTransport {

    private static final Logger log = LoggerFactory.getLogger(RedisDagMailboxTransport.class);

    private static final long DEFAULT_TTL_SECONDS = 24L * 60L * 60L;
    private static final long MIN_BACKOFF_MS = 1L;
    private static final long ACK_TIMEOUT_MS = 10_000L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DagDistributedProperties distributedProperties;

    public RedisDagMailboxTransport(StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    DagDistributedProperties distributedProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.distributedProperties = distributedProperties;
    }

    @Override
    public void send(DagMessageEnvelope envelope) {
        if (!isValidEnvelope(envelope)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (envelope.getCreatedAt() == null) {
            envelope.setCreatedAt(Instant.now());
        }
        if (envelope.getDeliveryAttempt() <= 0) {
            envelope.setDeliveryAttempt(1);
        }
        if (envelope.getAvailableAtEpochMs() <= 0L) {
            envelope.setAvailableAtEpochMs(now);
        }
        envelope.setAckDeadlineEpochMs(0L);

        String envelopeKey = envelopeDataKey(envelope.getEnvelopeId());
        String readyQueueKey = readyQueueKey(envelope.getOwnerInstanceId());
        String dagIndexKey = dagPendingIndexKey(envelope.getDagRunId());

        // 外部接口调用：写入 Redis 消息主体。

        redisTemplate.opsForValue().set(envelopeKey, serializeEnvelope(envelope));
        // 外部接口调用：写入 ready 队列时间索引。
        redisTemplate.opsForZSet().add(readyQueueKey, envelope.getEnvelopeId(), envelope.getAvailableAtEpochMs());
        // 外部接口调用：写入 dagRun 维度待处理索引。
        redisTemplate.opsForSet().add(dagIndexKey, envelope.getEnvelopeId());
        // 外部接口调用：刷新 key 过期，避免孤儿数据长期积压。
        expireKeys(envelope.getEnvelopeId(), envelope.getOwnerInstanceId(), envelope.getDagRunId());
    }

    @Override
    public List<DagMessageEnvelope> poll(String instanceId, int batchSize) {
        if (!StringUtils.hasText(instanceId) || batchSize <= 0) {
            return List.of();
        }
        String trimmedInstanceId = instanceId.trim();
        String readyQueueKey = readyQueueKey(trimmedInstanceId);
        String inflightSetKey = inflightSetKey(trimmedInstanceId);
        long now = System.currentTimeMillis();

        // 外部接口调用：读取当前实例可消费消息 ID 集合。

        Set<String> candidateIds = redisTemplate.opsForZSet().rangeByScore(readyQueueKey,
                Double.NEGATIVE_INFINITY,
                now,
                0,
                Math.max(1, batchSize));
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }

        List<DagMessageEnvelope> result = new ArrayList<>();
        for (String envelopeId : candidateIds) {
            if (!StringUtils.hasText(envelopeId)) {
                continue;
            }
            String envelopeKey = envelopeDataKey(envelopeId);
            // 外部接口调用：读取消息主体并校验归属实例。
            String json = redisTemplate.opsForValue().get(envelopeKey);
            DagMessageEnvelope envelope = deserializeEnvelope(json);
            if (envelope == null) {
                // 外部接口调用：清理 ready 中无效索引。
                redisTemplate.opsForZSet().remove(readyQueueKey, envelopeId);
                continue;
            }
            if (!trimmedInstanceId.equals(envelope.getOwnerInstanceId())) {
                continue;
            }
            // 外部接口调用：ready -> inflight 原子迁移，防止并发重复消费。
            Long removed = redisTemplate.opsForZSet().remove(readyQueueKey, envelopeId);
            if (removed == null || removed <= 0L) {
                continue;
            }
            long ackDeadline = now + ACK_TIMEOUT_MS;
            envelope.setAckDeadlineEpochMs(ackDeadline);
            redisTemplate.opsForValue().set(envelopeKey, serializeEnvelope(envelope));
            redisTemplate.opsForZSet().add(inflightSetKey, envelopeId, ackDeadline);
            result.add(copyEnvelope(envelope));
            if (result.size() >= batchSize) {
                break;
            }
        }
        return result;
    }

    @Override
    public void ack(String envelopeId, String instanceId) {
        if (!StringUtils.hasText(envelopeId) || !StringUtils.hasText(instanceId)) {
            return;
        }
        String envelopeKey = envelopeDataKey(envelopeId);
        // 外部接口调用：读取消息主体，用于清理关联索引。
        String json = redisTemplate.opsForValue().get(envelopeKey);
        DagMessageEnvelope envelope = deserializeEnvelope(json);
        if (envelope == null) {
            return;
        }
        if (!Objects.equals(instanceId.trim(), envelope.getOwnerInstanceId())) {
            log.warn("Redis DAG消息 ack 实例不匹配, envelopeId={}, owner={}, caller={}",
                    envelopeId,
                    envelope.getOwnerInstanceId(),
                    instanceId);
            return;
        }

        String readyQueueKey = readyQueueKey(envelope.getOwnerInstanceId());
        String inflightSetKey = inflightSetKey(envelope.getOwnerInstanceId());
        String dagIndexKey = dagPendingIndexKey(envelope.getDagRunId());

        // 外部接口调用：删除 ready/inflight 索引与消息主体，完成确认。

        redisTemplate.opsForZSet().remove(readyQueueKey, envelopeId);
        redisTemplate.opsForZSet().remove(inflightSetKey, envelopeId);
        redisTemplate.opsForSet().remove(dagIndexKey, envelopeId);
        redisTemplate.delete(envelopeKey);
    }

    @Override
    public void nack(String envelopeId, String instanceId, long backoffMs, String reason) {
        if (!StringUtils.hasText(envelopeId) || !StringUtils.hasText(instanceId)) {
            return;
        }
        String envelopeKey = envelopeDataKey(envelopeId);
        // 外部接口调用：读取消息主体并执行回退重投递。
        String json = redisTemplate.opsForValue().get(envelopeKey);
        DagMessageEnvelope envelope = deserializeEnvelope(json);
        if (envelope == null) {
            return;
        }
        if (!Objects.equals(instanceId.trim(), envelope.getOwnerInstanceId())) {
            log.warn("Redis DAG消息 nack 实例不匹配, envelopeId={}, owner={}, caller={}",
                    envelopeId,
                    envelope.getOwnerInstanceId(),
                    instanceId);
            return;
        }

        long effectiveBackoff = Math.max(MIN_BACKOFF_MS, backoffMs);
        int nextAttempt = envelope.getDeliveryAttempt() + 1;
        if (nextAttempt > distributedProperties.getMaxDeliveryAttempts()) {
            // 关键逻辑：超限消息直接确认删除，由上层 dispatcher 写入 DLQ。
            ack(envelopeId, instanceId);
            log.warn("Redis DAG消息超过重试上限并删除, envelopeId={}, reason={}, attempt={}",
                    envelopeId,
                    reason,
                    nextAttempt);
            return;
        }

        envelope.setDeliveryAttempt(nextAttempt);
        envelope.setAckDeadlineEpochMs(0L);
        envelope.setAvailableAtEpochMs(System.currentTimeMillis() + effectiveBackoff);

        String readyQueueKey = readyQueueKey(envelope.getOwnerInstanceId());
        String inflightSetKey = inflightSetKey(envelope.getOwnerInstanceId());

        // 外部接口调用：inflight 回退到 ready 队列，按 backoff 再次可见。

        redisTemplate.opsForZSet().remove(inflightSetKey, envelopeId);
        redisTemplate.opsForValue().set(envelopeKey, serializeEnvelope(envelope));
        redisTemplate.opsForZSet().add(readyQueueKey, envelopeId, envelope.getAvailableAtEpochMs());
        expireKeys(envelope.getEnvelopeId(), envelope.getOwnerInstanceId(), envelope.getDagRunId());
    }

    @Override
    public List<DagMessageEnvelope> listPendingByDagRun(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return Collections.emptyList();
        }
        String dagIndexKey = dagPendingIndexKey(dagRunId);
        // 外部接口调用：按 dagRun 读取待处理 envelopeId 索引。
        Set<String> envelopeIds = redisTemplate.opsForSet().members(dagIndexKey);
        if (envelopeIds == null || envelopeIds.isEmpty()) {
            return List.of();
        }
        List<DagMessageEnvelope> pending = new ArrayList<>();
        for (String envelopeId : envelopeIds) {
            String json = redisTemplate.opsForValue().get(envelopeDataKey(envelopeId));
            DagMessageEnvelope envelope = deserializeEnvelope(json);
            if (envelope != null) {
                pending.add(copyEnvelope(envelope));
                continue;
            }
            // 外部接口调用：清理 dag 索引中已失效的 envelopeId。
            redisTemplate.opsForSet().remove(dagIndexKey, envelopeId);
        }
        return pending;
    }

    private boolean isValidEnvelope(DagMessageEnvelope envelope) {
        if (envelope == null) {
            return false;
        }
        if (!StringUtils.hasText(envelope.getEnvelopeId())) {
            return false;
        }
        if (!StringUtils.hasText(envelope.getOwnerInstanceId())) {
            return false;
        }
        if (!StringUtils.hasText(envelope.getDagRunId())) {
            return false;
        }
        return true;
    }

    private void expireKeys(String envelopeId, String instanceId, String dagRunId) {
        long ttlSeconds = Math.max(60L, DEFAULT_TTL_SECONDS);
        redisTemplate.expire(envelopeDataKey(envelopeId), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(readyQueueKey(instanceId), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(inflightSetKey(instanceId), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(dagPendingIndexKey(dagRunId), ttlSeconds, TimeUnit.SECONDS);
    }

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
        target.setMessage(copyMessage(source.getMessage()));
        return target;
    }

    private DagMessage copyMessage(DagMessage message) {
        if (message == null) {
            return null;
        }
        return new DagMessage(message.getType(),
                message.getWorkflowId(),
                message.getMessageId(),
                message.getFromNode(),
                message.getToNode(),
                message.getTopic(),
                message.getVersion(),
                message.getTimestamp(),
                message.getPayload());
    }

    private String serializeEnvelope(DagMessageEnvelope envelope) {
        try {
            EnvelopePayload payload = EnvelopePayload.fromEnvelope(envelope);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 DAG envelope 失败", exception);
        }
    }

    private DagMessageEnvelope deserializeEnvelope(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            EnvelopePayload payload = objectMapper.readValue(json, EnvelopePayload.class);
            return payload.toEnvelope();
        } catch (JsonProcessingException exception) {
            log.error("反序列化 DAG envelope 失败, json={}", json, exception);
            return null;
        }
    }

    private String envelopeDataKey(String envelopeId) {
        return "dag:mailbox:envelope:" + envelopeId;
    }

    private String readyQueueKey(String instanceId) {
        return "dag:mailbox:ready:" + instanceId;
    }

    private String inflightSetKey(String instanceId) {
        return "dag:mailbox:inflight:" + instanceId;
    }

    private String dagPendingIndexKey(String dagRunId) {
        return "dag:mailbox:dag-index:" + dagRunId;
    }

    /**
     * Redis 序列化载体。
     */
    public static class EnvelopePayload {

        private String envelopeId;
        private String dagRunId;
        private String workflowId;
        private String nodeId;
        private String shardKey;
        private String messageId;
        private String ownerInstanceId;
        private long availableAtEpochMs;
        private int deliveryAttempt;
        private long ackDeadlineEpochMs;
        private long createdAtEpochMs;
        private MessagePayload message;

        public static EnvelopePayload fromEnvelope(DagMessageEnvelope envelope) {
            EnvelopePayload payload = new EnvelopePayload();
            payload.envelopeId = envelope.getEnvelopeId();
            payload.dagRunId = envelope.getDagRunId();
            payload.workflowId = envelope.getWorkflowId();
            payload.nodeId = envelope.getNodeId();
            payload.shardKey = envelope.getShardKey();
            payload.messageId = envelope.getMessageId();
            payload.ownerInstanceId = envelope.getOwnerInstanceId();
            payload.availableAtEpochMs = envelope.getAvailableAtEpochMs();
            payload.deliveryAttempt = envelope.getDeliveryAttempt();
            payload.ackDeadlineEpochMs = envelope.getAckDeadlineEpochMs();
            payload.createdAtEpochMs = envelope.getCreatedAt() == null
                    ? System.currentTimeMillis()
                    : envelope.getCreatedAt().toEpochMilli();
            payload.message = MessagePayload.fromMessage(envelope.getMessage());
            return payload;
        }

        public DagMessageEnvelope toEnvelope() {
            DagMessageEnvelope envelope = new DagMessageEnvelope();
            envelope.setEnvelopeId(envelopeId);
            envelope.setDagRunId(dagRunId);
            envelope.setWorkflowId(workflowId);
            envelope.setNodeId(nodeId);
            envelope.setShardKey(shardKey);
            envelope.setMessageId(messageId);
            envelope.setOwnerInstanceId(ownerInstanceId);
            envelope.setAvailableAtEpochMs(availableAtEpochMs);
            envelope.setDeliveryAttempt(deliveryAttempt);
            envelope.setAckDeadlineEpochMs(ackDeadlineEpochMs);
            envelope.setCreatedAt(Instant.ofEpochMilli(Math.max(0L, createdAtEpochMs)));
            envelope.setMessage(message == null ? null : message.toMessage());
            return envelope;
        }

        public String getEnvelopeId() {
            return envelopeId;
        }

        public void setEnvelopeId(String envelopeId) {
            this.envelopeId = envelopeId;
        }

        public String getDagRunId() {
            return dagRunId;
        }

        public void setDagRunId(String dagRunId) {
            this.dagRunId = dagRunId;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getShardKey() {
            return shardKey;
        }

        public void setShardKey(String shardKey) {
            this.shardKey = shardKey;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getOwnerInstanceId() {
            return ownerInstanceId;
        }

        public void setOwnerInstanceId(String ownerInstanceId) {
            this.ownerInstanceId = ownerInstanceId;
        }

        public long getAvailableAtEpochMs() {
            return availableAtEpochMs;
        }

        public void setAvailableAtEpochMs(long availableAtEpochMs) {
            this.availableAtEpochMs = availableAtEpochMs;
        }

        public int getDeliveryAttempt() {
            return deliveryAttempt;
        }

        public void setDeliveryAttempt(int deliveryAttempt) {
            this.deliveryAttempt = deliveryAttempt;
        }

        public long getAckDeadlineEpochMs() {
            return ackDeadlineEpochMs;
        }

        public void setAckDeadlineEpochMs(long ackDeadlineEpochMs) {
            this.ackDeadlineEpochMs = ackDeadlineEpochMs;
        }

        public long getCreatedAtEpochMs() {
            return createdAtEpochMs;
        }

        public void setCreatedAtEpochMs(long createdAtEpochMs) {
            this.createdAtEpochMs = createdAtEpochMs;
        }

        public MessagePayload getMessage() {
            return message;
        }

        public void setMessage(MessagePayload message) {
            this.message = message;
        }
    }

    /**
     * Redis 消息载体。
     */
    public static class MessagePayload {

        private String type;
        private String workflowId;
        private String messageId;
        private String fromNode;
        private String toNode;
        private String topic;
        private long version;
        private long timestampEpochMs;
        private Map<String, Object> payload;

        public static MessagePayload fromMessage(DagMessage message) {
            if (message == null) {
                return null;
            }
            MessagePayload value = new MessagePayload();
            value.type = message.getType() == null ? null : message.getType().name();
            value.workflowId = message.getWorkflowId();
            value.messageId = message.getMessageId();
            value.fromNode = message.getFromNode();
            value.toNode = message.getToNode();
            value.topic = message.getTopic();
            value.version = message.getVersion();
            value.timestampEpochMs = message.getTimestamp() == null ? 0L : message.getTimestamp().toEpochMilli();
            value.payload = message.getPayload();
            return value;
        }

        public DagMessage toMessage() {
            DagMessageType messageType = StringUtils.hasText(type)
                    ? DagMessageType.valueOf(type)
                    : DagMessageType.DEPENDENCY_SATISFIED;
            return new DagMessage(messageType,
                    workflowId,
                    messageId,
                    fromNode,
                    toNode,
                    topic,
                    version,
                    timestampEpochMs <= 0L ? Instant.now() : Instant.ofEpochMilli(timestampEpochMs),
                    payload == null ? Map.of() : payload);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getFromNode() {
            return fromNode;
        }

        public void setFromNode(String fromNode) {
            this.fromNode = fromNode;
        }

        public String getToNode() {
            return toNode;
        }

        public void setToNode(String toNode) {
            this.toNode = toNode;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public long getVersion() {
            return version;
        }

        public void setVersion(long version) {
            this.version = version;
        }

        public long getTimestampEpochMs() {
            return timestampEpochMs;
        }

        public void setTimestampEpochMs(long timestampEpochMs) {
            this.timestampEpochMs = timestampEpochMs;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        public void setPayload(Map<String, Object> payload) {
            this.payload = payload;
        }
    }
}

