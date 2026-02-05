package com.example.agent.capabilities.memory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 记忆策略，用于判断是否需要触发自动压缩。
 */
@Component
public class MemoryPolicy {

    private final MemoryPolicyProperties properties;
    private final TokenEstimator tokenEstimator;

    public MemoryPolicy(MemoryPolicyProperties properties, TokenEstimator tokenEstimator) {
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * 判断是否需要触发压缩。
     *
     * @param records 会话内记忆记录
     * @param now 当前时间
     * @return 是否触发压缩
     */
    public boolean shouldCompress(List<MemoryRecord> records, Instant now) {
        if (!properties.isEnabled() || records == null || records.isEmpty()) {
            return false;
        }
        List<MemoryRecord> active = filterNonCompressed(records);
        if (active.isEmpty()) {
            return false;
        }
        if (hasRecentCompression(records, now)) {
            return false;
        }
        if (properties.getSizeThreshold() > 0 && active.size() >= properties.getSizeThreshold()) {
            return true;
        }
        if (properties.getTokenThreshold() > 0 && estimateTokens(active) >= properties.getTokenThreshold()) {
            return true;
        }
        if (properties.getMaxAgeSeconds() > 0 && isExpired(active, now, properties.getMaxAgeSeconds())) {
            return true;
        }
        return false;
    }

    private boolean hasRecentCompression(List<MemoryRecord> records, Instant now) {
        if (properties.getMinCompressIntervalSeconds() <= 0) {
            return false;
        }
        return records.stream()
                .filter(record -> "compressed".equalsIgnoreCase(record.getLayer()))
                .filter(record -> record.getCreatedAt() != null)
                .anyMatch(record -> Duration.between(record.getCreatedAt(), now).getSeconds()
                        < properties.getMinCompressIntervalSeconds());
    }

    private boolean isExpired(List<MemoryRecord> records, Instant now, long maxAgeSeconds) {
        MemoryRecord oldest = records.stream()
                .filter(record -> record.getCreatedAt() != null)
                .min(Comparator.comparing(MemoryRecord::getCreatedAt))
                .orElse(null);
        if (oldest == null) {
            return false;
        }
        return Duration.between(oldest.getCreatedAt(), now).getSeconds() >= maxAgeSeconds;
    }

    private long estimateTokens(List<MemoryRecord> records) {
        long total = 0;
        for (MemoryRecord record : records) {
            String text = firstNonBlank(record.getContent(), record.getSummary());
            total += tokenEstimator.estimateTokens(text);
        }
        return total;
    }

    private List<MemoryRecord> filterNonCompressed(List<MemoryRecord> records) {
        List<MemoryRecord> filtered = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            String layer = record.getLayer();
            if (!StringUtils.hasText(layer) || !"compressed".equalsIgnoreCase(layer)) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return null;
    }
}
