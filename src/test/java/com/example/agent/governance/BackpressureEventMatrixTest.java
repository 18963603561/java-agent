package com.example.agent.governance;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.budget.token.application.BudgetThresholdEvaluator;
import com.example.agent.budget.token.application.TokenBudgetManager;
import com.example.agent.budget.token.application.TokenUsageRecordFactory;
import com.example.agent.budget.token.application.TokenUsageRecorder;
import com.example.agent.budget.token.application.TokenUsageSummaryService;
import com.example.agent.budget.token.event.BudgetEventPublisher;
import com.example.agent.budget.token.model.TokenUsageInput;
import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.budget.token.model.TokenUsageSummary;
import com.example.agent.capabilities.llm.provider.ModelFallbackDecision;
import com.example.agent.capabilities.llm.provider.ModelFallbackPolicy;
import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.capabilities.tools.execution.ToolExecutor;
import com.example.agent.common.error.GovernanceRejectionException;
import com.example.agent.governance.approval.ApprovalProperties;
import com.example.agent.governance.approval.ApprovalService;
import com.example.agent.governance.approval.domain.ApprovalArgsDigestBuilder;
import com.example.agent.governance.approval.domain.ApprovalDecisionAwaiter;
import com.example.agent.governance.approval.domain.PendingApprovalStore;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 背压事件矩阵测试。
 */
class BackpressureEventMatrixTest {

    @Test
    void rateLimitShouldPublishBackpressureAndToolError() {
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        Mockito.when(toolExecutor.buildArguments(Mockito.any())).thenReturn(Map.of());
        Map<String, Object> context = new HashMap<>();
        context.put("governanceType", "rate_limit");
        context.put("trigger", "threshold_reject");
        Mockito.when(toolExecutor.execute(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.any()))
                .thenThrow(new GovernanceRejectionException(HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMITED",
                        "请求过于频繁",
                        context));

        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        EnforcementGateway gateway = new EnforcementGateway(toolExecutor, publisher, buildApprovalServiceDisabled());

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");

        assertThrows(GovernanceRejectionException.class,
                () -> gateway.execute(request,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"),
                        "wf-rate",
                        "task-rate",
                        new AtomicLong(),
                        "demo_tool"));

        assertEventPublished(publisher, EventType.BACKPRESSURE_APPLIED);
        assertEventPublished(publisher, EventType.TOOL_ERROR);
    }

    @Test
    void circuitOpenShouldPublishCircuitAndBackpressureAndToolError() {
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        Mockito.when(toolExecutor.buildArguments(Mockito.any())).thenReturn(Map.of());
        Map<String, Object> context = new HashMap<>();
        context.put("governanceType", "circuit_breaker");
        context.put("trigger", "open_state");
        Mockito.when(toolExecutor.execute(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.any()))
                .thenThrow(new GovernanceRejectionException(HttpStatus.SERVICE_UNAVAILABLE,
                        "CIRCUIT_OPEN",
                        "熔断开启",
                        context));

        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        EnforcementGateway gateway = new EnforcementGateway(toolExecutor, publisher, buildApprovalServiceDisabled());

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");

        assertThrows(GovernanceRejectionException.class,
                () -> gateway.execute(request,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"),
                        "wf-circuit",
                        "task-circuit",
                        new AtomicLong(),
                        "demo_tool"));

        assertEventPublished(publisher, EventType.CIRCUIT_OPENED);
        assertEventPublished(publisher, EventType.BACKPRESSURE_APPLIED);
        assertEventPublished(publisher, EventType.TOOL_ERROR);
    }

    @Test
    void budgetPressureShouldPublishBackpressureAndThreshold() {
        TokenUsageRecordFactory recordFactory = Mockito.mock(TokenUsageRecordFactory.class);
        TokenUsageRecorder recorder = Mockito.mock(TokenUsageRecorder.class);
        TokenUsageSummaryService summaryService = Mockito.mock(TokenUsageSummaryService.class);
        BudgetThresholdEvaluator evaluator = Mockito.mock(BudgetThresholdEvaluator.class);
        ModelFallbackPolicy fallbackPolicy = Mockito.mock(ModelFallbackPolicy.class);
        BudgetEventPublisher eventPublisher = Mockito.mock(BudgetEventPublisher.class);
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());

        TokenUsageRecord record = new TokenUsageRecord();
        record.setTaskId("task-budget");
        record.setUsageId("usage-budget");
        record.setTotalTokens(1200);
        Mockito.when(recordFactory.create(Mockito.any(), Mockito.any())).thenReturn(record);
        Mockito.when(recorder.record(Mockito.any())).thenReturn(true);
        Mockito.when(summaryService.summarize(Mockito.eq("task-budget"), Mockito.any()))
                .thenReturn(new TokenUsageSummary("task-budget", 1200, 0D, Map.of(), Map.of()));
        Mockito.when(evaluator.isExceeded(Mockito.any())).thenReturn(true);
        Mockito.when(evaluator.getThresholdTokens()).thenReturn(1000);

        TokenBudgetManager manager = new TokenBudgetManager(
                recordFactory,
                recorder,
                summaryService,
                evaluator,
                fallbackPolicy,
                eventPublisher,
                metricsPublisher);
        ReflectionTestUtils.setField(manager, "enabled", true);

        TokenUsageInput input = new TokenUsageInput();
        input.setTaskId("task-budget");
        input.setUsageId("usage-budget");
        input.setModel("mock-model");
        input.setInputTokens(400);
        input.setOutputTokens(800);
        input.setTotalTokens(1200);

        manager.recordUsage(input, new TenantContext("t1", "u1", List.of(), "req", "trace"));

        Mockito.verify(eventPublisher, Mockito.times(1))
                .publishBackpressureEvent(Mockito.any(), Mockito.eq("task-budget"), Mockito.eq(1200), Mockito.eq(1000));
        Mockito.verify(eventPublisher, Mockito.times(1))
                .publishThresholdEvent(Mockito.any(), Mockito.eq("task-budget"), Mockito.eq(1200));
    }

    private void assertEventPublished(ApplicationEventPublisher publisher, EventType expectedType) {
        ArgumentCaptor<StreamEvent> eventCaptor = ArgumentCaptor.forClass(StreamEvent.class);
        Mockito.verify(publisher, Mockito.atLeast(1)).publishEvent(eventCaptor.capture());
        List<StreamEvent> events = eventCaptor.getAllValues();
        assertTrue(events.stream().anyMatch(event -> event.getType() == expectedType));
    }

    private ApprovalService buildApprovalServiceDisabled() {
        ApprovalProperties properties = new ApprovalProperties();
        properties.setEnabled(false);
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        return new ApprovalService(properties,
                new PendingApprovalStore(metricsPublisher),
                new ApprovalDecisionAwaiter(),
                new ApprovalArgsDigestBuilder(),
                new GovernanceTelemetry(metricsPublisher));
    }
}
