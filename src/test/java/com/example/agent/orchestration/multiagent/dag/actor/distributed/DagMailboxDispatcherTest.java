package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import com.example.agent.orchestration.multiagent.dag.actor.DagMessage;
import com.example.agent.orchestration.multiagent.dag.actor.DagMessageType;
import com.example.agent.orchestration.multiagent.dag.actor.DagNodeActor;
import com.example.agent.orchestration.multiagent.dag.infrastructure.recovery.InMemoryDagDeadLetterRepository;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagMailboxDispatcherTest {

    @Test
    void shouldDispatchAndAckWhenHandlerExists() {
        DagDistributedProperties properties = new DagDistributedProperties();
        InProcessDagMailboxTransport transport = new InProcessDagMailboxTransport();
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        InMemoryDagDeadLetterRepository deadLetterRepository = new InMemoryDagDeadLetterRepository();
        DagMailboxDispatcher dispatcher = new DagMailboxDispatcher(properties, transport, metricsPublisher, deadLetterRepository);

        DagNodeActor actor = Mockito.mock(DagNodeActor.class);
        dispatcher.registerHandler("run-1", "node-a", actor);

        DagMessageEnvelope envelope = buildEnvelope("run-1", "wf-1", "node-a", 1);
        dispatcher.send(envelope);

        int dispatched = dispatcher.dispatchDagRun("run-1");

        assertEquals(1, dispatched);
        Mockito.verify(actor).processMessage(Mockito.any(DagMessage.class));
        assertTrue(dispatcher.listPending("run-1").isEmpty());
    }

    @Test
    void shouldWriteDeadLetterWhenHandlerMissingAndAttemptExhausted() {
        DagDistributedProperties properties = new DagDistributedProperties();
        properties.setMaxDeliveryAttempts(1);
        InProcessDagMailboxTransport transport = new InProcessDagMailboxTransport();
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        InMemoryDagDeadLetterRepository deadLetterRepository = new InMemoryDagDeadLetterRepository();
        DagMailboxDispatcher dispatcher = new DagMailboxDispatcher(properties, transport, metricsPublisher, deadLetterRepository);

        DagMessageEnvelope envelope = buildEnvelope("run-2", "wf-2", "node-missing", 1);
        dispatcher.send(envelope);

        int dispatched = dispatcher.dispatchDagRun("run-2");

        assertEquals(0, dispatched);
        assertTrue(dispatcher.listPending("run-2").isEmpty());
        assertEquals(1, deadLetterRepository.findByDagRun("run-2").size());
        assertEquals("node-missing", deadLetterRepository.findByDagRun("run-2").get(0).getNodeId());
    }

    private DagMessageEnvelope buildEnvelope(String dagRunId, String workflowId, String nodeId, int attempt) {
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
        envelope.setOwnerInstanceId("instance-local");
        envelope.setAvailableAtEpochMs(System.currentTimeMillis());
        envelope.setDeliveryAttempt(attempt);
        envelope.setCreatedAt(Instant.now());
        envelope.setMessage(message);
        return envelope;
    }
}
