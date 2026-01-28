package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.runtime.RuntimeResult;
import com.example.agent.security.RedactionProperties;
import com.example.agent.security.RedactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.example.agent.observability.MetricsPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryWriteServiceTest {

    @Test
    void saveTaskMemoryWritesQueryAndFinalOutput() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository);
        MemoryWriteProperties properties = new MemoryWriteProperties();
        properties.setEnabled(true);
        properties.setSaveUserQuery(true);
        properties.setSaveFinalOutput(true);
        properties.setMaxRecordChars(500);
        properties.setMaxSummaryChars(200);
        RedactionService redactionService = buildRedactionService();
        MemoryWriteService service = new MemoryWriteService(store, properties, new ObjectMapper(), redactionService);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setSessionId("session-1");
        request.setContext(Map.of());

        RuntimeResult result = new RuntimeResult();
        result.setPlanSummary("plan-summary");
        result.setFinalOutput(Map.of("answer", "pong"));

        service.saveTaskMemory(request, result, tenantContext, "task-1");

        List<MemoryRecord> records = repository.findBySession("tenant-a", "session-1");
        assertEquals(2, records.size());
        assertTrue(records.stream().anyMatch(item -> "ping".equals(item.getContent())));
        assertTrue(records.stream().anyMatch(item -> item.getContent() != null
                && item.getContent().contains("\"answer\"")));
        assertTrue(records.stream().allMatch(item -> "task-1".equals(item.getTaskId())));
    }

    @Test
    void saveTaskMemoryRejectsSecret() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository);
        MemoryWriteProperties properties = new MemoryWriteProperties();
        properties.setEnabled(true);
        properties.setSaveUserQuery(true);
        properties.setSaveFinalOutput(false);
        properties.setMaxRecordChars(500);
        properties.setMaxSummaryChars(200);
        RedactionService redactionService = buildRedactionService();
        MemoryWriteService service = new MemoryWriteService(store, properties, new ObjectMapper(), redactionService);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        TaskRequest request = new TaskRequest();
        request.setQuery("token=abcd123456");
        request.setSessionId("session-1");
        request.setContext(Map.of());

        RuntimeResult result = new RuntimeResult();
        result.setFinalOutput(Map.of("answer", "pong"));

        service.saveTaskMemory(request, result, tenantContext, "task-2");

        List<MemoryRecord> records = repository.findBySession("tenant-a", "session-1");
        assertEquals(0, records.size());
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
}
