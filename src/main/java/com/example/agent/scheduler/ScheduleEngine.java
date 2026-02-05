package com.example.agent.scheduler;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 调度引擎，负责根据 Cron 触发执行记录。
 */
@Service
public class ScheduleEngine {

    private static final Logger log = LoggerFactory.getLogger(ScheduleEngine.class);

    private final ScheduleExecutionRepository executionRepository;
    private final MetricsPublisher metricsPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final ThreadPoolTaskScheduler scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public ScheduleEngine(ScheduleExecutionRepository executionRepository,
                          MetricsPublisher metricsPublisher,
                          ApplicationEventPublisher eventPublisher,
                          EventStreamService eventStreamService) {
        this.executionRepository = executionRepository;
        this.metricsPublisher = metricsPublisher;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(2);
        this.scheduler.setThreadNamePrefix("schedule-engine-");
        this.scheduler.initialize();
    }

    /**
     * 注册调度。
     *
     * @param spec 调度定义
     */
    public void register(ScheduleSpec spec) {
        if (spec == null || !StringUtils.hasText(spec.getScheduleId())) {
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(spec.getStatus())) {
            unregister(spec.getScheduleId());
            return;
        }
        if (!StringUtils.hasText(spec.getCron())) {
            log.warn("调度 Cron 缺失, scheduleId={}", spec.getScheduleId());
            return;
        }
        unregister(spec.getScheduleId());
        ZoneId zoneId = resolveZone(spec.getTimezone());
        CronTrigger trigger = new CronTrigger(spec.getCron(), zoneId);
        ScheduledFuture<?> future = scheduler.schedule(() -> triggerExecution(spec), trigger);
        futures.put(spec.getScheduleId(), future);
        log.info("调度注册, scheduleId={}, cron={}, timezone={}", spec.getScheduleId(), spec.getCron(), zoneId);
    }

    /**
     * 取消调度。
     *
     * @param scheduleId 调度标识
     */
    public void unregister(String scheduleId) {
        if (!StringUtils.hasText(scheduleId)) {
            return;
        }
        ScheduledFuture<?> future = futures.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
            log.info("调度取消, scheduleId={}", scheduleId);
        }
    }

    void triggerExecution(ScheduleSpec spec) {
        if (spec == null) {
            return;
        }
        ScheduleExecutionRecord record = new ScheduleExecutionRecord();
        record.setExecutionId(UUID.randomUUID().toString());
        record.setScheduleId(spec.getScheduleId());
        record.setStatus("TRIGGERED");
        record.setStartedAt(Instant.now());
        record.setTenantId(spec.getTenantId());
        executionRepository.save(record);
        metricsPublisher.increment("schedule.run.count");

        TenantContext tenantContext = new TenantContext(spec.getTenantId(), null, java.util.List.of(), null, null);
        publishScheduleEvent(tenantContext, spec.getScheduleId());
    }

    private void publishScheduleEvent(TenantContext tenantContext, String scheduleId) {
        if (tenantContext == null || scheduleId == null) {
            return;
        }
        String workflowId = "schedule-" + scheduleId;
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(EventType.SCHEDULE_TRIGGERED);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(Map.of("scheduleId", scheduleId));
        eventPublisher.publishEvent(event);
    }

    private ZoneId resolveZone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    @PreDestroy
    public void shutdown() {
        futures.values().stream().filter(Objects::nonNull).forEach(future -> future.cancel(false));
        scheduler.shutdown();
    }
}
