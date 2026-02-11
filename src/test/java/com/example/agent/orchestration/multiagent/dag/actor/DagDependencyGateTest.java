package com.example.agent.orchestration.multiagent.dag.actor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagDependencyGateTest {

    @Test
    void shouldNotReadyWhenDependenciesRemain() {
        DagNodeRuntimeState state = new DagNodeRuntimeState("writer", 2);

        assertFalse(state.tryReady());
        assertEquals(2, state.getRemainingDependencies());
        assertEquals(DagNodeExecutionStatus.PENDING, state.getStatus());
    }

    @Test
    void shouldReadyWhenDependencyCountReachesZero() {
        DagNodeRuntimeState state = new DagNodeRuntimeState("writer", 2);

        assertEquals(1, state.decrementDependency());
        assertFalse(state.tryReady());
        assertEquals(0, state.decrementDependency());
        assertTrue(state.tryReady());
        assertEquals(DagNodeExecutionStatus.READY, state.getStatus());
    }

    @Test
    void shouldRespectLifecycleTransitionOrder() {
        DagNodeRuntimeState state = new DagNodeRuntimeState("writer", 0);

        state.forceReadyWhenNoDependencies();
        assertTrue(state.tryStart());
        assertTrue(state.trySucceed());
        assertFalse(state.tryFail());
        assertEquals(DagNodeExecutionStatus.SUCCEEDED, state.getStatus());
    }

    @Test
    void shouldAllowReadyAfterFailureForRetry() {
        DagNodeRuntimeState state = new DagNodeRuntimeState("writer", 0);

        state.forceReadyWhenNoDependencies();
        assertTrue(state.tryStart());
        assertTrue(state.tryFail());
        assertEquals(DagNodeExecutionStatus.FAILED, state.getStatus());
        assertTrue(state.tryReadyAfterFailure());
        assertEquals(DagNodeExecutionStatus.READY, state.getStatus());
    }
}
