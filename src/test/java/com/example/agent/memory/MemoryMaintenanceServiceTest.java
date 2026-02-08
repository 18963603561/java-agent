package com.example.agent.memory;

import com.example.agent.capabilities.memory.CompressedMemoryStore;
import com.example.agent.capabilities.memory.CompressionRequest;
import com.example.agent.capabilities.memory.InMemoryMemoryRepository;
import com.example.agent.capabilities.memory.MemoryExpireProperties;
import com.example.agent.capabilities.memory.MemoryExpirationService;
import com.example.agent.capabilities.memory.MemoryPolicy;
import com.example.agent.capabilities.memory.MemoryPolicyProperties;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.TokenEstimator;
import com.example.agent.capabilities.memory.store.MemoryMaintenanceService;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryMaintenanceServiceTest {

    @Test
    void compressShouldSkipWhenSessionMissing() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryMaintenanceService service = buildService(repository, 100, 3600, 0);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        CompressionRequest request = new CompressionRequest();
        MemoryRecord compressed = service.compress(request, tenantContext);
        assertNull(compressed);
    }

    @Test
    void autoCompressShouldGenerateCompressedRecordWhenThresholdReached() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryMaintenanceService service = buildService(repository, 2, 3600, 0);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        saveRecent(repository, "tenant-a", "s-1", "a");
        saveRecent(repository, "tenant-a", "s-1", "b");
        saveRecent(repository, "tenant-a", "s-1", "c");

        service.autoCompressIfNeeded("s-1", tenantContext);
        List<MemoryRecord> records = repository.findBySession("tenant-a", "s-1");
        assertTrue(records.stream().anyMatch(item -> "compressed".equalsIgnoreCase(item.getLayer())));
    }

    @Test
    void cleanupExpiredShouldDeleteExpiredRecords() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryExpireProperties expireProperties = new MemoryExpireProperties();
        expireProperties.setEnabled(true);
        expireProperties.setCleanupOnRead(true);
        expireProperties.setCleanupIntervalSeconds(0);
        MemoryExpirationService expirationService = new MemoryExpirationService(expireProperties);
        MemoryPolicyProperties policyProperties = new MemoryPolicyProperties();
        policyProperties.setEnabled(false);
        MemoryPolicy memoryPolicy = new MemoryPolicy(policyProperties, new TokenEstimator());
        CompressedMemoryStore compressedMemoryStore = new CompressedMemoryStore(repository, expirationService);
        MemoryMaintenanceService service = new MemoryMaintenanceService(repository, compressedMemoryStore,
                memoryPolicy, expireProperties, expirationService);

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");
        MemoryRecord expired = new MemoryRecord();
        expired.setMemoryId("expired-1");
        expired.setTenantId("tenant-a");
        expired.setSessionId("s-2");
        expired.setContent("old");
        expired.setExpiresAt(Instant.now().minusSeconds(10));
        repository.save(expired);

        service.cleanupExpiredIfNeeded(tenantContext, "test");
        assertEquals(0, repository.findBySession("tenant-a", "s-2").size());
    }

    @Test
    void compressShouldReturnStructuredCompressedRecord() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryMaintenanceService service = buildService(repository, 100, 3600, 0);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        saveRecent(repository, "tenant-a", "s-3", "first");
        saveRecent(repository, "tenant-a", "s-3", "second");

        CompressionRequest request = new CompressionRequest();
        request.setSessionId("s-3");
        request.setWorkflowId("wf-1");

        MemoryRecord compressed = service.compress(request, tenantContext);
        assertNotNull(compressed);
        assertEquals("compressed", compressed.getLayer());
        assertNotNull(compressed.getConversationSummary());
    }

    private MemoryMaintenanceService buildService(InMemoryMemoryRepository repository,
                                                  int sizeThreshold,
                                                  long maxAgeSeconds,
                                                  long minCompressIntervalSeconds) {
        MemoryExpireProperties expireProperties = new MemoryExpireProperties();
        MemoryExpirationService expirationService = new MemoryExpirationService(expireProperties);
        CompressedMemoryStore compressedMemoryStore = new CompressedMemoryStore(repository, expirationService);

        MemoryPolicyProperties policyProperties = new MemoryPolicyProperties();
        policyProperties.setEnabled(true);
        policyProperties.setSizeThreshold(sizeThreshold);
        policyProperties.setTokenThreshold(10000);
        policyProperties.setMaxAgeSeconds(maxAgeSeconds);
        policyProperties.setMinCompressIntervalSeconds(minCompressIntervalSeconds);
        MemoryPolicy memoryPolicy = new MemoryPolicy(policyProperties, new TokenEstimator());

        return new MemoryMaintenanceService(repository, compressedMemoryStore,
                memoryPolicy, expireProperties, expirationService);
    }

    private void saveRecent(InMemoryMemoryRepository repository, String tenantId, String sessionId, String content) {
        MemoryRecord record = new MemoryRecord();
        record.setMemoryId(UUID.randomUUID().toString());
        record.setTenantId(tenantId);
        record.setSessionId(sessionId);
        record.setLayer("recent");
        record.setContent(content);
        record.setCreatedAt(Instant.now());
        repository.save(record);
    }
}
