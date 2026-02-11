package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DagShardRouterTest {

    @Test
    void shouldAssignStableOwnerForSameShardKey() {
        DagShardRouter router = new DagShardRouter();
        List<String> instances = List.of("instance-b", "instance-a", "instance-c");

        DagShardAssignment first = router.assign("tenant-1", "wf-1", "node-1", instances);
        DagShardAssignment second = router.assign("tenant-1", "wf-1", "node-1", instances);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getInstanceId(), second.getInstanceId());
        assertEquals(first.getShardKey(), second.getShardKey());
    }

    @Test
    void shouldFailFastWhenNoInstances() {
        DagShardRouter router = new DagShardRouter();

        assertThrows(IllegalArgumentException.class,
                () -> router.assign("tenant-x", "wf-x", "node-x", List.of()));
    }
}
