package com.example.agent.orchestration.multiagent.dag.replay;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.history.eventlog.EventLogRepository;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditSnapshot;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * DAG 语义回放服务。
 *
 * <p>用途：按照 dagRunId 聚合审计与事件日志，生成可重放的 DAG 运行帧。</p>
 * <p>输入：工作流标识、租户标识、游标分页与节点过滤条件。</p>
 * <p>输出：结构化回放响应，包含帧列表、游标和摘要统计。</p>
 * <p>边界：当指定 dagRunId 不存在时抛出 404 业务异常。</p>
 */
@Service
public class DagReplayService {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagReplayService.class);

    /**
     * 默认分页大小。
     */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * 分页大小上限。
     */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * DAG 审计服务。
     */
    private final DagAuditService dagAuditService;

    /**
     * 事件日志仓储。
     */
    private final EventLogRepository eventLogRepository;

    public DagReplayService(DagAuditService dagAuditService,
                            EventLogRepository eventLogRepository) {
        this.dagAuditService = dagAuditService;
        this.eventLogRepository = eventLogRepository;
    }

    /**
     * 按 DAG 运行维度回放事件。
     *
     * @param workflowId 工作流标识
     * @param tenantId 租户标识
     * @param dagRunId DAG 运行标识，可为空
     * @param cursor 事件游标，可为空
     * @param size 页大小，可为空
     * @param fromSeq 起始序号，可为空
     * @param toSeq 结束序号，可为空
     * @param nodeId 节点标识，可为空
     * @param attempt 尝试次数，可为空
     * @return 回放响应
     */
    public DagReplayResponse replay(String workflowId,
                                    String tenantId,
                                    String dagRunId,
                                    String cursor,
                                    Integer size,
                                    Long fromSeq,
                                    Long toSeq,
                                    String nodeId,
                                    Integer attempt) {
        // 关键逻辑：入参缺失时直接拒绝，避免跨租户或空工作流查询。
        if (!StringUtils.hasText(workflowId) || !StringUtils.hasText(tenantId)) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "DAG_REPLAY_INVALID_REQUEST", "回放参数不完整");
        }
        // 关键逻辑：优先使用外部指定 dagRunId，否则回退到当前工作流最新运行。
        String targetDagRunId = resolveDagRunId(workflowId, dagRunId);
        // 关键逻辑：查询审计快照用于补齐回放摘要。
        Optional<DagAuditSnapshot> snapshotOptional = dagAuditService.findSnapshot(targetDagRunId);
        // 关键逻辑：未找到快照时说明该运行不存在，直接返回 404。
        if (snapshotOptional.isEmpty()) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "DAG_RUN_NOT_FOUND", "DAG运行记录不存在");
        }
        // 关键逻辑：读取工作流全量事件，再按 dagRunId 与过滤条件进行裁剪。
        List<EventLogRecord> workflowEvents = eventLogRepository.findByWorkflow(tenantId, workflowId);
        // 关键逻辑：事件先按序号升序排序，保证回放时序一致。
        workflowEvents.sort(Comparator.comparingLong(this::parseSeqSafely));
        // 关键逻辑：应用 dagRunId、节点、尝试次数和序号区间过滤。
        List<EventLogRecord> filteredEvents = filterEvents(workflowEvents,
                targetDagRunId,
                fromSeq,
                toSeq,
                nodeId,
                attempt);

        // 关键逻辑：根据游标定位起始位置，实现稳定续传。
        int startIndex = resolveStartIndex(filteredEvents, cursor);
        // 关键逻辑：约束分页大小，防止单次返回过大。
        int pageSize = normalizePageSize(size);
        // 关键逻辑：计算分页终点并截取当前页。
        int endIndex = Math.min(startIndex + pageSize, filteredEvents.size());
        List<EventLogRecord> pageEvents = startIndex < endIndex
                ? new ArrayList<>(filteredEvents.subList(startIndex, endIndex))
                : List.of();
        // 关键逻辑：转换当前页为回放帧。
        List<DagReplayFrame> frames = buildFrames(pageEvents);
        // 关键逻辑：若存在下一页则返回最后一条事件作为 nextCursor。
        String nextCursor = endIndex < filteredEvents.size()
                ? filteredEvents.get(endIndex - 1).getEventId()
                : null;
        boolean hasMore = endIndex < filteredEvents.size();

        DagReplayResponse response = new DagReplayResponse();
        response.setWorkflowId(workflowId);
        response.setDagRunId(targetDagRunId);
        response.setFromCursor(cursor);
        response.setNextCursor(nextCursor);
        response.setHasMore(hasMore);
        response.setFrames(frames);
        // 关键逻辑：汇总运行态统计，给前端直接展示基础诊断面板。
        response.setSummary(buildSummary(snapshotOptional.get(), filteredEvents.size(), frames.size(), fromSeq, toSeq));
        log.info("DAG回放完成, workflowId={}, dagRunId={}, frameSize={}, hasMore={}",
                workflowId,
                targetDagRunId,
                frames.size(),
                hasMore);
        return response;
    }

    /**
     * 解析目标 DAG 运行标识。
     */
    private String resolveDagRunId(String workflowId, String dagRunId) {
        // 关键逻辑：显式传入时直接采用，避免查询歧义。
        if (StringUtils.hasText(dagRunId)) {
            return dagRunId;
        }
        // 关键逻辑：按 startedAt 倒序选择最新运行，兜底使用 dagRunId 字典序。
        List<DagRunAuditRecord> runRecords = dagAuditService.findRunRecordsByWorkflow(workflowId);
        runRecords.sort((left, right) -> {
            // 关键逻辑：优先比较开始时间，保证回放默认取最近一次运行。
            if (left.getStartedAt() != null && right.getStartedAt() != null) {
                return right.getStartedAt().compareTo(left.getStartedAt());
            }
            // 关键逻辑：开始时间缺失时回退到 dagRunId 逆序，保证结果稳定。
            return String.valueOf(right.getDagRunId()).compareTo(String.valueOf(left.getDagRunId()));
        });
        // 关键逻辑：无运行记录时返回空串，交由上层统一抛 404。
        if (runRecords.isEmpty()) {
            return "";
        }
        return runRecords.get(0).getDagRunId();
    }

    /**
     * 按条件过滤事件。
     */
    private List<EventLogRecord> filterEvents(List<EventLogRecord> source,
                                              String dagRunId,
                                              Long fromSeq,
                                              Long toSeq,
                                              String nodeId,
                                              Integer attempt) {
        List<EventLogRecord> filtered = new ArrayList<>();
        // 关键逻辑：逐条检查事件锚点字段，确保仅保留目标 DAG 运行数据。
        for (EventLogRecord record : source) {
            // 关键逻辑：空记录直接跳过，避免空指针风险。
            if (record == null) {
                continue;
            }
            Map<String, Object> payload = safePayload(record.getPayload());
            String payloadDagRunId = asText(payload.get("dagRunId"));
            // 关键逻辑：dagRunId 不匹配时跳过，避免跨运行串帧。
            if (!StringUtils.hasText(payloadDagRunId) || !payloadDagRunId.equals(dagRunId)) {
                continue;
            }
            long seq = parseSeqSafely(record);
            // 关键逻辑：起始序号过滤用于截取回放窗口。
            if (fromSeq != null && seq < fromSeq) {
                continue;
            }
            // 关键逻辑：结束序号过滤用于截取回放窗口。
            if (toSeq != null && seq > toSeq) {
                continue;
            }
            String payloadNodeId = asText(payload.get("nodeId"));
            // 关键逻辑：节点过滤仅保留目标节点事件。
            if (StringUtils.hasText(nodeId) && !nodeId.equals(payloadNodeId)) {
                continue;
            }
            Integer payloadAttempt = parseInteger(payload.get("attempt"));
            // 关键逻辑：尝试次数过滤用于重试链路精确回放。
            if (attempt != null && !attempt.equals(payloadAttempt)) {
                continue;
            }
            filtered.add(record);
        }
        return filtered;
    }

    /**
     * 计算游标起始位置。
     */
    private int resolveStartIndex(List<EventLogRecord> events, String cursor) {
        // 关键逻辑：游标为空时从第一页开始返回。
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        // 关键逻辑：找到游标后从下一条开始，保证结果不重复。
        for (int index = 0; index < events.size(); index++) {
            EventLogRecord record = events.get(index);
            if (record != null && cursor.equals(record.getEventId())) {
                return index + 1;
            }
        }
        return 0;
    }

    /**
     * 归一化分页大小。
     */
    private int normalizePageSize(Integer size) {
        // 关键逻辑：默认分页用于避免调用方遗漏 size 参数。
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        // 关键逻辑：非法分页值回退默认值，避免负数或零触发异常。
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        // 关键逻辑：分页上限保护服务稳定性。
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 构建回放帧列表。
     */
    private List<DagReplayFrame> buildFrames(List<EventLogRecord> pageEvents) {
        List<DagReplayFrame> frames = new ArrayList<>();
        // 关键逻辑：逐条映射事件记录到回放帧，保留原始 payload。
        for (EventLogRecord record : pageEvents) {
            // 关键逻辑：空记录直接跳过，保证帧列表有效。
            if (record == null) {
                continue;
            }
            Map<String, Object> payload = safePayload(record.getPayload());
            DagReplayFrame frame = new DagReplayFrame();
            frame.setEventId(record.getEventId());
            frame.setEventType(record.getType());
            frame.setSeq(parseSeqSafely(record));
            frame.setTimestamp(record.getTimestamp());
            frame.setNodeId(asText(payload.get("nodeId")));
            frame.setAttempt(parseInteger(payload.get("attempt")));
            frame.setReason(asText(payload.get("reason")));
            frame.setPayload(payload);
            frames.add(frame);
        }
        return frames;
    }

    /**
     * 构建回放摘要。
     */
    private Map<String, Object> buildSummary(DagAuditSnapshot snapshot,
                                             int matchedTotal,
                                             int pageSize,
                                             Long fromSeq,
                                             Long toSeq) {
        Map<String, Object> summary = new HashMap<>();
        DagRunAuditRecord runRecord = snapshot.getRunRecord();
        // 关键逻辑：运行主记录存在时优先输出运行态摘要。
        if (runRecord != null) {
            summary.put("status", runRecord.getStatus());
            summary.put("failurePolicy", runRecord.getFailurePolicy());
            summary.put("startedAt", runRecord.getStartedAt());
            summary.put("completedAt", runRecord.getCompletedAt());
            summary.put("failures", runRecord.getFailures() == null ? List.of() : runRecord.getFailures());
        }
        summary.put("matchedTotal", matchedTotal);
        summary.put("pageSize", pageSize);
        summary.put("fromSeq", fromSeq);
        summary.put("toSeq", toSeq);
        summary.put("attemptCount", snapshot.getAttempts() == null ? 0 : snapshot.getAttempts().size());
        summary.put("dependencyEventCount",
                snapshot.getDependencyEvents() == null ? 0 : snapshot.getDependencyEvents().size());
        summary.put("backpressureEventCount",
                snapshot.getBackpressureRecords() == null ? 0 : snapshot.getBackpressureRecords().size());
        return summary;
    }

    /**
     * 安全获取 payload。
     */
    private Map<String, Object> safePayload(Map<String, Object> payload) {
        // 关键逻辑：空 payload 返回空 map，避免下游判空分支膨胀。
        if (payload == null) {
            return Map.of();
        }
        return new HashMap<>(payload);
    }

    /**
     * 安全解析事件序号。
     */
    private long parseSeqSafely(EventLogRecord record) {
        // 关键逻辑：事件为空时返回 0，保持排序稳定。
        if (record == null || !StringUtils.hasText(record.getEventId())) {
            return 0L;
        }
        String eventId = record.getEventId();
        int splitIndex = eventId.lastIndexOf(':');
        // 关键逻辑：不符合 workflow:seq 格式时返回 0。
        if (splitIndex < 0 || splitIndex >= eventId.length() - 1) {
            return 0L;
        }
        try {
            // 关键逻辑：优先按 eventId 后缀解析序号。
            return Long.parseLong(eventId.substring(splitIndex + 1));
        } catch (NumberFormatException exception) {
            log.warn("事件序号解析失败, eventId={}", eventId, exception);
            return 0L;
        }
    }

    /**
     * 转换文本。
     */
    private String asText(Object value) {
        // 关键逻辑：空值统一转换为空字符串，简化上层判空。
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    /**
     * 解析整数。
     */
    private Integer parseInteger(Object value) {
        // 关键逻辑：空值直接返回 null，表示该事件没有尝试次数字段。
        if (value == null) {
            return null;
        }
        // 关键逻辑：数值类型直接转换，避免字符串解析误差。
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            // 关键逻辑：字符串输入按整数格式解析。
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            log.debug("尝试次数解析失败, value={}", value, exception);
            return null;
        }
    }
}

