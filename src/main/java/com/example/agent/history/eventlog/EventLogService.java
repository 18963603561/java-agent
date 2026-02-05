package com.example.agent.history.eventlog;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 事件日志服务，负责事件持久化与查询。
 */
@Service
public class EventLogService {

    private static final Logger log = LoggerFactory.getLogger(EventLogService.class);

    private final EventLogRepository repository;
    private final MetricsPublisher metricsPublisher;

    public EventLogService(EventLogRepository repository, MetricsPublisher metricsPublisher) {
        this.repository = repository;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 监听事件并持久化。
     *
     * @param event 事件
     */
    @EventListener
    public void onStreamEvent(StreamEvent event) {
        if (event == null || event.getTenantId() == null || event.getWorkflowId() == null) {
            return;
        }
        if (event.getType() == EventType.LLM_PARTIAL) {
            return;
        }
        EventLogRecord record = new EventLogRecord();
        record.setEventId(event.getEventId());
        record.setWorkflowId(event.getWorkflowId());
        record.setType(event.getType() != null ? event.getType().name() : null);
        record.setTimestamp(event.getTimestamp());
        record.setPayload(event.getPayload());
        record.setTenantId(event.getTenantId());
        boolean saved = repository.saveIfAbsent(record);
        if (saved) {
            metricsPublisher.increment("event.persist.count");
        }
    }

    /**
     * 查询事件日志。
     *
     * @param query 查询参数
     * @param tenantContext 租户上下文
     * @return 事件分页
     */
    public EventLogPage listEvents(EventQuery query, TenantContext tenantContext) {
        List<EventLogRecord> records = repository.findByWorkflow(
                tenantContext.getTenantId(), query.getWorkflowId());
        if (records.isEmpty()) {
            return new EventLogPage(List.of(), null, false);
        }
        records.sort((left, right) -> {
            long leftSeq = parseSeq(left.getEventId());
            long rightSeq = parseSeq(right.getEventId());
            if (leftSeq > 0 && rightSeq > 0 && leftSeq != rightSeq) {
                return Long.compare(leftSeq, rightSeq);
            }
            if (left.getTimestamp() == null && right.getTimestamp() == null) {
                return 0;
            }
            if (left.getTimestamp() == null) {
                return -1;
            }
            if (right.getTimestamp() == null) {
                return 1;
            }
            return left.getTimestamp().compareTo(right.getTimestamp());
        });

        int startIndex = 0;
        if (query.getCursor() != null && !query.getCursor().isBlank()) {
            for (int i = 0; i < records.size(); i++) {
                if (query.getCursor().equals(records.get(i).getEventId())) {
                    startIndex = i + 1;
                    break;
                }
            }
        }

        int size = query.getSize() != null && query.getSize() > 0 ? query.getSize() : records.size();
        int endIndex = Math.min(startIndex + size, records.size());
        List<EventLogRecord> page = startIndex < endIndex
                ? new ArrayList<>(records.subList(startIndex, endIndex))
                : new ArrayList<>();

        String nextCursor = endIndex < records.size() ? records.get(endIndex - 1).getEventId() : null;
        boolean hasMore = endIndex < records.size();
        return new EventLogPage(page, nextCursor, hasMore);
    }

    private long parseSeq(String eventId) {
        if (eventId == null) {
            return 0;
        }
        int index = eventId.lastIndexOf(':');
        if (index < 0 || index == eventId.length() - 1) {
            return 0;
        }
        try {
            return Long.parseLong(eventId.substring(index + 1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
