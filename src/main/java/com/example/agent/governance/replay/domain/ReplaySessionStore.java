package com.example.agent.governance.replay.domain;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.common.state.GenericStateStore;
import com.example.agent.governance.common.state.StateStorePolicy;
import com.example.agent.governance.common.state.StoreMetricsRecorder;
import com.example.agent.governance.replay.ReplaySession;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 回放会话存储组件。
 */
@Component
public class ReplaySessionStore {

    private static final Logger log = LoggerFactory.getLogger(ReplaySessionStore.class);

    private final GenericStateStore<ReplaySession> stateStore;
    private final MetricsPublisher metricsPublisher;

    public ReplaySessionStore(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
        this.stateStore = new GenericStateStore<>(
                this::resolveLastAccessEpochMs,
                (replayId, session) -> {
                    // 回放会话过期清理无需额外回调。
                },
                new StoreMetricsRecorder<>() {
                    @Override
                    public void onCapacityRejected(ReplaySession value, int currentSize, int maxSize) {
                        metricsPublisher.incrementWithTags("governance.replay.session_rejected_total", "reason", "capacity");
                        log.warn("回放会话容量超限, tenantId={}, taskId={}, currentSize={}, maxSize={}",
                                value != null ? value.getTenantId() : null,
                                value != null ? value.getTaskId() : null,
                                currentSize,
                                maxSize);
                    }

                    @Override
                    public void onCleanup(int removed, int currentSize) {
                        for (int index = 0; index < removed; index++) {
                            metricsPublisher.incrementWithTags("governance.replay.session_cleanup_total", "cause", "expired");
                        }
                        metricsPublisher.recordSummary("governance.replay.session_cleanup_removed", removed);
                        metricsPublisher.recordSummary("governance.replay.session_size", currentSize);
                        log.info("回放会话清理完成, removed={}, currentSize={}", removed, currentSize);
                    }
                });
    }

    /**
     * 写入会话。
     *
     * @param replayId 回放标识
     * @param session 会话
     * @param properties 存储配置
     */
    public void put(String replayId, ReplaySession session, ReplaySessionStoreProperties properties) {
        StateStorePolicy policy = toPolicy(properties);
        boolean stored = stateStore.put(replayId, session, policy);
        if (!stored) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    "REPLAY_SESSION_FULL",
                    "回放系统繁忙，请稍后重试");
        }
        metricsPublisher.recordSummary("governance.replay.session_size", stateStore.size(policy));
    }

    /**
     * 获取会话数量。
     *
     * @param properties 存储配置
     * @return 会话数量
     */
    public int size(ReplaySessionStoreProperties properties) {
        return stateStore.size(toPolicy(properties));
    }

    private StateStorePolicy toPolicy(ReplaySessionStoreProperties properties) {
        return new StateStorePolicy(
                properties.getSessionTtlSeconds(),
                properties.getSessionMaxSize(),
                properties.getCleanupIntervalSeconds());
    }

    private long resolveLastAccessEpochMs(ReplaySession session) {
        if (session == null) {
            return 0;
        }
        Instant lastAccess = session.getLastAccessedAt() != null ? session.getLastAccessedAt() : session.getCompletedAt();
        if (lastAccess == null) {
            lastAccess = session.getStartedAt();
        }
        if (lastAccess == null) {
            return 0;
        }
        return lastAccess.toEpochMilli();
    }
}
