package com.example.agent.reflection;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.reflection.model.ReflectionContextMapper;
import com.example.agent.reflection.prompt.ReflectionPromptProvider;
import com.example.agent.reflection.prompt.ReflectionPromptTemplateEngine;
import com.example.agent.reflection.parser.ReflectionResponseParser;
import com.example.agent.reflection.strategy.HeuristicReflectionStrategy;
import com.example.agent.reflection.strategy.LlmReflectionStrategy;
import com.example.agent.reflection.strategy.ReflectionLlmDecisionResolver;
import com.example.agent.reflection.strategy.ReflectionLlmInvocationExecutor;
import com.example.agent.reflection.strategy.ReflectionPromptTraceRecorder;
import com.example.agent.reflection.strategy.ReflectionStrategySelector;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ReflectionServiceTest {

    private ReflectionService createService(ReflectionProperties properties,
                                            MetricsPublisher metricsPublisher,
                                            ModelInvocationService modelInvocationService,
                                            ModelToolResolver modelToolResolver,
                                            PromptAssembler promptAssembler,
                                            ObjectMapper objectMapper,
                                            JsonOutputRepairService jsonOutputRepairService) {
        ReflectionPromptTemplateEngine templateEngine = new ReflectionPromptTemplateEngine();
        ReflectionPromptProvider promptProvider = new ReflectionPromptProvider(
                properties,
                new org.springframework.core.io.DefaultResourceLoader(),
                objectMapper,
                templateEngine
        );
        ReflectionResponseParser parser = new ReflectionResponseParser(objectMapper, jsonOutputRepairService);
        ReflectionLlmInvocationExecutor invocationExecutor = new ReflectionLlmInvocationExecutor(
                modelInvocationService,
                modelToolResolver,
                promptAssembler,
                promptProvider
        );
        ReflectionLlmDecisionResolver decisionResolver = new ReflectionLlmDecisionResolver(
                parser,
                properties,
                metricsPublisher
        );
        ReflectionPromptTraceRecorder traceRecorder = new ReflectionPromptTraceRecorder(modelInvocationService);
        LlmReflectionStrategy llmStrategy = new LlmReflectionStrategy(
                metricsPublisher,
                properties,
                modelInvocationService,
                invocationExecutor,
                decisionResolver,
                traceRecorder
        );
        HeuristicReflectionStrategy heuristicStrategy = new HeuristicReflectionStrategy(properties, metricsPublisher);
        ReflectionStrategySelector selector = new ReflectionStrategySelector(
                properties,
                List.of(llmStrategy, heuristicStrategy)
        );
        return new ReflectionService(properties, new ReflectionContextMapper(), selector);
    }

    @Test
    void reflectRequestsRetryWhenScoreLow() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setMaxRetries(2);
        properties.setConfidenceThreshold(0.8);
        properties.setMinOutputChars(30);
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        StepSpec step = new StepSpec("TOOL", Map.of("critical", true));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("error", "failed"));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1);
        assertTrue(result.retryRequested());
    }

    @Test
    void reflectStopsRetryWhenAttemptsExhausted() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setMaxRetries(1);
        properties.setConfidenceThreshold(0.8);
        properties.setMinOutputChars(30);
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        StepSpec step = new StepSpec("TOOL", Map.of("critical", true));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("error", "failed"));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1);
        assertFalse(result.retryRequested());
    }

    @Test
    void reflectUsesLlmWhenEnabled() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setMaxRetries(2);
        properties.setConfidenceThreshold(0.6);
        properties.setMinOutputChars(10);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        String content = "{\"score\":0.5,\"retry\":true,\"notes\":\"needs retry\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("reflect", content, 10, 5));

        StepSpec step = new StepSpec("TOOL", Map.of("critical", true));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("result", "ok"));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));
        assertTrue(result.retryRequested());
    }

    @Test
    void reflectRepairsOutputWithExtraText() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), repairService);

        String badContent = "璇存槑:{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}鍚庣紑";
        String repaired = "{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), any(ModelScene.class),
                any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("reflect", badContent, 10, 20),
                        new ModelResponse("repair", repaired, 10, 20));

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("answer", "ok"));
        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), 1, "wf-1",
                new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);
        assertEquals(0.9, result.report().score());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_success_total", "scene", "reflection");
    }

    @Test
    void reflectFallsBackWhenRepairFails() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), repairService);

        String badContent = "鏃犳硶瑙ｆ瀽";
        when(modelInvocationService.invoke(any(ModelRequest.class), any(ModelScene.class),
                any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("reflect", badContent, 10, 20),
                        new ModelResponse("repair", "", 10, 20));

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of());
        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), 1, "wf-1",
                new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);
        assertNotNull(result.report());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "reflection");
    }

    @Test
    void reflectPromptUsesSummaryOnly() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        String content = "{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("reflect", content, 10, 5));

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of(
                "contextSnapshot", "big",
                "contextBudget", "big",
                "evidencePack", "big",
                "tokenUsage", "big"
        ));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        Mockito.verify(modelInvocationService).invoke(captor.capture(), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any());
        String prompt = captor.getValue().getPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.contains("contextSnapshot"));
        assertFalse(prompt.contains("contextBudget"));
        assertFalse(prompt.contains("evidencePack"));
        assertFalse(prompt.contains("tokenUsage"));
        assertFalse(prompt.contains("\"output\""));
        assertTrue(prompt.contains("(summary disabled)"));
    }

    @Test
    void reflectPromptFillsDigestSummaryWhenMissing() throws Exception {
        // 构建反思配置对象。
        ReflectionProperties properties = new ReflectionProperties();
        // 启用反思能力开关。
        properties.setEnabled(true);
        // 启用 LLM 反思开关。
        properties.setLlmEnabled(true);
        // 关闭回退开关以验证提示词。
        properties.setFallbackEnabled(false);
        // 构建指标发布器模拟对象。
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        // 构建模型调用服务模拟对象。
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        // 构建工具解析器模拟对象。
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        // 构建提示组装器模拟对象。
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        // 构建 JSON 序列化工具。
        ObjectMapper objectMapper = new ObjectMapper();
        // 构建输出修复服务模拟对象。
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        // 调用工厂方法创建反思服务。
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, objectMapper, repairService);

        // 构建模型返回内容。
        String content = "{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}";
        // 配置模型调用模拟返回。
        org.mockito.stubbing.OngoingStubbing<ModelResponse> stubbing = when(modelInvocationService.invoke(
                any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()));
        // 写入模型返回结果。
        stubbing.thenReturn(new ModelResponse("reflect", content, 10, 5));

        // 初始化语义摘要映射。
        Map<String, Object> summaryView = new java.util.HashMap<>();
        // 写入语义摘要文本字段。
        summaryView.put(RuntimeOutputKeys.SUMMARY_TEXT, "语义摘要");
        // 写入截断标记字段。
        summaryView.put(RuntimeOutputKeys.TRUNCATED, false);
        // 构建步骤输出载荷。
        Map<String, Object> payload = Map.of();
        // 生成步骤输出对象。
        StepExecutionOutput output = StepExecutionOutput.fromPayload(payload);
        // 注入语义摘要映射到步骤输出。
        StepExecutionOutput outputWithSummary = output.withSummary(summaryView);

        // 构建步骤定义。
        StepSpec step = new StepSpec("TOOL", Map.of());
        // 调用反思服务执行反思。
        ReflectionResult result = service.reflect(step, outputWithSummary,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));

        // 校验反思结果不为空。
        assertNotNull(result);

        // 构建参数捕获器。
        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        // 验证模型调用并捕获请求。
        Mockito.verify(modelInvocationService).invoke(captor.capture(), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any());
        // 读取提示词内容。
        String prompt = captor.getValue().getPrompt();
        // 校验提示词不为空。
        assertNotNull(prompt);
        // 校验提示词不包含原始字段。
        assertFalse(prompt.contains("contextSnapshot"));
        // 校验提示词不包含原始字段。
        assertFalse(prompt.contains("contextBudget"));
        // 校验提示词不包含原始字段。
        assertFalse(prompt.contains("evidencePack"));
        // 校验提示词不包含原始字段。
        assertFalse(prompt.contains("tokenUsage"));

        // 定义上下文标记。
        String marker = "REFLECTION_CONTEXT_JSON:";
        // 查找标记位置。
        int index = prompt.indexOf(marker);
        // 校验标记位置有效。
        assertTrue(index > -1);
        // 截取上下文 JSON。
        String contextJson = prompt.substring(index + marker.length());
        // 清理上下文 JSON 前后空白。
        String trimmedContextJson = contextJson.trim();
        // 解析上下文 JSON 为映射。
        Map<String, Object> context = objectMapper.readValue(trimmedContextJson, new TypeReference<Map<String, Object>>() {
        });
        // 读取摘要对象。
        Object summaryObj = ((Map<?, ?>) context.get("outputSummary")).get("text");
        // 校验摘要对象不为空。
        assertNotNull(summaryObj);
        // 转换摘要文本。
        String summary = summaryObj.toString();
        // 校验摘要文本等于语义摘要。
        assertEquals("语义摘要", summary);
    }

    @Test
    void reflectFallsBackToHeuristicWhenLlmResponseEmpty() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        properties.setMaxRetries(2);
        properties.setConfidenceThreshold(0.8);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(null);

        StepSpec step = new StepSpec("TOOL", Map.of("critical", true));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("error", "failed"));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);
        assertTrue(result.retryRequested());
    }

    @Test
    void reflectThrowsWhenFallbackDisabledAndLlmResponseEmpty() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(null);

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("result", "ok"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.reflect(step, output,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                        "wf-1", new java.util.concurrent.atomic.AtomicLong(0)));
        assertEquals("reflection_fallback_disabled:llm_empty_response", exception.getMessage());
    }

    @Test
    void reflectThrowsWhenFallbackDisabledAndLlmInvocationError() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenThrow(new RuntimeException("timeout"));

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("result", "ok"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.reflect(step, output,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                        "wf-1", new java.util.concurrent.atomic.AtomicLong(0)));
        assertEquals("reflection_fallback_disabled:llm_invocation_error", exception.getMessage());
    }
}
