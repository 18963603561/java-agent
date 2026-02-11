package com.example.agent.orchestration.multiagent.handoff;

import com.example.agent.common.error.ErrorCodeException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交接仓储测试。
 */
class HandoffRepositoryTest {

    @Test
    void createShouldStoreByIdAndIdempotencyKey() {
        InMemoryHandoffRepository repository = new InMemoryHandoffRepository();
        HandoffRecord record = buildRecord("handoff-a", "idem-a", HandoffStatus.PENDING, 0L);

        HandoffRecord created = repository.create(record);

        assertNotNull(created);
        assertEquals("handoff-a", created.getHandoffId());
        assertTrue(repository.findById("handoff-a").isPresent());
        assertTrue(repository.findByIdempotencyKey("idem-a").isPresent());
    }

    @Test
    void compareAndSetShouldIncreaseVersion() {
        InMemoryHandoffRepository repository = new InMemoryHandoffRepository();
        HandoffRecord created = repository.create(buildRecord("handoff-b", "idem-b", HandoffStatus.PENDING, 0L));

        HandoffRecord updating = new HandoffRecord();
        updating.setHandoffId(created.getHandoffId());
        updating.setFromAgent(created.getFromAgent());
        updating.setToAgent(created.getToAgent());
        updating.setStatus(HandoffStatus.RUNNING);
        updating.setVersion(created.getVersion());
        updating.setIdempotencyKey(created.getIdempotencyKey());
        updating.setContext(created.getContext());
        updating.setCreatedAt(created.getCreatedAt());
        updating.setUpdatedAt(Instant.now());

        HandoffRecord updated = repository.compareAndSet(updating, created.getVersion());

        assertEquals(1L, updated.getVersion());
        assertEquals(HandoffStatus.RUNNING, updated.getStatus());
    }

    @Test
    void compareAndSetShouldFailWhenVersionConflict() {
        InMemoryHandoffRepository repository = new InMemoryHandoffRepository();
        HandoffRecord created = repository.create(buildRecord("handoff-c", "idem-c", HandoffStatus.PENDING, 0L));

        HandoffRecord stale = new HandoffRecord();
        stale.setHandoffId(created.getHandoffId());
        stale.setFromAgent(created.getFromAgent());
        stale.setToAgent(created.getToAgent());
        stale.setStatus(HandoffStatus.RUNNING);
        stale.setVersion(created.getVersion());
        stale.setIdempotencyKey(created.getIdempotencyKey());
        stale.setContext(created.getContext());
        stale.setCreatedAt(created.getCreatedAt());
        stale.setUpdatedAt(created.getUpdatedAt());

        repository.compareAndSet(stale, 0L);

        ErrorCodeException exception = assertThrows(ErrorCodeException.class,
                () -> repository.compareAndSet(stale, 0L));
        assertEquals("HANDOFF_VERSION_CONFLICT", exception.getErrorCode());
    }

    private HandoffRecord buildRecord(String handoffId,
                                      String idempotencyKey,
                                      HandoffStatus status,
                                      long version) {
        HandoffRecord record = new HandoffRecord();
        record.setHandoffId(handoffId);
        record.setFromAgent("planner");
        record.setToAgent("writer");
        record.setStatus(status);
        record.setVersion(version);
        record.setIdempotencyKey(idempotencyKey);
        record.setContext(Map.of("topic", "analysis"));
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        return record;
    }
}

