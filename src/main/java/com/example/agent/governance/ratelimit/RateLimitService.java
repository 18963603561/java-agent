package com.example.agent.governance.ratelimit;

import com.example.agent.governance.common.state.StateStorePolicy;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.governance.ratelimit.domain.RateLimitStore;
import com.example.agent.governance.ratelimit.domain.RateLimitWindowEntry;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 简易限流服务，按分钟窗口限制调用频次。
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final MetricsPublisher metricsPublisher;
    private final RateLimitStore rateLimitStore;
    private final GovernanceTelemetry governanceTelemetry;

    @Value("${agent.rate-limit.max-per-minute:100}")
    private int maxPerMinute;

    @Value("${agent.rate-limit.max-keys:20000}")
    private int maxKeys;

    @Value("${agent.rate-limit.entry-ttl-seconds:3600}")
    private int entryTtlSeconds;

    @Value("${agent.rate-limit.cleanup-interval-seconds:30}")
    private int cleanupIntervalSeconds;

    public RateLimitService(MetricsPublisher metricsPublisher, RateLimitStore rateLimitStore) {
        this(metricsPublisher, rateLimitStore, new GovernanceTelemetry(metricsPublisher));
    }

    @Autowired
    public RateLimitService(MetricsPublisher metricsPublisher,
                            RateLimitStore rateLimitStore,
                            GovernanceTelemetry governanceTelemetry) {
        this.metricsPublisher = metricsPublisher;
        this.rateLimitStore = rateLimitStore;
        this.governanceTelemetry = governanceTelemetry;
    }

    public boolean allow(String key) {
        StateStorePolicy policy = resolveStorePolicy();
        rateLimitStore.cleanup(policy);
        if (key == null || key.isBlank()) {
            log.warn("限流键缺失，默认拒绝请求");
            metricsPublisher.incrementWithTags("governance.ratelimit.rejected_total", "reason", "blank_key");
            governanceTelemetry.increment("ratelimit.allow.total",
                    "domain", "ratelimit",
                    "action", "allow",
                    "result", "rejected_blank_key");
            return false;
        }
        long currentWindow = Instant.now().getEpochSecond() / 60;
        RateLimitWindowEntry counter = rateLimitStore.get(key, policy);
        if (counter == null) {
            counter = rateLimitStore.getOrCreate(key, currentWindow, policy);
            if (counter == null) {
                log.warn("限流缓存容量超限, key={}, currentSize={}, maxKeys={}", key, rateLimitStore.size(policy), maxKeys);
                governanceTelemetry.increment("ratelimit.allow.total",
                        "domain", "ratelimit",
                        "action", "allow",
                        "result", "rejected_capacity");
                return false;
            }
        }
        synchronized (counter) {
            if (counter.getMinuteWindow() != currentWindow) {
                counter.resetWindow(currentWindow);
            }
            counter.touch();
            int current = counter.getCount().incrementAndGet();
            boolean allowed = current <= maxPerMinute;
            if (!allowed) {
                metricsPublisher.incrementWithTags("governance.ratelimit.rejected_total", "reason", "threshold");
                log.warn("触发限流, key={}, currentCount={}, maxPerMinute={}", key, current, maxPerMinute);
                governanceTelemetry.increment("ratelimit.allow.total",
                        "domain", "ratelimit",
                        "action", "allow",
                        "result", "rejected_threshold");
                return false;
            }
            governanceTelemetry.increment("ratelimit.allow.total",
                    "domain", "ratelimit",
                    "action", "allow",
                    "result", "allowed");
            return true;
        }
    }

    /**
     * 返回当前缓存键数量，仅用于测试和诊断。
     *
     * @return 缓存键数量
     */
    public int cachedKeyCount() {
        StateStorePolicy policy = resolveStorePolicy();
        rateLimitStore.cleanup(policy);
        return rateLimitStore.size(policy);
    }

    private StateStorePolicy resolveStorePolicy() {
        return new StateStorePolicy(entryTtlSeconds, maxKeys, cleanupIntervalSeconds);
    }
}
