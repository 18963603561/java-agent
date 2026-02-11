package com.example.agent.orchestration.multiagent.dag.actor;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagMailboxDeliveryTest {

    @Test
    void shouldReturnStructuredRejectedResultWhenMailboxFull() {
        DagMailbox mailbox = new DagMailbox("node-a", 1);
        DagMessage first = new DagMessage(DagMessageType.DEPENDENCY_SATISFIED,
                "wf-1",
                "a",
                "node-a",
                "topic",
                1L,
                Map.of());
        DagMessage second = new DagMessage(DagMessageType.DEPENDENCY_SATISFIED,
                "wf-1",
                "b",
                "node-a",
                "topic",
                2L,
                Map.of());

        assertTrue(mailbox.deliver(first).isAccepted());
        DagMessageDeliveryResult rejected = mailbox.deliver(second);

        assertFalse(rejected.isAccepted());
        assertEquals("mailbox_full", rejected.getReason());
        assertEquals(1, rejected.getCapacity());
        assertEquals(1, rejected.getQueueSize());
    }
}

