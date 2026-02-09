package com.example.agent.governance.replay;

import com.example.agent.governance.replay.domain.ReplayCommand;
import com.example.agent.governance.replay.domain.ReplayEventFactory;
import com.example.agent.governance.replay.domain.ReplayItem;
import com.example.agent.governance.replay.domain.ReplayItemAssembler;
import com.example.agent.governance.replay.domain.ReplayResult;
import com.example.agent.governance.replay.domain.ReplaySessionStore;
import com.example.agent.governance.replay.domain.ReplaySessionStoreProperties;
import com.example.agent.governance.replay.domain.ReplayTaskSnapshot;
import com.example.agent.governance.replay.domain.ReplayTaskResolver;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 回放服务，用于重放历史事件与步骤。
 */
@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private static final int DEFAULT_SESSION_TTL_SECONDS = 1800;
    private static final int DEFAULT_SESSION_MAX_SIZE = 2000;
    private static final int DEFAULT_CLEANUP_INTERVAL_SECONDS = 30;

    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final MetricsPublisher metricsPublisher;
    private final ReplayProperties replayProperties;
    private final ReplayTaskResolver replayTaskResolver;
    private final ReplayItemAssembler replayItemAssembler;
    private final ReplayEventFactory replayEventFactory;
    private final ReplaySessionStore replaySessionStore;
    private final GovernanceTelemetry governanceTelemetry;

    public ReplayService(ApplicationEventPublisher eventPublisher,
                         EventStreamService eventStreamService,
                         MetricsPublisher metricsPublisher,
                         ReplayProperties replayProperties,
                         ReplayTaskResolver replayTaskResolver,
                         ReplayItemAssembler replayItemAssembler,
                         ReplayEventFactory replayEventFactory,
                         ReplaySessionStore replaySessionStore) {
        this(eventPublisher,
                eventStreamService,
                metricsPublisher,
                replayProperties,
                replayTaskResolver,
                replayItemAssembler,
                replayEventFactory,
                replaySessionStore,
                new GovernanceTelemetry(metricsPublisher));
    }

    @Autowired
    public ReplayService(ApplicationEventPublisher eventPublisher,
                         EventStreamService eventStreamService,
                         MetricsPublisher metricsPublisher,
                         ReplayProperties replayProperties,
                         ReplayTaskResolver replayTaskResolver,
                         ReplayItemAssembler replayItemAssembler,
                         ReplayEventFactory replayEventFactory,
                         ReplaySessionStore replaySessionStore,
                         GovernanceTelemetry governanceTelemetry) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.metricsPublisher = metricsPublisher;
        this.replayProperties = replayProperties;
        this.replayTaskResolver = replayTaskResolver;
        this.replayItemAssembler = replayItemAssembler;
        this.replayEventFactory = replayEventFactory;
        this.replaySessionStore = replaySessionStore;
        this.governanceTelemetry = governanceTelemetry;
    }

    /**
     * 执行回放。
     *
     * @param command 回放命令
     * @param tenantContext 租户上下文
     * @return 回放响应
     */
    public ReplayResult replay(ReplayCommand command, TenantContext tenantContext) {
        log.info("治理链路开始, domain={}, action={}, result={}, tenantId={}, workflowId={}, taskId={}, requestId={}, traceId={}",
                "replay",
                "replay",
                "started",
                tenantContext != null ? tenantContext.getTenantId() : null,
                null,
                command != null ? command.getTaskId() : null,
                tenantContext != null ? tenantContext.getRequestId() : null,
                tenantContext != null ? tenantContext.getTraceId() : null);
        ReplayTaskSnapshot task = replayTaskResolver.resolveTask(command, tenantContext);

        String replayId = UUID.randomUUID().toString();
        ReplaySession session = new ReplaySession();
        session.setReplayId(replayId);
        session.setTaskId(command.getTaskId());
        session.setStatus("RUNNING");
        session.setTenantId(tenantContext.getTenantId());
        session.setStartedAt(Instant.now());
        session.setLastAccessedAt(Instant.now());
        replaySessionStore.put(replayId, session, resolveSessionStoreProperties());

        metricsPublisher.increment("replay.count");
        governanceTelemetry.increment("replay.run.total",
                "domain", "replay",
                "action", "run",
                "result", "started");

        String replayStreamId = "replay-" + replayId;
        AtomicLong seqCounter = eventStreamService.sequenceCounter(tenantContext.getTenantId(), replayStreamId);
        publishReplayEvent(tenantContext.getTenantId(), replayStreamId, seqCounter, replayId, EventType.REPLAY_STARTED);

        java.util.List<ReplayItem> items = replayItemAssembler.assemble(tenantContext, task.getWorkflowId());
        for (ReplayItem item : items) {
            StreamEvent event = replayEventFactory.buildReplayItemEvent(replayStreamId,
                    tenantContext.getTenantId(),
                    seqCounter,
                    item.getTimestamp(),
                    item.getEventRecord(),
                    item.getStepRecord());
            eventPublisher.publishEvent(event);
        }

        session.setStatus("COMPLETED");
        session.setCompletedAt(Instant.now());
        session.setLastAccessedAt(Instant.now());
        publishReplayEvent(tenantContext.getTenantId(), replayStreamId, seqCounter, replayId, EventType.REPLAY_COMPLETED);

        governanceTelemetry.increment("replay.run.total",
                "domain", "replay",
                "action", "run",
                "result", "completed");
        log.info("治理链路结束, domain={}, action={}, result={}, tenantId={}, workflowId={}, taskId={}, requestId={}, traceId={}, replayId={}",
                "replay",
                "replay",
                "completed",
                tenantContext != null ? tenantContext.getTenantId() : null,
                task != null ? task.getWorkflowId() : null,
                command != null ? command.getTaskId() : null,
                tenantContext != null ? tenantContext.getRequestId() : null,
                tenantContext != null ? tenantContext.getTraceId() : null,
                replayId);

        return new ReplayResult(replayId, "COMPLETED", session.getStartedAt(), session.getCompletedAt());
    }

    /**
     * 获取回放会话数量，仅用于测试与诊断。
     *
     * @return 会话数量
     */
    public int sessionCount() {
        return replaySessionStore.size(resolveSessionStoreProperties());
    }

    private void publishReplayEvent(String tenantId,
                                    String replayStreamId,
                                    AtomicLong seqCounter,
                                    String replayId,
                                    EventType type) {
        StreamEvent event = replayEventFactory.buildLifecycleEvent(replayStreamId, tenantId, seqCounter, replayId, type);
        eventPublisher.publishEvent(event);
    }

    private int getSessionTtlSeconds() {
        if (replayProperties == null) {
            return DEFAULT_SESSION_TTL_SECONDS;
        }
        return Math.max(1, replayProperties.getSessionTtlSeconds());
    }

    private int getSessionMaxSize() {
        if (replayProperties == null) {
            return DEFAULT_SESSION_MAX_SIZE;
        }
        return Math.max(1, replayProperties.getSessionMaxSize());
    }

    private int getCleanupIntervalSeconds() {
        if (replayProperties == null) {
            return DEFAULT_CLEANUP_INTERVAL_SECONDS;
        }
        return Math.max(1, replayProperties.getCleanupIntervalSeconds());
    }

    private ReplaySessionStoreProperties resolveSessionStoreProperties() {
        return new ReplaySessionStoreProperties(getSessionTtlSeconds(),
                getSessionMaxSize(),
                getCleanupIntervalSeconds());
    }
}
