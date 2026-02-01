package com.example.agent.runtime;

import com.example.agent.observability.MetricsPublisher;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.streaming.EventStreamService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepRuntimeServiceTest {

    @Test
    void completeStepBuildsSummaryWhenOutputNull() {
        StepSummaryProperties properties = new StepSummaryProperties();
        properties.setEnable(true);
        properties.setMaxChars(200);
        properties.setMaxListItems(5);
        properties.setMaxFieldChars(50);
        StepOutputSummaryBuilder builder = new StepOutputSummaryBuilder(properties);

        StepRuntimeService service = new StepRuntimeService(
                Mockito.mock(ApplicationEventPublisher.class),
                Mockito.mock(EventStreamService.class),
                Mockito.mock(MetricsPublisher.class),
                Mockito.mock(TracingPublisher.class),
                Mockito.mock(StepRecordRepository.class),
                builder
        );

        StepRecord record = new StepRecord();
        record.setStepId("s-1");
        record.setWorkflowId("wf-1");
        record.setType("TOOL");
        record.setStatus(StepState.STARTED);
        record.setAttempt(1);
        record.setTenantId("t-1");
        record.setStartedAt(Instant.now());

        StepRecord completed = service.completeStep(record, null, new AtomicLong(0));

        Map<String, Object> output = completed.getOutput();
        assertNotNull(output);
        assertTrue(output.containsKey("outputSummary"));
        assertTrue(output.containsKey("stepSummary"));
        assertTrue(output.containsKey("outputDigest"));
        assertTrue(output.containsKey("truncated"));

        @SuppressWarnings("unchecked")
        Map<String, Object> outputSummary = (Map<String, Object>) output.get("outputSummary");
        assertNotNull(outputSummary);
        assertEquals(false, outputSummary.get("hasOutput"));

        @SuppressWarnings("unchecked")
        Map<String, Object> outputDigest = (Map<String, Object>) output.get("outputDigest");
        assertNotNull(outputDigest);
        assertEquals(0, ((Number) outputDigest.get("keyCount")).intValue());
        assertEquals(0, ((Number) outputDigest.get("charCount")).intValue());
        assertEquals(false, outputDigest.get("truncated"));
    }
}
