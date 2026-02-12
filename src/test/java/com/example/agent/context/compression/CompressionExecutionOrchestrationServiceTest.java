package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.application.CompressionExecutionOrchestrationService;
import com.example.agent.capabilities.context.compression.application.CompressionExecutionRouter;
import com.example.agent.capabilities.context.compression.application.CompressionModelMapper;
import com.example.agent.capabilities.context.compression.application.port.CompressionTelemetryPort;
import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionRequest;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeResult;
import com.example.agent.capabilities.context.compression.domain.policy.HistoryWindowPolicy;
import com.example.agent.capabilities.context.compression.experiment.application.CompressionDualTrackOrchestrator;
import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionComparisonRecord;
import com.example.agent.capabilities.context.compression.experiment.domain.model.DualTrackExecutionResult;
import com.example.agent.capabilities.context.compression.experiment.domain.model.RolloutDecision;
import com.example.agent.capabilities.context.compression.observability.CompressionObservationFactory;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.RuntimeMeta;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 压缩执行编排服务测试。
 */
class CompressionExecutionOrchestrationServiceTest {

    @Test
    void shouldFillDualTrackAndShapeMetadataWhenExecutionSuccess() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getTrigger().setCompressionTriggerRatio(0.91D);
        properties.getTrigger().setCompressionTargetRatio(0.73D);
        CompressionTelemetryPort telemetryPort = Mockito.mock(CompressionTelemetryPort.class);
        CompressionExecutionRouter executionRouter = Mockito.mock(CompressionExecutionRouter.class);
        CompressionModelMapper modelMapper = new CompressionModelMapper();
        HistoryWindowPolicy historyWindowPolicy = Mockito.mock(HistoryWindowPolicy.class);
        CompressionDualTrackOrchestrator dualTrackOrchestrator = Mockito.mock(CompressionDualTrackOrchestrator.class);

        CompressionExecutionOrchestrationService service = new CompressionExecutionOrchestrationService(
                properties,
                telemetryPort,
                executionRouter,
                dualTrackOrchestrator,
                modelMapper,
                historyWindowPolicy,
                new CompressionObservationFactory());

        ContextSnapshot snapshot = new ContextSnapshot();
        RuntimeMeta runtimeMeta = new RuntimeMeta();
        runtimeMeta.setTenantId("tenant-a");
        snapshot.setRuntimeMeta(runtimeMeta);
        ContextCompressionRequest request = new ContextCompressionRequest();
        request.setSnapshot(snapshot);
        request.setWorkflowId("wf-a");
        request.setSessionId("session-a");
        ContextCompressionResult result = new ContextCompressionResult();
        result.setSnapshot(snapshot);

        HistoryWindowShapeResult shapeResult = new HistoryWindowShapeResult();
        shapeResult.setSnapshot(snapshot);
        shapeResult.setWindowShaped(true);
        shapeResult.setShapeReason("WINDOW_SHAPED");
        shapeResult.setPrimersRetained(2);
        shapeResult.setRecentsRetained(3);
        shapeResult.setMiddleWindowSize(5);
        when(historyWindowPolicy.shape(any())).thenReturn(shapeResult);

        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSuccess(true);
        primary.setSource("llm");
        primary.setDurationMs(33L);

        DualTrackExecutionResult dualTrackExecutionResult = new DualTrackExecutionResult();
        dualTrackExecutionResult.setDualTrackEnabled(true);
        dualTrackExecutionResult.setPrimaryResult(primary);
        RolloutDecision rolloutDecision = new RolloutDecision();
        rolloutDecision.setRolloutVersion("v2");
        rolloutDecision.setReason("TENANT_HIT");
        dualTrackExecutionResult.setRolloutDecision(rolloutDecision);
        CompressionComparisonRecord comparisonRecord = new CompressionComparisonRecord();
        comparisonRecord.setRecordId("cmp-1");
        comparisonRecord.setRollbackReason("QUALITY_LOW");
        comparisonRecord.setWinnerSource("rule");
        dualTrackExecutionResult.setComparisonRecord(comparisonRecord);
        when(dualTrackOrchestrator.execute(any(), any(), any(), any(), any())).thenReturn(dualTrackExecutionResult);

        CompressionExecutionResult executionResult = service.execute(request, result);

        assertNotNull(executionResult);
        assertTrue(executionResult.isSuccess());
        assertEquals("llm", executionResult.getSource());
        assertEquals(0.91D, executionResult.getTriggerRatio());
        assertEquals(0.73D, executionResult.getTargetRatio());
        assertTrue(result.isWindowShaped());
        assertEquals("WINDOW_SHAPED", result.getShapeReason());
        assertTrue(result.isDualTrackEnabled());
        assertEquals("v2", result.getRolloutVersion());
        assertEquals("cmp-1", result.getComparisonRecordId());
        assertEquals("rule", result.getWinnerSource());
        verify(telemetryPort, Mockito.times(2)).recordObservation(any());
    }

    @Test
    void shouldReturnNullWhenExecutionResultMissing() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        CompressionTelemetryPort telemetryPort = Mockito.mock(CompressionTelemetryPort.class);
        CompressionExecutionRouter executionRouter = Mockito.mock(CompressionExecutionRouter.class);
        CompressionModelMapper modelMapper = new CompressionModelMapper();
        HistoryWindowPolicy historyWindowPolicy = Mockito.mock(HistoryWindowPolicy.class);
        CompressionDualTrackOrchestrator dualTrackOrchestrator = Mockito.mock(CompressionDualTrackOrchestrator.class);

        CompressionExecutionOrchestrationService service = new CompressionExecutionOrchestrationService(
                properties,
                telemetryPort,
                executionRouter,
                dualTrackOrchestrator,
                modelMapper,
                historyWindowPolicy,
                new CompressionObservationFactory());

        ContextSnapshot snapshot = new ContextSnapshot();
        ContextCompressionRequest request = new ContextCompressionRequest();
        request.setSnapshot(snapshot);
        request.setWorkflowId("wf-b");
        request.setSessionId("session-b");
        ContextCompressionResult result = new ContextCompressionResult();
        result.setSnapshot(snapshot);

        when(historyWindowPolicy.shape(any())).thenReturn(null);
        when(dualTrackOrchestrator.execute(any(), any(), any(), any(), any())).thenReturn(null);
        when(executionRouter.execute(any())).thenReturn(null);

        CompressionExecutionResult executionResult = service.execute(request, result);

        assertNull(executionResult);
    }
}
