package com.example.agent.capabilities.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 内存记忆仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryMemoryRepository implements MemoryRepository {

    private final Map<String, MemoryRecord> records = new ConcurrentHashMap<>();
    private final Map<String, List<MemoryRecord>> sessionIndex = new ConcurrentHashMap<>();

    @Override
    public MemoryRecord save(MemoryRecord record) {
        if (record == null || !StringUtils.hasText(record.getMemoryId())) {
            return null;
        }
        records.put(record.getMemoryId(), record);
        String key = buildSessionKey(record.getTenantId(), record.getSessionId());
        sessionIndex.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(record);
        return record;
    }

    @Override
    public List<MemoryRecord> findBySession(String tenantId, String sessionId) {
        String key = buildSessionKey(tenantId, sessionId);
        List<MemoryRecord> list = sessionIndex.get(key);
        if (list == null) {
            return List.of();
        }
        return filterNotExpired(new ArrayList<>(list), Instant.now());
    }

    @Override
    public List<MemoryRecord> search(String tenantId, String sessionId, String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<MemoryRecord> records = findBySession(tenantId, sessionId);
        List<MemoryRecord> result = new ArrayList<>();
        String lower = query.toLowerCase();
        for (MemoryRecord record : records) {
            if (result.size() >= limit) {
                break;
            }
            if (matches(record, lower)) {
                result.add(record);
            }
        }
        return result;
    }

    @Override
    public int deleteExpired(String tenantId, Instant now) {
        if (now == null) {
            return 0;
        }
        int removed = 0;
        Iterator<Map.Entry<String, MemoryRecord>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MemoryRecord> entry = iterator.next();
            MemoryRecord record = entry.getValue();
            if (record == null) {
                iterator.remove();
                continue;
            }
            if (!matchesTenant(tenantId, record)) {
                continue;
            }
            if (isExpired(record, now)) {
                iterator.remove();
                removed++;
                String key = buildSessionKey(record.getTenantId(), record.getSessionId());
                List<MemoryRecord> list = sessionIndex.get(key);
                if (list != null) {
                    synchronized (list) {
                        list.removeIf(item -> item != null
                                && record.getMemoryId() != null
                                && record.getMemoryId().equals(item.getMemoryId()));
                    }
                }
            }
        }
        return removed;
    }

    private boolean matches(MemoryRecord record, String lowerQuery) {
        if (record.getContent() != null && record.getContent().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        return record.getSummary() != null && record.getSummary().toLowerCase().contains(lowerQuery);
    }

    private boolean matchesTenant(String tenantId, MemoryRecord record) {
        if (tenantId == null) {
            return true;
        }
        return tenantId.equals(record.getTenantId());
    }

    private boolean isExpired(MemoryRecord record, Instant now) {
        if (record == null || now == null) {
            return false;
        }
        Instant expiresAt = record.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    private List<MemoryRecord> filterNotExpired(List<MemoryRecord> records, Instant now) {
        if (records == null || records.isEmpty() || now == null) {
            return records == null ? List.of() : records;
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

    private String buildSessionKey(String tenantId, String sessionId) {
        return tenantId + ":" + (sessionId == null ? "-" : sessionId);
    }
}
