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
        // 构建内存仓库。
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        // 构建内存存储。
        MemoryStore store = buildStore(repository);
        // 构建写入配置。
        MemoryWriteProperties properties = new MemoryWriteProperties();
        // 启用记忆写入。
        properties.setEnabled(true);
        // 启用用户查询写入。
        properties.setSaveUserQuery(true);
        // 启用最终输出写入。
        properties.setSaveFinalOutput(true);
        // 设置记录最大字符数。
        properties.setMaxRecordChars(500);
        // 设置摘要最大字符数。
        properties.setMaxSummaryChars(200);
        // 构建脱敏服务。
        RedactionService redactionService = buildRedactionService();
        // 构建指标发布器。
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        // 构建写入服务。
        MemoryWriteService service = buildWriteService(store, properties, new ObjectMapper(), redactionService,
                metricsPublisher);
        // 构建租户上下文。
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        // 构建任务请求。
        TaskRequest request = new TaskRequest();
        // 设置查询文本。
        request.setQuery("ping");
        // 设置会话标识。
        request.setSessionId("session-1");
        // 设置请求上下文。
        request.setContext(Map.of());

        // 构建最终输出的 meta 映射。
        Map<String, Object> meta = new java.util.HashMap<>();
        // 写入模型标识。
        meta.put("modelId", "model-1");
        // 构建结构化 data 映射。
        Map<String, Object> resultData = new java.util.HashMap<>();
        // 写入结构化答案字段。
        resultData.put("answer", "pong");
        // 构建结构化 result 映射。
        Map<String, Object> resultMap = new java.util.HashMap<>();
        // 写入结果类型字段。
        resultMap.put("kind", "DEFAULT");
        // 写入结构版本字段。
        resultMap.put("schemaVersion", 1);
        // 写入结构化数据字段。
        resultMap.put("data", resultData);
        // 构建决策映射。
        Map<String, Object> decision = new java.util.HashMap<>();
        // 写入决策重试字段。
        decision.put("retry", false);
        // 构建语义摘要映射。
        Map<String, Object> summary = new java.util.HashMap<>();
        // 写入语义摘要文本字段。
        summary.put("text", "pong-summary");
        // 构建最终输出映射。
        Map<String, Object> finalOutput = new java.util.HashMap<>();
        // 写入元信息字段。
        finalOutput.put("meta", meta);
        // 写入结构化结果字段。
        finalOutput.put("result", resultMap);
        // 写入决策字段。
        finalOutput.put("decision", decision);
        // 写入语义摘要字段。
        finalOutput.put("summary", summary);

        // 构建运行结果。
        RuntimeResult runtimeResult = new RuntimeResult();
        // 写入规划摘要。
        runtimeResult.setPlanSummary("plan-summary");
        // 写入最终输出。
        runtimeResult.setFinalOutput(finalOutput);

        // 调用记忆写入入口。
        service.saveTaskMemory(request, runtimeResult, tenantContext, "task-1");

        // 读取写入记录列表。
        List<MemoryRecord> records = repository.findBySession("tenant-a", "session-1");
        // 校验记录数量。
        assertEquals(2, records.size());
        // 初始化查询记录标记。
        boolean foundQuery = false;
        // 初始化摘要记录标记。
        boolean foundSummary = false;
        // 初始化结果内容标记。
        boolean foundResult = false;
        // 初始化任务标识一致性标记。
        boolean taskIdMatched = true;
        // 循环遍历记录集合。
        for (MemoryRecord record : records) {
            // 判断记录内容是否等于查询文本。
            if ("ping".equals(record.getContent())) {
                // 标记查询记录存在。
                foundQuery = true;
            }
            // 判断摘要是否等于语义摘要文本。
            if ("pong-summary".equals(record.getSummary())) {
                // 标记摘要记录存在。
                foundSummary = true;
            }
            // 判断内容是否包含结构化结果字段。
            if (record.getContent() != null && record.getContent().contains("\"result\"")) {
                // 标记结果内容存在。
                foundResult = true;
            }
            // 判断任务标识是否一致。
            if (!"task-1".equals(record.getTaskId())) {
                // 标记任务标识不一致。
                taskIdMatched = false;
            }
        }
        // 校验查询记录存在。
        assertTrue(foundQuery);
        // 校验摘要记录存在。
        assertTrue(foundSummary);
        // 校验结果内容存在。
        assertTrue(foundResult);
        // 校验任务标识一致。
        assertTrue(taskIdMatched);
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
        // 构建内存仓库。
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        // 构建内存存储。
        MemoryStore store = buildStore(repository);
        // 构建写入配置。
        MemoryWriteProperties properties = new MemoryWriteProperties();
        // 启用记忆写入。
        properties.setEnabled(true);
        // 关闭用户查询写入。
        properties.setSaveUserQuery(false);
        // 启用最终输出写入。
        properties.setSaveFinalOutput(true);
        // 设置记录最大字符数。
        properties.setMaxRecordChars(500);
        // 设置摘要最大字符数。
        properties.setMaxSummaryChars(200);
        // 构建脱敏服务。
        RedactionService redactionService = buildRedactionService();
        // 构建指标注册器。
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        // 构建指标发布器。
        MetricsPublisher metricsPublisher = new MetricsPublisher(meterRegistry);

        // 构建对象序列化器。
        ObjectMapper objectMapper = new ObjectMapper();
        // 注册异常序列化模块，模拟序列化失败。
        objectMapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addSerializer(BadValue.class, new BadValueSerializer()));

        // 构建写入服务。
        MemoryWriteService service = buildWriteService(store, properties, objectMapper, redactionService,
                metricsPublisher);
        // 构建租户上下文。
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        // 构建任务请求。
        TaskRequest request = new TaskRequest();
        // 设置会话标识。
        request.setSessionId("session-serialize");
        // 设置请求上下文。
        request.setContext(Map.of("workflowId", "wf-serialize"));

        // 构建结构化 data 映射。
        Map<String, Object> resultData = new java.util.HashMap<>();
        // 写入异常值，触发序列化失败。
        resultData.put("bad", new BadValue("x"));
        // 构建结构化 result 映射。
        Map<String, Object> resultMap = new java.util.HashMap<>();
        // 写入结果类型字段。
        resultMap.put("kind", "DEFAULT");
        // 写入结构版本字段。
        resultMap.put("schemaVersion", 1);
        // 写入结构化数据字段。
        resultMap.put("data", resultData);
        // 构建语义摘要映射。
        Map<String, Object> summary = new java.util.HashMap<>();
        // 写入语义摘要文本字段。
        summary.put("text", "bad-summary");
        // 构建最终输出映射。
        Map<String, Object> finalOutput = new java.util.HashMap<>();
        // 写入结构化结果字段。
        finalOutput.put("result", resultMap);
        // 写入语义摘要字段。
        finalOutput.put("summary", summary);

        // 构建运行结果。
        RuntimeResult runtimeResult = new RuntimeResult();
        // 写入最终输出。
        runtimeResult.setFinalOutput(finalOutput);

        // 调用记忆写入入口。
        service.saveTaskMemory(request, runtimeResult, tenantContext, "task-serialize");

        // 读取写入记录列表。
        List<MemoryRecord> records = repository.findBySession("tenant-a", "session-serialize");
        // 校验记录数量。
        assertEquals(1, records.size());
        // 读取序列化降级计数器。
        Counter counter = meterRegistry.find("memory_write_output_serialize_fallback_total").counter();
        // 校验降级计数器存在且计数增加。
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
