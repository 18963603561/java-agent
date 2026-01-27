package com.example.agent.scheduler;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.observability.MetricsPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 调度管理服务，处理调度任务的创建与状态变更。
 */
@Service
public class ScheduleManager {

    private static final Logger log = LoggerFactory.getLogger(ScheduleManager.class);

    private final ScheduleRepository scheduleRepository;
    private final ScheduleExecutionRepository executionRepository;
    private final ScheduleEngine scheduleEngine;
    private final MetricsPublisher metricsPublisher;

    public ScheduleManager(ScheduleRepository scheduleRepository,
                           ScheduleExecutionRepository executionRepository,
                           ScheduleEngine scheduleEngine,
                           MetricsPublisher metricsPublisher) {
        this.scheduleRepository = scheduleRepository;
        this.executionRepository = executionRepository;
        this.scheduleEngine = scheduleEngine;
        this.metricsPublisher = metricsPublisher;
    }

    public ScheduleResponse create(ScheduleSpec spec, TenantContext tenantContext) {
        ensureTenant(spec, tenantContext);
        String existingId = findIdempotent(spec, tenantContext);
        if (existingId != null) {
            ScheduleSpec existing = scheduleRepository.findById(tenantContext.getTenantId(), existingId);
            return new ScheduleResponse(existing.getScheduleId(), existing.getStatus());
        }
        if (!StringUtils.hasText(spec.getScheduleId())) {
            spec.setScheduleId(UUID.randomUUID().toString());
        }
        if (!StringUtils.hasText(spec.getStatus())) {
            spec.setStatus("ACTIVE");
        }
        scheduleRepository.save(spec);
        scheduleEngine.register(spec);
        log.info("调度创建, tenantId={}, scheduleId={}", tenantContext.getTenantId(), spec.getScheduleId());
        return new ScheduleResponse(spec.getScheduleId(), spec.getStatus());
    }

    public ScheduleResponse update(ScheduleSpec spec, TenantContext tenantContext) {
        ensureTenant(spec, tenantContext);
        ScheduleSpec existing = scheduleRepository.findById(tenantContext.getTenantId(), spec.getScheduleId());
        if (existing == null) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "调度不存在");
        }
        scheduleRepository.save(spec);
        scheduleEngine.register(spec);
        log.info("调度更新, tenantId={}, scheduleId={}", tenantContext.getTenantId(), spec.getScheduleId());
        return new ScheduleResponse(spec.getScheduleId(), spec.getStatus());
    }

    public ScheduleResponse pause(String scheduleId, TenantContext tenantContext) {
        ScheduleSpec spec = getRequired(scheduleId, tenantContext);
        spec.setStatus("PAUSED");
        scheduleRepository.save(spec);
        scheduleEngine.unregister(scheduleId);
        log.info("调度暂停, tenantId={}, scheduleId={}", tenantContext.getTenantId(), scheduleId);
        return new ScheduleResponse(scheduleId, spec.getStatus());
    }

    /**
     * 取消调度并停止触发。
     *
     * @param scheduleId 调度标识
     * @param tenantContext 租户上下文
     * @return 取消结果
     */
    public ScheduleResponse cancel(String scheduleId, TenantContext tenantContext) {
        ScheduleSpec spec = getRequired(scheduleId, tenantContext);
        spec.setStatus("CANCELLED");
        scheduleRepository.save(spec);
        scheduleEngine.unregister(scheduleId);
        log.info("调度取消, tenantId={}, scheduleId={}", tenantContext.getTenantId(), scheduleId);
        return new ScheduleResponse(scheduleId, spec.getStatus());
    }

    public ScheduleResponse resume(String scheduleId, TenantContext tenantContext) {
        ScheduleSpec spec = getRequired(scheduleId, tenantContext);
        spec.setStatus("ACTIVE");
        scheduleRepository.save(spec);
        scheduleEngine.register(spec);
        log.info("调度恢复, tenantId={}, scheduleId={}", tenantContext.getTenantId(), scheduleId);
        return new ScheduleResponse(scheduleId, spec.getStatus());
    }

    public void delete(String scheduleId, TenantContext tenantContext) {
        ScheduleSpec spec = getRequired(scheduleId, tenantContext);
        scheduleRepository.delete(tenantContext.getTenantId(), scheduleId);
        scheduleEngine.unregister(scheduleId);
        log.info("调度删除, tenantId={}, scheduleId={}", tenantContext.getTenantId(), scheduleId);
    }

    public SchedulePage list(ScheduleQuery query, TenantContext tenantContext) {
        List<ScheduleSpec> specs = scheduleRepository.list(tenantContext.getTenantId());
        String statusFilter = query != null ? query.getStatus() : null;
        List<ScheduleSpec> filtered = new ArrayList<>();
        for (ScheduleSpec spec : specs) {
            if (StringUtils.hasText(statusFilter) && !statusFilter.equalsIgnoreCase(spec.getStatus())) {
                continue;
            }
            filtered.add(spec);
        }
        filtered.sort(Comparator.comparing(ScheduleSpec::getScheduleId));
        int startIndex = 0;
        if (query != null && StringUtils.hasText(query.getCursor())) {
            for (int i = 0; i < filtered.size(); i++) {
                if (query.getCursor().equals(filtered.get(i).getScheduleId())) {
                    startIndex = i + 1;
                    break;
                }
            }
        }
        int size = query != null && query.getSize() != null && query.getSize() > 0
                ? query.getSize() : filtered.size();
        int endIndex = Math.min(startIndex + size, filtered.size());
        List<ScheduleResponse> page = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            ScheduleSpec spec = filtered.get(i);
            page.add(new ScheduleResponse(spec.getScheduleId(), spec.getStatus()));
        }
        String nextCursor = endIndex < filtered.size() && endIndex > 0
                ? filtered.get(endIndex - 1).getScheduleId()
                : null;
        boolean hasMore = endIndex < filtered.size();
        return new SchedulePage(page, nextCursor, hasMore);
    }

    public void recordExecution(String scheduleId, TenantContext tenantContext, String status) {
        ScheduleExecutionRecord record = new ScheduleExecutionRecord();
        record.setExecutionId(UUID.randomUUID().toString());
        record.setScheduleId(scheduleId);
        record.setStatus(status);
        record.setStartedAt(Instant.now());
        record.setTenantId(tenantContext.getTenantId());
        executionRepository.save(record);
        metricsPublisher.increment("schedule.run.count");
    }

    private void ensureTenant(ScheduleSpec spec, TenantContext tenantContext) {
        spec.setTenantId(tenantContext.getTenantId());
    }

    private String findIdempotent(ScheduleSpec spec, TenantContext tenantContext) {
        if (!StringUtils.hasText(spec.getIdempotencyKey())) {
            return null;
        }
        return scheduleRepository.findByIdempotencyKey(tenantContext.getTenantId(), spec.getIdempotencyKey());
    }

    private ScheduleSpec getRequired(String scheduleId, TenantContext tenantContext) {
        ScheduleSpec spec = scheduleRepository.findById(tenantContext.getTenantId(), scheduleId);
        if (spec == null) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "调度不存在");
        }
        return spec;
    }
}
