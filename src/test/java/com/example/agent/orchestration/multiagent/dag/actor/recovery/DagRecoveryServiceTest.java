package com.example.agent.orchestration.multiagent.dag.actor.recovery;

import com.example.agent.orchestration.multiagent.dag.actor.DagMessage;
import com.example.agent.orchestration.multiagent.dag.actor.DagMessageType;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxDispatcher;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxTransport;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMessageEnvelope;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeRuntimeSnapshot;
import com.example.agent.orchestration.multiagent.dag.infrastructure.state.InMemoryDagRuntimeStateRepository;
import com.example.agent.orchestration.multiagent.dag.infrastructure.recovery.InMemoryDagDeadLetterRepository;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagRecoveryServiceTest {

    @Test
    void shouldReplayDeadLetterByIdAndDelete() {
        DagMailboxDispatcher dispatcher = Mockito.mock(DagMailboxDispatcher.class);
        DagMailboxTransport transport = Mockito.mock(DagMailboxTransport.class);
        InMemoryDagRuntimeStateRepository runtimeStateRepository = new InMemoryDagRuntimeStateRepository();
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        InMemoryDagDeadLetterRepository deadLetterRepository = new InMemoryDagDeadLetterRepository();

        DagRecoveryService recoveryService = new DagRecoveryService(dispatcher,
                transport,
                runtimeStateRepository,
                workspaceSyncService,
                metricsPublisher,
                deadLetterRepository);

        DagDeadLetterMessage deadLetter = new DagDeadLetterMessage();
        deadLetter.setDeadLetterId("dlq-1");
        deadLetter.setDagRunId("run-1");
        deadLetter.setWorkflowId("wf-1");
        deadLetter.setNodeId("node-a");
        deadLetter.setReason("handler_error");
        deadLetter.setDeliveryAttempt(3);
        deadLetter.setEnvelope(buildEnvelope("run-1", "wf-1", "node-a"));
        deadLetterRepository.save(deadLetter);

        boolean replayed = recoveryService.replayDeadLetterById("dlq-1");

        assertTrue(replayed);
        Mockito.verify(transport).send(Mockito.any(DagMessageEnvelope.class));
        assertTrue(deadLetterRepository.findById("dlq-1").isEmpty());
    }

    @Test
    void shouldRecoverDagRunBySnapshots() {
        DagMailboxDispatcher dispatcher = Mockito.mock(DagMailboxDispatcher.class);
        Mockito.when(dispatcher.dispatchDagRun("run-2")).thenReturn(1);
        DagMailboxTransport transport = Mockito.mock(DagMailboxTransport.class);
        InMemoryDagRuntimeStateRepository runtimeStateRepository = new InMemoryDagRuntimeStateRepository();
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        InMemoryDagDeadLetterRepository deadLetterRepository = new InMemoryDagDeadLetterRepository();

        DagRecoveryService recoveryService = new DagRecoveryService(dispatcher,
                transport,
                runtimeStateRepository,
                workspaceSyncService,
                metricsPublisher,
                deadLetterRepository);

        DagNodeRuntimeSnapshot snapshot = new DagNodeRuntimeSnapshot();
        snapshot.setDagRunId("run-2");
        snapshot.setWorkflowId("wf-2");
        snapshot.setNodeId("node-b");
        snapshot.setStatus("RUNNING");
        snapshot.setRemainingDependencies(1);
        snapshot.setAttempt(1);
        snapshot.setVersion(1L);
        snapshot.setUpdatedAt(Instant.now());
        runtimeStateRepository.save(snapshot);
        workspaceSyncService.append("wf-2", "topic.plan", Map.of("value", "x"));

        int recoveredCount = recoveryService.recoverDagRun("run-2", "wf-2");

        assertEquals(2, recoveredCount);
        Mockito.verify(dispatcher).dispatchDagRun("run-2");
    }

    private DagMessageEnvelope buildEnvelope(String dagRunId, String workflowId, String nodeId) {
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
        envelope.setDeliveryAttempt(1);
        envelope.setCreatedAt(Instant.now());
        envelope.setMessage(message);
        return envelope;
    }
}
