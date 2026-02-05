package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.context.ContextPolicy;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.security.RedactionProperties;
import com.example.agent.security.RedactionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRecallServicePolicyTest {

    @Test
    void recallRespectsRetrievalPriorityOrder() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryRecord semanticRecord = new MemoryRecord();
        semanticRecord.setMemoryId("semantic-1");
        semanticRecord.setSessionId("session-1");
        semanticRecord.setLayer("semantic");
        semanticRecord.setContent("hello semantic");

        VectorStore vectorStore = new TestVectorStore(List.of(semanticRecord));
        MemoryStore store = buildStore(repository, vectorStore);

        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setEnabled(true);
        properties.setMinQueryLength(1);
        properties.setLimit(5);
        properties.setMaxRecordChars(200);
        properties.setMaxSummaryChars(200);
        properties.setIncludeCompressed(true);

        MemoryRecallService service = new MemoryRecallService(store, properties,
                buildRedactionService(), new MetricsPublisher(new SimpleMeterRegistry()));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        MemoryRecord recentRecord = new MemoryRecord();
        recentRecord.setMemoryId("recent-1");
        recentRecord.setSessionId("session-1");
        recentRecord.setContent("hello recent");
        store.save(recentRecord, tenantContext);

        MemoryRecord compressedRecord = new MemoryRecord();
        compressedRecord.setMemoryId("compressed-1");
        compressedRecord.setSessionId("session-1");
        compressedRecord.setTenantId("tenant-a");
        compressedRecord.setLayer("compressed");
        compressedRecord.setContent("hello compressed");
        repository.save(compressedRecord);

        ContextPolicy policy = new ContextPolicy();
        policy.setRetrievalPriority(List.of("RECENT", "SUMMARY", "SEMANTIC"));

        TaskRequest request = new TaskRequest();
        request.setQuery("hello");
        request.setSessionId("session-1");
        request.setContext(Map.of("contextPolicy", policy));

        MemoryRecallResult result = service.recall(request, request.getContext(), tenantContext);
        assertNotNull(result.getRecords());
        List<String> ids = result.getRecords().stream().map(MemoryRecord::getMemoryId).toList();
        assertEquals(List.of("recent-1", "compressed-1", "semantic-1"), ids);
    }

    @Test
    void recallSkipsRedactionWhenSensitiveMaskDisabled() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository, new TestVectorStore(List.of()));
        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setEnabled(true);
        properties.setMinQueryLength(1);
        properties.setLimit(3);
        properties.setMaxRecordChars(200);
        properties.setMaxSummaryChars(200);
        MemoryRecallService service = new MemoryRecallService(store, properties,
                buildRedactionService(), new MetricsPublisher(new SimpleMeterRegistry()));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        MemoryRecord record = new MemoryRecord();
        record.setMemoryId("m1");
        record.setSessionId("session-1");
        record.setContent("联系邮箱 test@example.com");
        store.save(record, tenantContext);

        ContextPolicy policy = new ContextPolicy();
        policy.setEnableSensitiveMask(Boolean.FALSE);

        TaskRequest request = new TaskRequest();
        request.setQuery("邮箱");
        request.setSessionId("session-1");
        request.setContext(Map.of("contextPolicy", policy));

        MemoryRecallResult result = service.recall(request, request.getContext(), tenantContext);
        assertTrue(result.isUsed());
        assertEquals(0, result.getRedactionsAppliedCount());
        assertNotNull(result.getSummary());
        assertTrue(result.getSummary().contains("test@example.com"));
    }

    private MemoryStore buildStore(InMemoryMemoryRepository repository, VectorStore vectorStore) {
        ObjectProvider<VectorStore> vectorProvider = new FixedObjectProvider<>(vectorStore);
        ObjectProvider<EmbeddingService> embeddingProvider = new FixedObjectProvider<>(text -> List.of(0.1f));
        RecentMemoryStore recentMemoryStore = new RecentMemoryStore(repository);
        SemanticMemoryStore semanticMemoryStore = new SemanticMemoryStore(vectorProvider, embeddingProvider);
        MemoryExpireProperties expireProperties = new MemoryExpireProperties();
        MemoryExpirationService expirationService = new MemoryExpirationService(expireProperties);
        CompressedMemoryStore compressedMemoryStore = new CompressedMemoryStore(repository, expirationService);
        MemoryPolicyProperties policyProperties = new MemoryPolicyProperties();
        policyProperties.setEnabled(false);
        MemoryPolicy memoryPolicy = new MemoryPolicy(policyProperties, new TokenEstimator());
        return new MemoryStore(repository, vectorProvider, embeddingProvider, recentMemoryStore,
                semanticMemoryStore, compressedMemoryStore, memoryPolicy, expireProperties, expirationService);
    }

    private RedactionService buildRedactionService() {
        RedactionProperties properties = new RedactionProperties();
        properties.setEnabled(true);
        properties.setRejectOnSecrets(true);
        properties.setRedactOnPii(true);
        return new RedactionService(properties, new MetricsPublisher(new SimpleMeterRegistry()));
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

    private static class TestVectorStore implements VectorStore {

        private final List<MemoryRecord> records;

        private TestVectorStore(List<MemoryRecord> records) {
            this.records = records;
        }

        @Override
        public void upsert(String tenantId, MemoryRecord record, List<Float> embedding) {
            // 测试场景忽略写入
        }

        @Override
        public List<MemoryRecord> search(String tenantId, String sessionId, List<Float> embedding, int limit) {
            return records;
        }
    }
}
