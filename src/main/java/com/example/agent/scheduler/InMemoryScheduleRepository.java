package com.example.agent.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存调度仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryScheduleRepository implements ScheduleRepository {

    private final ConcurrentHashMap<String, ScheduleSpec> schedules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    @Override
    public ScheduleSpec save(ScheduleSpec spec) {
        schedules.put(buildKey(spec.getTenantId(), spec.getScheduleId()), spec);
        if (spec.getIdempotencyKey() != null) {
            idempotencyIndex.put(buildIdempotencyKey(spec.getTenantId(), spec.getIdempotencyKey()),
                    spec.getScheduleId());
        }
        return spec;
    }

    @Override
    public ScheduleSpec findById(String tenantId, String scheduleId) {
        return schedules.get(buildKey(tenantId, scheduleId));
    }

    @Override
    public String findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return idempotencyIndex.get(buildIdempotencyKey(tenantId, idempotencyKey));
    }

    @Override
    public void delete(String tenantId, String scheduleId) {
        schedules.remove(buildKey(tenantId, scheduleId));
    }

    @Override
    public List<ScheduleSpec> list(String tenantId) {
        List<ScheduleSpec> result = new ArrayList<>();
        for (ScheduleSpec spec : schedules.values()) {
            if (tenantId.equals(spec.getTenantId())) {
                result.add(spec);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private String buildKey(String tenantId, String scheduleId) {
        return tenantId + ":" + scheduleId;
    }

    private String buildIdempotencyKey(String tenantId, String idempotencyKey) {
        return tenantId + ":" + idempotencyKey;
    }
}
