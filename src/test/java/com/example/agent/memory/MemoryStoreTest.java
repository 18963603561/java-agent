package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

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

    private MemoryStore buildStore(InMemoryMemoryRepository repository, MemoryPolicyProperties policyProperties) {
        ObjectProvider<VectorStore> vectorProvider = new FixedObjectProvider<>(null);
        ObjectProvider<EmbeddingService> embeddingProvider = new FixedObjectProvider<>(null);
        RecentMemoryStore recentMemoryStore = new RecentMemoryStore(repository);
        SemanticMemoryStore semanticMemoryStore = new SemanticMemoryStore(vectorProvider, embeddingProvider);
        CompressedMemoryStore compressedMemoryStore = new CompressedMemoryStore(repository);
        MemoryPolicy memoryPolicy = new MemoryPolicy(policyProperties, new TokenEstimator());
        return new MemoryStore(repository, vectorProvider, embeddingProvider, recentMemoryStore,
                semanticMemoryStore, compressedMemoryStore, memoryPolicy);
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
}
