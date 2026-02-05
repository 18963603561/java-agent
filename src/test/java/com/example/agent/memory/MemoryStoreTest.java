package com.example.agent.memory;

import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.example.agent.capabilities.memory.InMemoryMemoryRepository;
import com.example.agent.capabilities.memory.MemoryExpireProperties;
import com.example.agent.capabilities.memory.MemoryPolicyProperties;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.CompressionRequest;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.CompressedMemoryStore;
import com.example.agent.capabilities.memory.EmbeddingService;
import com.example.agent.capabilities.memory.MemoryExpirationService;
import com.example.agent.capabilities.memory.MemoryPolicy;
import com.example.agent.capabilities.memory.MemoryQuery;
import com.example.agent.capabilities.memory.MemorySearchResult;
import com.example.agent.capabilities.memory.RecentMemoryStore;
import com.example.agent.capabilities.memory.SemanticMemoryStore;
import com.example.agent.capabilities.memory.TokenEstimator;
import com.example.agent.capabilities.memory.VectorStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryStoreTest {

    @Test
    void autoCompressTriggeredBySizeThreshold() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository, policyProps(2, 1000, 3600, 0));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        saveRecord(store, tenantContext, "session-1", "alpha");
        saveRecord(store, tenantContext, "session-1", "beta");
        saveRecord(store, tenantContext, "session-1", "gamma");

        List<MemoryRecord> records = repository.findBySession("tenant-a", "session-1");
        MemoryRecord compressed = records.stream()
                .filter(item -> "compressed".equalsIgnoreCase(item.getLayer()))
                .findFirst()
                .orElse(null);
        assertNotNull(compressed);
        assertTrue(compressed.getSummary().contains("alpha"));
    }

    @Test
    void autoCompressTriggeredByTimePolicy() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository, policyProps(100, 1000, 60, 0));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        MemoryRecord record = new MemoryRecord();
        record.setSessionId("session-2");
        record.setContent("old-content");
        record.setCreatedAt(Instant.now().minusSeconds(3600));
        store.save(record, tenantContext);

        List<MemoryRecord> records = repository.findBySession("tenant-a", "session-2");
        boolean hasCompressed = records.stream()
                .anyMatch(item -> "compressed".equalsIgnoreCase(item.getLayer()));
        assertTrue(hasCompressed);
    }

    @Test
    void searchAggregatesRecentAndCompressed() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository, policyProps(100, 1000, 3600, 0));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        saveRecord(store, tenantContext, "session-3", "hello world");
        saveRecord(store, tenantContext, "session-3", "hello agent");

        CompressionRequest request = new CompressionRequest();
        request.setSessionId("session-3");
        store.compress(request, tenantContext);

        MemoryQuery query = new MemoryQuery();
        query.setSessionId("session-3");
        query.setQuery("hello");
        query.setLimit(10);

        MemorySearchResult result = store.search(query, tenantContext);
        assertTrue(result.getRecords().size() >= 2);
        assertEquals("recent", result.getRecords().get(0).getLayer());
        assertTrue(result.getRecords().stream()
                .anyMatch(item -> "compressed".equalsIgnoreCase(item.getLayer())));
    }

    @Test
    void compressOutputsStructuredSummary() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository, policyProps(100, 1000, 3600, 0));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        saveRecord(store, tenantContext, "session-struct", "first message");
        saveRecord(store, tenantContext, "session-struct", "second message");

        CompressionRequest request = new CompressionRequest();
        request.setSessionId("session-struct");
        MemoryRecord compressed = store.compress(request, tenantContext);

        assertNotNull(compressed);
        assertNotNull(compressed.getConversationSummary());
        assertEquals("v1", compressed.getConversationSummary().getVersion());
        assertNotNull(compressed.getConversationSummary().getSummary());
        assertTrue(compressed.getConversationSummary().getSummaryChars() > 0);
        assertTrue(compressed.getConversationSummary().getBulletCount() > 0);

        assertNotNull(compressed.getWorkingMemorySummary());
        assertEquals("v1", compressed.getWorkingMemorySummary().getVersion());
        assertNotNull(compressed.getWorkingMemorySummary().getSummary());
        assertTrue(compressed.getWorkingMemorySummary().getSummaryChars() > 0);
        assertTrue(compressed.getWorkingMemorySummary().getItemCount() > 0);
    }

    @Test
    void saveAppliesExpiration() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryExpireProperties expireProperties = new MemoryExpireProperties();
        expireProperties.setEnabled(true);
        expireProperties.setTtlSeconds(60);
        MemoryStore store = buildStore(repository, policyProps(100, 1000, 3600, 0), expireProperties);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        MemoryRecord record = new MemoryRecord();
        record.setSessionId("session-expire");
        record.setContent("payload");
        store.save(record, tenantContext);

        List<MemoryRecord> records = repository.findBySession("tenant-a", "session-expire");
        assertEquals(1, records.size());
        assertNotNull(records.get(0).getExpiresAt());
        assertTrue(records.get(0).getExpiresAt().isAfter(records.get(0).getCreatedAt()));
    }

    @Test
    void searchFiltersExpiredRecords() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryExpireProperties expireProperties = new MemoryExpireProperties();
        expireProperties.setEnabled(true);
        expireProperties.setCleanupOnRead(false);
        MemoryStore store = buildStore(repository, policyProps(100, 1000, 3600, 0), expireProperties);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        MemoryRecord record = new MemoryRecord();
        record.setSessionId("session-expired");
        record.setContent("expired-content");
        record.setExpiresAt(Instant.now().minusSeconds(60));
        store.save(record, tenantContext);

        MemoryQuery query = new MemoryQuery();
        query.setSessionId("session-expired");
        query.setQuery("expired");
        query.setLimit(5);

        MemorySearchResult result = store.search(query, tenantContext);
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void searchTriggersCleanupWhenEnabled() {
        TrackingMemoryRepository repository = new TrackingMemoryRepository();
        MemoryExpireProperties expireProperties = new MemoryExpireProperties();
        expireProperties.setEnabled(true);
        expireProperties.setCleanupOnRead(true);
        expireProperties.setCleanupIntervalSeconds(0);
        MemoryStore store = buildStore(repository, policyProps(100, 1000, 3600, 0), expireProperties);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        MemoryQuery query = new MemoryQuery();
        query.setSessionId("session-clean");
        query.setQuery("clean");
        query.setLimit(5);

        store.search(query, tenantContext);
        assertEquals(1, repository.getDeleteCalls());
    }

    private MemoryStore buildStore(InMemoryMemoryRepository repository, MemoryPolicyProperties policyProperties) {
        return buildStore(repository, policyProperties, new MemoryExpireProperties());
    }

    private MemoryStore buildStore(InMemoryMemoryRepository repository,
                                   MemoryPolicyProperties policyProperties,
                                   MemoryExpireProperties expireProperties) {
        ObjectProvider<VectorStore> vectorProvider = new FixedObjectProvider<>(null);
        ObjectProvider<EmbeddingService> embeddingProvider = new FixedObjectProvider<>(null);
        RecentMemoryStore recentMemoryStore = new RecentMemoryStore(repository);
        SemanticMemoryStore semanticMemoryStore = new SemanticMemoryStore(vectorProvider, embeddingProvider);
        MemoryExpirationService expirationService = new MemoryExpirationService(expireProperties);
        CompressedMemoryStore compressedMemoryStore = new CompressedMemoryStore(repository, expirationService);
        MemoryPolicy memoryPolicy = new MemoryPolicy(policyProperties, new TokenEstimator());
        return new MemoryStore(repository, vectorProvider, embeddingProvider, recentMemoryStore,
                semanticMemoryStore, compressedMemoryStore, memoryPolicy, expireProperties, expirationService);
    }

    private MemoryPolicyProperties policyProps(int sizeThreshold, int tokenThreshold,
                                               long maxAgeSeconds, long minCompressIntervalSeconds) {
        MemoryPolicyProperties properties = new MemoryPolicyProperties();
        properties.setEnabled(true);
        properties.setSizeThreshold(sizeThreshold);
        properties.setTokenThreshold(tokenThreshold);
        properties.setMaxAgeSeconds(maxAgeSeconds);
        properties.setMinCompressIntervalSeconds(minCompressIntervalSeconds);
        return properties;
    }

    private void saveRecord(MemoryStore store, TenantContext tenantContext, String sessionId, String content) {
        MemoryRecord record = new MemoryRecord();
        record.setSessionId(sessionId);
        record.setContent(content);
        store.save(record, tenantContext);
    }

    private static class FixedObjectProvider<T> implements ObjectProvider<T> {

        private final T value;

        private FixedObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfAvailable(java.util.function.Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getIfUnique(java.util.function.Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }

        @Override
        public Stream<T> stream() {
            return value == null ? Stream.empty() : Stream.of(value);
        }

        @Override
        public Stream<T> orderedStream() {
            return stream();
        }
    }

    private static class TrackingMemoryRepository extends InMemoryMemoryRepository {

        private int deleteCalls;

        @Override
        public int deleteExpired(String tenantId, Instant now) {
            deleteCalls++;
            return super.deleteExpired(tenantId, now);
        }

        private int getDeleteCalls() {
            return deleteCalls;
        }
    }
}
