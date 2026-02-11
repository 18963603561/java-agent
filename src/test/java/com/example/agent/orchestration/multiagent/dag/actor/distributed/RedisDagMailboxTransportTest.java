package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import com.example.agent.orchestration.multiagent.dag.actor.DagMessage;
import com.example.agent.orchestration.multiagent.dag.actor.DagMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisDagMailboxTransportTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ZSetOperations<String, String> zSetOperations;
    private SetOperations<String, String> setOperations;
    private RedisDagMailboxTransport transport;
    private DagDistributedProperties properties;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        zSetOperations = Mockito.mock(ZSetOperations.class);
        setOperations = Mockito.mock(SetOperations.class);

        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        Mockito.when(redisTemplate.opsForSet()).thenReturn(setOperations);
        Mockito.when(redisTemplate.expire(Mockito.anyString(), Mockito.anyLong(), Mockito.any())).thenReturn(true);

        properties = new DagDistributedProperties();
        properties.setMaxDeliveryAttempts(3);

        transport = new RedisDagMailboxTransport(redisTemplate, new ObjectMapper(), properties);
    }

    @Test
    void shouldSendPollAndAck() {
        DagMessageEnvelope envelope = buildEnvelope("run-1", "wf-1", "node-a", "instance-a", 1, 0);
        String envelopeKey = "dag:mailbox:envelope:" + envelope.getEnvelopeId();
        String json = toJson(envelope);

        Mockito.when(zSetOperations.rangeByScore(Mockito.eq("dag:mailbox:ready:instance-a"),
                Mockito.eq(Double.NEGATIVE_INFINITY),
                Mockito.anyDouble(),
                Mockito.eq(0L),
                Mockito.eq(1L))).thenReturn(setOf(envelope.getEnvelopeId()));
        Mockito.when(valueOperations.get(envelopeKey)).thenReturn(json);
        Mockito.when(zSetOperations.remove("dag:mailbox:ready:instance-a", envelope.getEnvelopeId())).thenReturn(1L);

        transport.send(envelope);
        List<DagMessageEnvelope> polled = transport.poll("instance-a", 1);

        assertEquals(1, polled.size());
        assertEquals(envelope.getEnvelopeId(), polled.get(0).getEnvelopeId());

        transport.ack(envelope.getEnvelopeId(), "instance-a");

        Mockito.verify(zSetOperations).add(Mockito.eq("dag:mailbox:ready:instance-a"),
                Mockito.eq(envelope.getEnvelopeId()),
                Mockito.anyDouble());
        Mockito.verify(zSetOperations).remove("dag:mailbox:inflight:instance-a", envelope.getEnvelopeId());
        Mockito.verify(setOperations).remove("dag:mailbox:dag-index:run-1", envelope.getEnvelopeId());
        Mockito.verify(redisTemplate).delete(envelopeKey);
    }

    @Test
    void shouldNackBackoffAndIncreaseAttempt() {
        DagMessageEnvelope envelope = buildEnvelope("run-2", "wf-2", "node-b", "instance-b", 1, 0);
        String envelopeKey = "dag:mailbox:envelope:" + envelope.getEnvelopeId();

        Mockito.when(valueOperations.get(envelopeKey)).thenReturn(toJson(envelope));

        transport.nack(envelope.getEnvelopeId(), "instance-b", 500L, "handler_error");

        Mockito.verify(zSetOperations).remove("dag:mailbox:inflight:instance-b", envelope.getEnvelopeId());
        Mockito.verify(zSetOperations).add(Mockito.eq("dag:mailbox:ready:instance-b"),
                Mockito.eq(envelope.getEnvelopeId()),
                Mockito.anyDouble());
        Mockito.verify(valueOperations, Mockito.atLeastOnce()).set(Mockito.eq(envelopeKey), Mockito.anyString());
    }

    @Test
    void shouldAckWhenAttemptExhaustedOnNack() {
        DagMessageEnvelope envelope = buildEnvelope("run-3", "wf-3", "node-c", "instance-c", 3, 0);
        String envelopeKey = "dag:mailbox:envelope:" + envelope.getEnvelopeId();

        Mockito.when(valueOperations.get(envelopeKey)).thenReturn(toJson(envelope));

        transport.nack(envelope.getEnvelopeId(), "instance-c", 100L, "too_many_retries");

        Mockito.verify(setOperations).remove("dag:mailbox:dag-index:run-3", envelope.getEnvelopeId());
        Mockito.verify(redisTemplate).delete(envelopeKey);
    }

    @Test
    void shouldListPendingByDagRun() {
        DagMessageEnvelope envelope = buildEnvelope("run-4", "wf-4", "node-d", "instance-d", 1, 0);
        String envelopeKey = "dag:mailbox:envelope:" + envelope.getEnvelopeId();

        Mockito.when(setOperations.members("dag:mailbox:dag-index:run-4")).thenReturn(setOf(envelope.getEnvelopeId()));
        Mockito.when(valueOperations.get(envelopeKey)).thenReturn(toJson(envelope));

        List<DagMessageEnvelope> pending = transport.listPendingByDagRun("run-4");

        assertEquals(1, pending.size());
        assertEquals("run-4", pending.get(0).getDagRunId());
        assertEquals("instance-d", pending.get(0).getOwnerInstanceId());
    }

    private DagMessageEnvelope buildEnvelope(String dagRunId,
                                             String workflowId,
                                             String nodeId,
                                             String ownerInstanceId,
                                             int attempt,
                                             long availableAtEpochMs) {
        DagMessage message = new DagMessage(DagMessageType.DEPENDENCY_SATISFIED,
                workflowId,
                "upstream",
                nodeId,
                "topic.plan",
                1L,
                Map.of("producerNode", "upstream"));
        DagMessageEnvelope envelope = new DagMessageEnvelope();
        envelope.setEnvelopeId(dagRunId + ":" + nodeId + ":" + message.getMessageId());
        envelope.setDagRunId(dagRunId);
        envelope.setWorkflowId(workflowId);
        envelope.setNodeId(nodeId);
        envelope.setMessageId(message.getMessageId());
        envelope.setOwnerInstanceId(ownerInstanceId);
        envelope.setAvailableAtEpochMs(availableAtEpochMs);
        envelope.setDeliveryAttempt(attempt);
        envelope.setCreatedAt(Instant.now());
        envelope.setMessage(message);
        return envelope;
    }

    private java.util.Set<String> setOf(String value) {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        values.add(value);
        return values;
    }

    private String toJson(DagMessageEnvelope envelope) {
        try {
            RedisDagMailboxTransport.EnvelopePayload payload = RedisDagMailboxTransport.EnvelopePayload.fromEnvelope(envelope);
            return new ObjectMapper().writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
