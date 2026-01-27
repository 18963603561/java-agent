package com.example.agent.scheduler;

import com.example.agent.observability.MetricsPublisher;
import com.example.agent.streaming.EventStreamService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ScheduleEngineTest {

    @Test
    void triggerExecutionWritesRecord() {
        ScheduleExecutionRepository executionRepository = new InMemoryScheduleExecutionRepository();
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        when(eventStreamService.nextSequence(any(), any())).thenReturn(1L);

        ScheduleEngine engine = new ScheduleEngine(executionRepository, metricsPublisher,
                eventPublisher, eventStreamService);

        ScheduleSpec spec = new ScheduleSpec();
        spec.setScheduleId("schedule-1");
        spec.setTenantId("tenant-a");
        spec.setStatus("ACTIVE");
        spec.setCron("0/5 * * * * ?");

        engine.triggerExecution(spec);

        List<ScheduleExecutionRecord> records = executionRepository.findBySchedule("tenant-a", "schedule-1");
        assertEquals(1, records.size());
        assertEquals("TRIGGERED", records.get(0).getStatus());

        engine.shutdown();
    }
}
