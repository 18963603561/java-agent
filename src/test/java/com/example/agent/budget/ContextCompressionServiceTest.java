package com.example.agent.budget;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.capabilities.memory.model.ConversationSummary;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.capabilities.memory.model.WorkingMemorySummary;
import com.example.agent.capabilities.context.compression.application.CompressionExecutionRouter;
import com.example.agent.capabilities.context.compression.application.CompressionModelMapper;
import com.example.agent.capabilities.context.compression.application.DefaultCompressionModeResolver;
import com.example.agent.capabilities.context.compression.application.LlmCompressionOrchestrator;
import com.example.agent.capabilities.context.compression.domain.policy.DefaultCompressionFallbackPolicy;
import com.example.agent.capabilities.context.compression.domain.policy.DefaultCompressionTriggerPolicy;
import com.example.agent.capabilities.context.compression.infrastructure.llm.LlmCompressionExecutorAdapter;
import com.example.agent.capabilities.context.compression.infrastructure.rule.RuleCompressionExecutorAdapter;
import com.example.agent.capabilities.context.compression.infrastructure.telemetry.MetricsCompressionTelemetryAdapter;
import com.example.agent.capabilities.context.compression.parser.CompressionResponseParser;
import com.example.agent.capabilities.context.compression.parser.DefaultCompressionValidationPolicy;
import com.example.agent.capabilities.context.compression.prompt.DefaultCompressionPromptBuilder;
import com.example.agent.capabilities.context.compression.summary.CompressionSummaryGuard;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.security.redaction.RedactionProperties;
import com.example.agent.security.redaction.RedactionService;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextBudgetAllocationState;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.budget.trim.application.ContextCompressionService;
import com.example.agent.budget.trim.config.ContextCompressionProperties;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.budget.trim.model.ContextCompressionResult;
import com.example.agent.budget.trim.application.DefaultCompressionSummaryApplier;
import com.example.agent.budget.trim.application.InMemoryCompressionCooldownService;
import com.example.agent.budget.core.ContextSection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextCompressionServiceTest {

    @Test
    void compressTriggeredWhenOverBudget() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.setMinIntervalSeconds(0);

        ContextCompressionService controller = createController(memoryStore, properties);

        ContextBudgetAllocation allocation = buildAllocation(50, 20);
        ContextTrimReport trimReport = buildTrimReport(300, 200, 150);
        ContextSnapshot snapshot = buildSnapshot("a".repeat(400), "b".repeat(200));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        MemoryRecord compressed = buildCompressedRecord();
        when(memoryStore.compress(any(), eq(tenantContext))).thenReturn(compressed);

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                trimReport,
                tenantContext,
                "wf-1",
                "s1");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        assertTrue(result.isTriggered());
        assertNotNull(result.getAfterCompressTokens());
        assertTrue(result.getAfterCompressTokens() < result.getAfterTrimTokens());
        assertEquals("v1", result.getRolloutVersion());
        assertEquals("v1", result.getQualityGateVersion());
        assertEquals("v1", result.getRollbackPolicyVersion());
        assertEquals("rule", result.getWinnerSource());
        assertNotNull(result.getQualityScore());
        verify(memoryStore, times(1)).compress(any(), eq(tenantContext));
    }

    @Test
    void compressSkippedDuringCooldown() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.setMinIntervalSeconds(60);

        ContextCompressionService controller = createController(memoryStore, properties);

        ContextBudgetAllocation allocation = buildAllocation(50, 20);
        ContextTrimReport trimReport = buildTrimReport(300, 200, 150);
        ContextSnapshot snapshot = buildSnapshot("a".repeat(400), "b".repeat(200));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        MemoryRecord compressed = buildCompressedRecord();
        when(memoryStore.compress(any(), eq(tenantContext))).thenReturn(compressed);

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                trimReport,
                tenantContext,
                "wf-2",
                "s2");

        ContextCompressionResult first = controller.compressIfNeeded(request);
        ContextCompressionResult second = controller.compressIfNeeded(request);

        assertTrue(first.isTriggered());
        assertTrue(second.isSkippedCooldown());
        verify(memoryStore, times(1)).compress(any(), eq(tenantContext));
    }

    @Test
    void compressDisabledNeverTriggers() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.setEnabled(false);

        ContextCompressionService controller = createController(memoryStore, properties);

        ContextBudgetAllocation allocation = buildAllocation(50, 20);
        ContextTrimReport trimReport = buildTrimReport(300, 200, 150);
        ContextSnapshot snapshot = buildSnapshot("a".repeat(400), "b".repeat(200));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                trimReport,
                tenantContext,
                "wf-3",
                "s3");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        assertTrue(!result.isTriggered());
        verify(memoryStore, times(0)).compress(any(), eq(tenantContext));
    }

    @Test
    void compressShouldSkipWhenEmergencyDisableEnabled() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getEmergency().setDisableCompression(true);

        ContextCompressionService controller = createController(memoryStore, properties);

        ContextBudgetAllocation allocation = buildAllocation(50, 20);
        ContextTrimReport trimReport = buildTrimReport(300, 200, 150);
        ContextSnapshot snapshot = buildSnapshot("a".repeat(400), "b".repeat(200));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                trimReport,
                tenantContext,
                "wf-emergency",
                "s-emergency");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        assertFalse(result.isTriggered());
        verify(memoryStore, times(0)).compress(any(), eq(tenantContext));
    }

    @Test
    void estimatedTokensShouldMatchUnifiedEstimatorWhenTrimReportMissing() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();

        TokenEstimator tokenEstimator = new TokenEstimator();
        ContextCompressionService controller = createController(memoryStore, properties, tokenEstimator);

        ContextSnapshot snapshot = buildSnapshot("a".repeat(120), "b".repeat(60));
        ContextBudgetAllocation allocation = buildAllocation(99999, 99999);
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                null,
                tenantContext,
                "wf-4",
                "s4");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        ContextTokenEstimator estimator = new ContextTokenEstimator(tokenEstimator);
        Map<ContextSection, Integer> tokens = estimator.estimateSectionTokens(snapshot);
        int expected = estimator.sumTokens(tokens);

        assertNotNull(result.getAfterTrimTokens());
        assertEquals(expected, result.getAfterTrimTokens());
    }

    @Test
    void compressShouldNotTriggerWhenAllTokenViewsWithinBudget() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();

        TokenEstimator tokenEstimator = new TokenEstimator();
        ContextCompressionService controller = createController(memoryStore, properties, tokenEstimator);

        ContextSnapshot snapshot = buildSnapshot("", "");
        ContextBudgetAllocation allocation = buildAllocation(99999, 99999);
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                null,
                tenantContext,
                "wf-5",
                "s5");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        assertFalse(result.isTriggered());
        verify(memoryStore, times(0)).compress(any(), eq(tenantContext));
    }

    @Test
    void compressShouldApplySummaryWhenCompressedRecordExists() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.setMinIntervalSeconds(0);

        ContextCompressionService controller = createController(memoryStore, properties);

        ContextBudgetAllocation allocation = buildAllocation(40, 10);
        ContextTrimReport trimReport = buildTrimReport(200, 160, 120);
        ContextSnapshot snapshot = buildSnapshot("a".repeat(200), "b".repeat(120));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        MemoryRecord compressed = buildCompressedRecord();
        compressed.setMemoryId("compressed-100");
        compressed.setExpiresAt(Instant.now().plusSeconds(600));
        when(memoryStore.compress(any(), eq(tenantContext))).thenReturn(compressed);

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                trimReport,
                tenantContext,
                "wf-6",
                "s6");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        assertTrue(result.isTriggered());
        assertEquals("v1", result.getSummaryVersion());
        assertNotNull(snapshot.getLongTermMemory());
        assertNotNull(snapshot.getLongTermMemory().getMemoryRefs());
        assertEquals(1, snapshot.getLongTermMemory().getMemoryRefs().size());
        assertEquals("compressed-100", snapshot.getLongTermMemory().getMemoryRefs().get(0).getMemoryId());
    }

    @Test
    void compressShouldSkipWhenAllocationDisabled() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();

        ContextCompressionService controller = createController(memoryStore, properties);

        ContextBudgetAllocation allocation = ContextBudgetAllocation.disabled(
                ContextBudgetAllocationState.DISABLED_BY_CONFIG,
                "config_disabled");
        ContextSnapshot snapshot = buildSnapshot("a".repeat(200), "b".repeat(120));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                null,
                tenantContext,
                "wf-disabled",
                "s-disabled");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        assertFalse(result.isTriggered());
        verify(memoryStore, times(0)).compress(any(), eq(tenantContext));
    }

    /**
     * 构造压缩服务测试实例。
     */
    private ContextCompressionService createController(MemoryStore memoryStore, ContextCompressionProperties properties) {
        // 默认估算器：用于大多数压缩场景测试。
        return createController(memoryStore, properties, new TokenEstimator());
    }

    /**
     * 构造压缩服务测试实例（可注入自定义估算器）。
     */
    private ContextCompressionService createController(MemoryStore memoryStore,
                                                       ContextCompressionProperties properties,
                                                       TokenEstimator tokenEstimator) {
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        // 构造规则执行器：复用现有 MemoryStore 压缩能力。
        RuleCompressionExecutorAdapter ruleExecutor = new RuleCompressionExecutorAdapter(memoryStore, metricsPublisher);
        // 构造 LLM 编排服务：注入 mock 模型调用，确保测试聚焦路由与降级行为。
        LlmCompressionOrchestrator orchestrator = new LlmCompressionOrchestrator(
                Mockito.mock(ModelInvocationService.class),
                new DefaultCompressionPromptBuilder(),
                new CompressionResponseParser(new ObjectMapper(), new DefaultCompressionValidationPolicy()),
                new CompressionSummaryGuard(new RedactionService(new RedactionProperties(), metricsPublisher)),
                properties);
        // 构造 LLM 执行器：通过编排服务执行真实解析与治理链路。
        LlmCompressionExecutorAdapter llmExecutor = new LlmCompressionExecutorAdapter(orchestrator);
        // 构造模式解析器：读取压缩配置决定执行路径。
        DefaultCompressionModeResolver modeResolver = new DefaultCompressionModeResolver(properties);
        // 构造路由器：组合 rule/llm 执行器并统一输出结果。
        CompressionExecutionRouter router = new CompressionExecutionRouter(
                modeResolver,
                List.of(ruleExecutor, llmExecutor),
                new DefaultCompressionFallbackPolicy(properties));
        // 构造压缩服务：注入触发策略、冷却策略、路由与模型映射。
        return new ContextCompressionService(
                tokenEstimator,
                new MetricsCompressionTelemetryAdapter(metricsPublisher),
                properties,
                new DefaultCompressionTriggerPolicy(properties),
                new InMemoryCompressionCooldownService(properties),
                router,
                new CompressionModelMapper(),
                new DefaultCompressionSummaryApplier());
    }

    private ContextBudgetAllocation buildAllocation(int totalTokens, int workingMemoryBudget) {
        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setTotalTokens(totalTokens);
        allocation.setAllocationState(ContextBudgetAllocationState.ENABLED);
        EnumMap<ContextSection, Integer> budgets = new EnumMap<>(ContextSection.class);
        budgets.put(ContextSection.WORKING_MEMORY, workingMemoryBudget);
        allocation.setSectionTokens(budgets);
        return allocation;
    }

    private ContextTrimReport buildTrimReport(int beforeTokens, int afterTokens, int workingMemoryTokens) {
        ContextTrimReport report = new ContextTrimReport();
        report.setTotalBeforeTokens(beforeTokens);
        report.setTotalAfterTokens(afterTokens);
        EnumMap<ContextSection, Integer> sections = new EnumMap<>(ContextSection.class);
        sections.put(ContextSection.WORKING_MEMORY, workingMemoryTokens);
        report.setSectionTokensAfter(sections);
        return report;
    }

    private ContextSnapshot buildSnapshot(String summary, String snippet) {
        WorkingMemory memory = new WorkingMemory();
        memory.setSummary(summary);
        LongTermMemory longTermMemory = new LongTermMemory();
        MemoryRef ref = new MemoryRef();
        ref.setMemoryId("m1");
        ref.setSnippet(snippet);
        longTermMemory.setMemoryRefs(List.of(ref));
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(memory);
        snapshot.setLongTermMemory(longTermMemory);
        return snapshot;
    }

    private MemoryRecord buildCompressedRecord() {
        MemoryRecord record = new MemoryRecord();
        record.setMemoryId("cm1");
        record.setSummary("压缩摘要");

        ConversationSummary conversationSummary = new ConversationSummary();
        conversationSummary.setVersion("v1");
        conversationSummary.setSummary("压缩摘要");
        conversationSummary.setBullets(List.of("要点1"));
        record.setConversationSummary(conversationSummary);

        WorkingMemorySummary workingMemorySummary = new WorkingMemorySummary();
        workingMemorySummary.setVersion("v1");
        workingMemorySummary.setSummary("压缩工作记忆摘要");
        workingMemorySummary.setItems(List.of("事项1"));
        record.setWorkingMemorySummary(workingMemorySummary);
        return record;
    }
}


