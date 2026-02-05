package com.example.agent.capabilities.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 记忆过期处理服务，用于设置过期时间与过滤过期记录。
 */
@Service
public class MemoryExpirationService {

    private final MemoryExpireProperties properties;

    public MemoryExpirationService(MemoryExpireProperties properties) {
        this.properties = properties;
    }

    /**
     * 为记录补齐过期时间。
     *
     * @param record 记忆记录
     * @param now 当前时间
     */
    public void applyExpiration(MemoryRecord record, Instant now) {
        if (record == null || now == null) {
            return;
        }
        if (!properties.isEnabled()) {
            return;
        }
        if (record.getExpiresAt() != null) {
            return;
        }
        long ttlSeconds = properties.getTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        Instant base = record.getCreatedAt() != null ? record.getCreatedAt() : now;
        record.setExpiresAt(base.plusSeconds(ttlSeconds));
    }

    /**
     * 判断记录是否过期。
     *
     * @param record 记忆记录
     * @param now 当前时间
     * @return 是否过期
     */
    public boolean isExpired(MemoryRecord record, Instant now) {
        if (record == null || now == null) {
            return false;
        }
        if (!properties.isEnabled()) {
            return false;
        }
        Instant expiresAt = record.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * 过滤过期记录。
     *
     * @param records 记忆记录
     * @param now 当前时间
     * @return 过滤后的列表
     */
    public List<MemoryRecord> filterExpired(List<MemoryRecord> records, Instant now) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        if (!properties.isEnabled()) {
            return new ArrayList<>(records);
        }
        List<MemoryRecord> filtered = new ArrayList<>(records.size());
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            if (!isExpired(record, now)) {
                filtered.add(record);
            }
        }
        return filtered;
    }
}
