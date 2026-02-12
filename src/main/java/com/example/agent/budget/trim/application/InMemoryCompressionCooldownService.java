package com.example.agent.budget.trim.application;

import com.example.agent.budget.trim.config.ContextCompressionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于内存时间戳的压缩冷却服务，用于防止短时间重复压缩。
 */
@Component
public class InMemoryCompressionCooldownService implements CompressionCooldownService {

    private final ContextCompressionProperties properties;
    private final Map<String, Instant> lastCompressedAt = new ConcurrentHashMap<>();

    public InMemoryCompressionCooldownService(ContextCompressionProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isInCooldown(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        int intervalSeconds = properties != null && properties.getTrigger() != null
                ? properties.getTrigger().getMinIntervalSeconds()
                : 0;
        if (intervalSeconds <= 0) {
            return false;
        }
        Instant last = lastCompressedAt.get(key);
        if (last == null) {
            return false;
        }
        return Duration.between(last, Instant.now()).getSeconds() < intervalSeconds;
    }

    @Override
    public void markCompressed(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        lastCompressedAt.put(key, Instant.now());
    }
}
