package com.example.agent.history;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 时间线生成服务。
 */
@Service
public class TimelineService {

    private final EventLogRepository repository;

    public TimelineService(EventLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取时间线。
     *
     * @param request 时间线请求
     * @param tenantContext 租户上下文
     * @return 时间线响应
     */
    public TimelineResponse getTimeline(TimelineRequest request, TenantContext tenantContext) {
        List<EventLogRecord> records = repository.findByWorkflow(
                tenantContext.getTenantId(), request.getWorkflowId());
        if (records.isEmpty()) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "时间线不存在");
        }
        records.sort(Comparator.comparing(EventLogRecord::getTimestamp));
        String mode = StringUtils.hasText(request.getMode()) ? request.getMode() : "summary";
        List<EventLogRecord> output = "summary".equalsIgnoreCase(mode)
                ? buildSummary(records)
                : new ArrayList<>(records);
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", records.size());
        stats.put("mode", mode);
        return new TimelineResponse(request.getWorkflowId(), mode, output, stats);
    }

    private List<EventLogRecord> buildSummary(List<EventLogRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<EventLogRecord> summary = new ArrayList<>();
        summary.add(records.get(0));
        if (records.size() > 1) {
            summary.add(records.get(records.size() - 1));
        }
        for (EventLogRecord record : records) {
            if ("ERROR_OCCURRED".equals(record.getType())) {
                summary.add(record);
            }
        }
        return summary;
    }
}
