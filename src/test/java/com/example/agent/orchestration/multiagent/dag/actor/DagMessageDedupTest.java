package com.example.agent.orchestration.multiagent.dag.actor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagMessageDedupTest {

    @Test
    void shouldProcessMessageOnlyOnce() {
        DagNodeRuntimeState state = new DagNodeRuntimeState("writer", 1);

        String messageId = DagMessage.buildMessageId("wf-1", "planner", "writer", "topic.summary", 1L);
        assertTrue(state.registerMessage(messageId));
        assertFalse(state.registerMessage(messageId));

        assertEquals(0, state.decrementDependency());
        assertEquals(0, state.decrementDependency());
        assertTrue(state.tryReady());
        assertFalse(state.tryReady());
    }

    @Test
    void shouldBuildStableMessageId() {
        String id = DagMessage.buildMessageId("wf-1", "planner", "writer", "topic.summary", 3L);
        assertEquals("wf-1:planner:writer:topic.summary:3", id);
    }
}

