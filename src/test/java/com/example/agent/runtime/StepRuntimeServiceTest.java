package com.example.agent.runtime;

import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.structured.extractor.GenericStructuredExtractor;
import com.example.agent.runtime.structured.StructuredExtractorRegistry;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.repository.StepRecordRepository;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import com.example.agent.runtime.summary.StepSummaryProperties;
import com.example.agent.runtime.summary.SummaryDigestService;
import com.example.agent.runtime.summary.SummaryInputSanitizer;
import com.example.agent.runtime.summary.SummarySnapshotService;
import com.example.agent.runtime.summary.StepSummaryTextService;

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
        StepOutputSummaryBuilder builder = new StepOutputSummaryBuilder(
                properties,
                new SummarySnapshotService(),
                new SummaryInputSanitizer(),
                new StepSummaryTextService(),
                new SummaryDigestService()
        );
        RawOutputEnvelopeBuilder rawBuilder = new RawOutputEnvelopeBuilder(properties);

        StepRuntimeService service = new StepRuntimeService(
                Mockito.mock(ApplicationEventPublisher.class),
                Mockito.mock(EventStreamService.class),
                Mockito.mock(MetricsPublisher.class),
                Mockito.mock(TracingPublisher.class),
                Mockito.mock(StepRecordRepository.class),
                builder,
                rawBuilder,
                new StructuredExtractorRegistry(java.util.List.of(new GenericStructuredExtractor()))
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

        StepResult output = completed.getOutput();
        assertNotNull(output);
        assertNotNull(output.getMeta());
        assertNotNull(output.getSummary());

        assertEquals("s-1", output.getMeta().getStepId());

        assertNotNull(output.getSummary().getStepSummary());
    }
}
