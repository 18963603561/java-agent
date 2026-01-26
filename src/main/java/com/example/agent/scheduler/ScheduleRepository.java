package com.example.agent.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 调度任务仓储，采用内存存储实现。
 */
@Repository
public class ScheduleRepository {

    private final ConcurrentHashMap<String, ScheduleSpec> schedules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    public ScheduleSpec save(ScheduleSpec spec) {
        schedules.put(buildKey(spec.getTenantId(), spec.getScheduleId()), spec);
        if (spec.getIdempotencyKey() != null) {
            idempotencyIndex.put(buildIdempotencyKey(spec.getTenantId(), spec.getIdempotencyKey()),
                    spec.getScheduleId());
        }
        return spec;
    }

    public ScheduleSpec findById(String tenantId, String scheduleId) {
        return schedules.get(buildKey(tenantId, scheduleId));
    }

    public String findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return idempotencyIndex.get(buildIdempotencyKey(tenantId, idempotencyKey));
    }

    public void delete(String tenantId, String scheduleId) {
        schedules.remove(buildKey(tenantId, scheduleId));
    }

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
