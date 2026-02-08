package com.example.agent.memory;

import com.example.agent.capabilities.memory.CompressedMemoryStore;
import com.example.agent.capabilities.memory.MemoryExpireProperties;
import com.example.agent.capabilities.memory.MemoryExpirationService;
import com.example.agent.capabilities.memory.MemoryPolicy;
import com.example.agent.capabilities.memory.MemoryPolicyProperties;
import com.example.agent.capabilities.memory.MemoryQuery;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.RecentMemoryStore;
import com.example.agent.capabilities.memory.RetrievalPriority;
import com.example.agent.capabilities.memory.SemanticMemoryStore;
import com.example.agent.capabilities.memory.TokenEstimator;
import com.example.agent.capabilities.memory.VectorStore;
import com.example.agent.capabilities.memory.EmbeddingService;
import com.example.agent.capabilities.memory.InMemoryMemoryRepository;
import com.example.agent.capabilities.memory.store.MemoryMaintenanceService;
import com.example.agent.capabilities.memory.store.MemorySearchOrchestrator;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySearchOrchestratorTest {

    @Test
    void searchShouldFollowPriorityOrderAndDeduplicate() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        ObjectProvider<VectorStore> vectorProvider = new FixedObjectProvider<>(new FixedVectorStore(List.of(
                buildRecord("semantic-1", "session-1", "semantic", "hello semantic")
        )));
        ObjectProvider<EmbeddingService> embeddingProvider = new FixedObjectProvider<>(text -> List.of(0.2f));

        RecentMemoryStore recentMemoryStore = new RecentMemoryStore(repository);
        SemanticMemoryStore semanticMemoryStore = new SemanticMemoryStore(vectorProvider, embeddingProvider);
        MemoryExpireProperties expireProperties = new MemoryExpireProperties();
        MemoryExpirationService expirationService = new MemoryExpirationService(expireProperties);
        CompressedMemoryStore compressedMemoryStore = new CompressedMemoryStore(repository, expirationService);

        MemoryPolicyProperties policyProperties = new MemoryPolicyProperties();
        policyProperties.setEnabled(false);
        MemoryPolicy memoryPolicy = new MemoryPolicy(policyProperties, new TokenEstimator());
        MemoryMaintenanceService maintenanceService = new MemoryMaintenanceService(repository, compressedMemoryStore,
                memoryPolicy, expireProperties, expirationService);
        MemorySearchOrchestrator orchestrator = new MemorySearchOrchestrator(recentMemoryStore,
                semanticMemoryStore, compressedMemoryStore, maintenanceService);

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        repository.save(buildRecord("recent-1", "session-1", "recent", "hello recent"));
        repository.save(buildRecord("summary-1", "session-1", "compressed", "hello summary"));

        MemoryQuery query = new MemoryQuery();
        query.setSessionId("session-1");
        query.setQuery("hello");
        query.setLimit(10);

        List<MemoryRecord> records = orchestrator.search(query, tenantContext,
                List.of(RetrievalPriority.SUMMARY, RetrievalPriority.RECENT, RetrievalPriority.SEMANTIC)).getRecords();
        List<String> ids = records.stream().map(MemoryRecord::getMemoryId).toList();
        assertEquals(List.of("summary-1", "recent-1", "semantic-1"), ids);
    }

    @Test
    void searchShouldReturnEmptyWhenTenantInvalid() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        ObjectProvider<VectorStore> vectorProvider = new FixedObjectProvider<>(null);
        ObjectProvider<EmbeddingService> embeddingProvider = new FixedObjectProvider<>(null);
        RecentMemoryStore recentMemoryStore = new RecentMemoryStore(repository);
        SemanticMemoryStore semanticMemoryStore = new SemanticMemoryStore(vectorProvider, embeddingProvider);
        MemoryExpireProperties expireProperties = new MemoryExpireProperties();
        MemoryExpirationService expirationService = new MemoryExpirationService(expireProperties);
        CompressedMemoryStore compressedMemoryStore = new CompressedMemoryStore(repository, expirationService);
        MemoryPolicyProperties policyProperties = new MemoryPolicyProperties();
        policyProperties.setEnabled(false);
        MemoryPolicy memoryPolicy = new MemoryPolicy(policyProperties, new TokenEstimator());
        MemoryMaintenanceService maintenanceService = new MemoryMaintenanceService(repository, compressedMemoryStore,
                memoryPolicy, expireProperties, expirationService);
        MemorySearchOrchestrator orchestrator = new MemorySearchOrchestrator(recentMemoryStore,
                semanticMemoryStore, compressedMemoryStore, maintenanceService);

        MemoryQuery query = new MemoryQuery();
        query.setSessionId("session-1");
        query.setQuery("hello");
        query.setLimit(10);

        assertTrue(orchestrator.search(query, null, null).getRecords().isEmpty());
    }

    private MemoryRecord buildRecord(String memoryId, String sessionId, String layer, String content) {
        MemoryRecord record = new MemoryRecord();
        record.setMemoryId(memoryId);
        record.setTenantId("tenant-a");
        record.setSessionId(sessionId);
        record.setLayer(layer);
        record.setContent(content);
        return record;
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

    private static class FixedVectorStore implements VectorStore {

        private final List<MemoryRecord> records;

        private FixedVectorStore(List<MemoryRecord> records) {
            this.records = records;
        }

        @Override
        public void upsert(String tenantId, MemoryRecord record, List<Float> embedding) {
            // 测试场景无需实现
        }

        @Override
        public List<MemoryRecord> search(String tenantId, String sessionId, List<Float> embedding, int limit) {
            return records;
        }
    }
}
