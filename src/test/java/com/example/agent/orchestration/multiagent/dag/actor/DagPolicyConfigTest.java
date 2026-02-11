package com.example.agent.orchestration.multiagent.dag.actor;

import com.example.agent.orchestration.multiagent.supervisor.FailurePropagationPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DagPolicyConfigTest {

    @Test
    void shouldClampSupervisorPolicyValues() {
        DagSupervisorPolicy policy = new DagSupervisorPolicy(0,
                -1,
                100L,
                FailurePropagationPolicy.FAIL_FAST);

        assertEquals(1, policy.getMaxFailures());
        assertEquals(0, policy.getMaxRetriesPerNode());
        assertEquals(1000L, policy.getNodeTimeoutMs());
        assertEquals(FailurePropagationPolicy.FAIL_FAST, policy.getFailurePropagationPolicy());
    }

    @Test
    void shouldClampBackpressurePolicyValues() {
        DagBackpressurePolicy policy = new DagBackpressurePolicy(0,
                0,
                0,
                100L,
                0L,
                1L);

        assertEquals(1, policy.getMaxActiveNodes());
        assertEquals(1, policy.getMaxReadyQueueSize());
        assertEquals(1, policy.getMailboxCapacity());
        assertEquals(1000L, policy.getWaitTimeoutMs());
        assertEquals(10L, policy.getBackoffMinMs());
        assertEquals(10L, policy.getBackoffMaxMs());
    }
}

