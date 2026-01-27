package com.example.agent.memory;

import java.util.ArrayList;
import java.util.Collections;
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
        return new ArrayList<>(list);
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

    private boolean matches(MemoryRecord record, String lowerQuery) {
        if (record.getContent() != null && record.getContent().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        return record.getSummary() != null && record.getSummary().toLowerCase().contains(lowerQuery);
    }

    private String buildSessionKey(String tenantId, String sessionId) {
        return tenantId + ":" + (sessionId == null ? "-" : sessionId);
    }
}
