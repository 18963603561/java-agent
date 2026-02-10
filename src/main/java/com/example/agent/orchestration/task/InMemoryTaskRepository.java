package com.example.agent.orchestration.task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 内存任务仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryTaskRepository implements TaskRepository {

    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    private final TaskStatusMapper taskStatusMapper;

    public InMemoryTaskRepository(TaskStatusMapper taskStatusMapper) {
        this.taskStatusMapper = taskStatusMapper;
    }

    @Override
    public TaskRecord save(TaskRecord record) {
        if (record == null) {
            return null;
        }
        if (record.getStatus() == null) {
            throw new IllegalArgumentException("invalid_task_status");
        }
        tasks.put(record.getTaskId(), record);
        if (StringUtils.hasText(record.getIdempotencyKey())) {
            idempotencyIndex.put(buildIdempotencyKey(record.getTenantId(), record.getIdempotencyKey()),
                    record.getTaskId());
        }
        return record;
    }

    @Override
    public TaskRecord findById(String tenantId, String taskId) {
        TaskRecord record = tasks.get(taskId);
        if (record == null || !tenantId.equals(record.getTenantId())) {
            return null;
        }
        return record;
    }

    @Override
    public TaskRecord findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String taskId = idempotencyIndex.get(buildIdempotencyKey(tenantId, idempotencyKey));
        if (taskId == null) {
            return null;
        }
        return findById(tenantId, taskId);
    }

    @Override
    public TaskPageResult listPage(TaskPageQuery query) {
        String tenantId = query.getTenantId();
        String status = query.getStatus();
        TaskStatus statusFilter = status != null ? taskStatusMapper.toDomainStatus(status) : null;
        List<TaskRecord> records = new ArrayList<>();
        for (TaskRecord record : tasks.values()) {
            if (!tenantId.equals(record.getTenantId())) {
                continue;
            }
            if (statusFilter != null && record.getStatus() != statusFilter) {
                continue;
            }
            records.add(record);
        }
        records.sort((left, right) -> {
            int updatedCompare = Comparator.comparing(TaskRecord::getUpdatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed()
                    .compare(left, right);
            if (updatedCompare != 0) {
                return updatedCompare;
            }
            String leftTaskId = left != null ? left.getTaskId() : null;
            String rightTaskId = right != null ? right.getTaskId() : null;
            return Comparator.nullsLast(Comparator.<String>naturalOrder()).reversed()
                    .compare(leftTaskId, rightTaskId);
        });

        TaskListCursorCodec.TaskCursor parsedCursor = TaskListCursorCodec.parse(query.getCursor());
        int startIndex = 0;
        if (parsedCursor != null) {
            for (int index = 0; index < records.size(); index++) {
                TaskRecord current = records.get(index);
                if (isAfterCursor(current, parsedCursor)) {
                    startIndex = index;
                    break;
                }
                startIndex = records.size();
            }
        }

        int pageSize = query.getSize();
        int endIndex = Math.min(startIndex + pageSize, records.size());
        List<TaskRecord> page = new ArrayList<>();
        if (startIndex < endIndex) {
            page.addAll(records.subList(startIndex, endIndex));
        }
        boolean hasMore = endIndex < records.size();
        String nextCursor = hasMore && !page.isEmpty()
                ? TaskListCursorCodec.encode(page.get(page.size() - 1))
                : null;
        return new TaskPageResult(page, nextCursor, hasMore, records.size());
    }

    /**
     * 判断记录是否位于游标之后。
     */
    private boolean isAfterCursor(TaskRecord record, TaskListCursorCodec.TaskCursor cursor) {
        if (record == null || cursor == null) {
            return false;
        }
        long updatedAt = record.getUpdatedAt() != null ? record.getUpdatedAt().toEpochMilli() : 0L;
        long cursorUpdatedAt = cursor.updatedAt() != null ? cursor.updatedAt().toEpochMilli() : 0L;
        if (updatedAt < cursorUpdatedAt) {
            return true;
        }
        if (updatedAt > cursorUpdatedAt) {
            return false;
        }
        String recordTaskId = record.getTaskId();
        String cursorTaskId = cursor.taskId();
        if (!StringUtils.hasText(recordTaskId) || !StringUtils.hasText(cursorTaskId)) {
            return false;
        }
        return recordTaskId.compareTo(cursorTaskId) < 0;
    }

    private String buildIdempotencyKey(String tenantId, String key) {
        return tenantId + ":" + key;
    }
}
