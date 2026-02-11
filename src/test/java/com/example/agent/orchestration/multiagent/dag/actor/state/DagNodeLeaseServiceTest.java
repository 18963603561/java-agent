package com.example.agent.orchestration.multiagent.dag.actor.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagNodeLeaseServiceTest {

    @Test
    void shouldAllowOnlySingleOwner() {
        DagNodeLeaseService leaseService = new DagNodeLeaseService();

        boolean firstAcquire = leaseService.acquire("run-1", "node-a", "instance-a", 30_000L);
        boolean secondAcquire = leaseService.acquire("run-1", "node-a", "instance-b", 30_000L);

        assertTrue(firstAcquire);
        assertFalse(secondAcquire);
        assertTrue(leaseService.isOwner("run-1", "node-a", "instance-a"));
        assertFalse(leaseService.isOwner("run-1", "node-a", "instance-b"));
    }

    @Test
    void shouldAllowReacquireAfterRelease() {
        DagNodeLeaseService leaseService = new DagNodeLeaseService();

        assertTrue(leaseService.acquire("run-2", "node-b", "instance-a", 30_000L));
        leaseService.release("run-2", "node-b", "instance-a");

        assertTrue(leaseService.acquire("run-2", "node-b", "instance-b", 30_000L));
        assertTrue(leaseService.isOwner("run-2", "node-b", "instance-b"));
    }
}
