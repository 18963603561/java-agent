package com.example.agent.memory;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.security.redaction.RedactionProperties;
import com.example.agent.security.redaction.RedactionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.example.agent.capabilities.memory.InMemoryMemoryRepository;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.CompressedMemoryStore;
import com.example.agent.capabilities.memory.EmbeddingService;
import com.example.agent.capabilities.memory.MemoryExpirationService;
import com.example.agent.capabilities.memory.MemoryExpireProperties;
import com.example.agent.capabilities.memory.MemoryPolicy;
import com.example.agent.capabilities.memory.MemoryPolicyProperties;
import com.example.agent.capabilities.memory.MemoryRecallProperties;
import com.example.agent.capabilities.memory.MemoryRecallResult;
import com.example.agent.capabilities.memory.MemoryRecallService;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.RecentMemoryStore;
import com.example.agent.capabilities.memory.SemanticMemoryStore;
import com.example.agent.capabilities.memory.TokenEstimator;
import com.example.agent.capabilities.memory.VectorStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRecallServiceTest {

    @Test
    void recallReturnsRecordsAndSummary() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository);
        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setEnabled(true);
        properties.setMinQueryLength(2);
        properties.setLimit(5);
        properties.setMaxRecordChars(100);
        properties.setMaxSummaryChars(200);
        MemoryRecallService service = new MemoryRecallService(store, properties,
                buildRedactionService(), new MetricsPublisher(new SimpleMeterRegistry()));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        MemoryRecord record = new MemoryRecord();
        record.setSessionId("session-1");
        record.setContent("hello agent");
        store.save(record, tenantContext);

        TaskRequest request = new TaskRequest();
        request.setQuery("hello");
        request.setSessionId("session-1");
        request.setContext(Map.of());

        MemoryRecallResult result = service.recall(request, request.getContext(), tenantContext);
        assertTrue(result.isUsed());
        assertEquals(1, result.getCount());
        assertNotNull(result.getSummary());
        assertTrue(result.getSummary().contains("hello"));
    }

    @Test
    void recallSkipsWhenSessionMissing() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository);
        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setEnabled(true);
        MemoryRecallService service = new MemoryRecallService(store, properties,
                buildRedactionService(), new MetricsPublisher(new SimpleMeterRegistry()));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        TaskRequest request = new TaskRequest();
        request.setQuery("hello");
        request.setContext(Map.of());

        MemoryRecallResult result = service.recall(request, request.getContext(), tenantContext);
        assertFalse(result.isUsed());
        assertEquals(0, result.getCount());
    }

    @Test
    void recallRedactsSensitiveContent() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository);
        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setEnabled(true);
        properties.setMinQueryLength(1);
        properties.setLimit(5);
        properties.setMaxRecordChars(200);
        properties.setMaxSummaryChars(200);
        MemoryRecallService service = new MemoryRecallService(store, properties,
                buildRedactionService(), new MetricsPublisher(new SimpleMeterRegistry()));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        MemoryRecord record = new MemoryRecord();
        record.setSessionId("session-1");
        record.setContent("联系邮箱 test@example.com");
        store.save(record, tenantContext);

        TaskRequest request = new TaskRequest();
        request.setQuery("邮箱");
        request.setSessionId("session-1");
        request.setContext(Map.of());

        MemoryRecallResult result = service.recall(request, request.getContext(), tenantContext);
        assertTrue(result.isUsed());
        assertTrue(result.getRedactionsAppliedCount() > 0);
        assertTrue(result.getSummary().contains("【已脱敏邮箱】"));
    }

    private MemoryStore buildStore(InMemoryMemoryRepository repository) {
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
        return new MemoryStore(repository, vectorProvider, embeddingProvider, recentMemoryStore,
                semanticMemoryStore, compressedMemoryStore, memoryPolicy, expireProperties, expirationService);
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

    private RedactionService buildRedactionService() {
        RedactionProperties properties = new RedactionProperties();
        properties.setEnabled(true);
        properties.setRejectOnSecrets(true);
        properties.setRedactOnPii(true);
        return new RedactionService(properties, new MetricsPublisher(new SimpleMeterRegistry()));
    }
}
