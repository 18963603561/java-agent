package com.example.agent.memory;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.security.redaction.RedactionProperties;
import com.example.agent.security.redaction.RedactionService;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.Counter;
import com.example.agent.capabilities.memory.repository.InMemoryMemoryRepository;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.store.CompressedMemoryStore;
import com.example.agent.capabilities.memory.vector.EmbeddingService;
import com.example.agent.capabilities.memory.policy.MemoryExpirationService;
import com.example.agent.capabilities.memory.config.MemoryExpireProperties;
import com.example.agent.capabilities.memory.policy.MemoryPolicy;
import com.example.agent.capabilities.memory.config.MemoryPolicyProperties;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.config.MemoryWriteProperties;
import com.example.agent.capabilities.memory.write.MemoryWriteService;
import com.example.agent.capabilities.memory.store.RecentMemoryStore;
import com.example.agent.capabilities.memory.store.SemanticMemoryStore;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.capabilities.memory.vector.VectorStore;
import com.example.agent.capabilities.memory.store.MemoryMaintenanceService;
import com.example.agent.capabilities.memory.store.MemorySaveOrchestrator;
import com.example.agent.capabilities.memory.store.MemorySearchOrchestrator;
import com.example.agent.capabilities.memory.write.MemoryWriteContextResolver;
import com.example.agent.capabilities.memory.write.MemoryWritePersistenceGateway;
import com.example.agent.capabilities.memory.write.MemoryWriteRecordFactory;
import com.example.agent.capabilities.memory.write.MemoryWriteRedactionProcessor;
import com.example.agent.capabilities.memory.write.MemoryWriteSerializer;

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
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        MemoryWriteService service = buildWriteService(store, properties, new ObjectMapper(), redactionService,
                metricsPublisher);
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
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        MemoryWriteService service = buildWriteService(store, properties, new ObjectMapper(), redactionService,
                metricsPublisher);
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

    @Test
    void saveTaskMemoryShouldFallbackWhenSerializeFailed() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryStore store = buildStore(repository);
        MemoryWriteProperties properties = new MemoryWriteProperties();
        properties.setEnabled(true);
        properties.setSaveUserQuery(false);
        properties.setSaveFinalOutput(true);
        properties.setMaxRecordChars(500);
        properties.setMaxSummaryChars(200);
        RedactionService redactionService = buildRedactionService();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsPublisher metricsPublisher = new MetricsPublisher(meterRegistry);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addSerializer(BadValue.class, new BadValueSerializer()));

        MemoryWriteService service = buildWriteService(store, properties, objectMapper, redactionService,
                metricsPublisher);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        TaskRequest request = new TaskRequest();
        request.setSessionId("session-serialize");
        request.setContext(Map.of("workflowId", "wf-serialize"));

        RuntimeResult result = new RuntimeResult();
        result.setFinalOutput(Map.of("bad", new BadValue("x")));

        service.saveTaskMemory(request, result, tenantContext, "task-serialize");

        List<MemoryRecord> records = repository.findBySession("tenant-a", "session-serialize");
        assertEquals(1, records.size());
        Counter counter = meterRegistry.find("memory_write_output_serialize_fallback_total").counter();
        assertTrue(counter != null && counter.count() >= 1.0);
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
        MemoryMaintenanceService maintenanceService = new MemoryMaintenanceService(
                repository,
                compressedMemoryStore,
                memoryPolicy,
                expireProperties,
                expirationService);
        MemorySaveOrchestrator saveOrchestrator = new MemorySaveOrchestrator(
                recentMemoryStore,
                vectorProvider,
                embeddingProvider,
                expirationService,
                maintenanceService);
        MemorySearchOrchestrator searchOrchestrator = new MemorySearchOrchestrator(
                recentMemoryStore,
                semanticMemoryStore,
                compressedMemoryStore,
                maintenanceService);
        return new MemoryStore(saveOrchestrator, searchOrchestrator, maintenanceService);
    }

    private RedactionService buildRedactionService() {
        RedactionProperties properties = new RedactionProperties();
        properties.setEnabled(true);
        properties.setRejectOnSecrets(true);
        properties.setRedactOnPii(true);
        return new RedactionService(properties, new MetricsPublisher(new SimpleMeterRegistry()));
    }

    private MemoryWriteService buildWriteService(MemoryStore store,
                                                 MemoryWriteProperties properties,
                                                 ObjectMapper objectMapper,
                                                 RedactionService redactionService,
                                                 MetricsPublisher metricsPublisher) {
        MemoryWriteContextResolver contextResolver = new MemoryWriteContextResolver(properties);
        MemoryWriteRedactionProcessor redactionProcessor = new MemoryWriteRedactionProcessor(redactionService);
        MemoryWriteSerializer serializer = new MemoryWriteSerializer(objectMapper, metricsPublisher, contextResolver);
        MemoryWriteRecordFactory recordFactory = new MemoryWriteRecordFactory(properties);
        MemoryWritePersistenceGateway gateway = new MemoryWritePersistenceGateway(store);
        return new MemoryWriteService(properties, contextResolver, redactionProcessor, serializer, recordFactory,
                gateway);
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

    private record BadValue(String value) {
    }

    private static class BadValueSerializer extends JsonSerializer<BadValue> {

        @Override
        public void serialize(BadValue value, JsonGenerator gen, SerializerProvider serializers) {
            throw new IllegalStateException("intentional serialize failure");
        }
    }
}


