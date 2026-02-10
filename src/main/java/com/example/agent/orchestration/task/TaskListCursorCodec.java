package com.example.agent.orchestration.task;

import com.example.agent.common.error.ErrorCodeException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * 任务列表游标编解码器。
 * <p>用途：统一任务列表翻页游标协议，确保排序键与游标键一致。
 * <p>协议：{@code updatedAtEpochMillis|taskId}。
 */
public final class TaskListCursorCodec {

    /**
     * 非法游标错误码。
     */
    public static final String INVALID_TASK_CURSOR_CODE = "INVALID_TASK_CURSOR";

    /**
     * 非法游标错误原因。
     */
    public static final String INVALID_TASK_CURSOR_REASON = "invalid_task_cursor";

    private static final String DELIMITER = "|";

    private TaskListCursorCodec() {
    }

    /**
     * 编码游标。
     */
    public static String encode(TaskRecord record) {
        if (record == null || !StringUtils.hasText(record.getTaskId())) {
            return null;
        }
        Instant updatedAt = record.getUpdatedAt() != null ? record.getUpdatedAt() : Instant.EPOCH;
        return updatedAt.toEpochMilli() + DELIMITER + record.getTaskId();
    }

    /**
     * 解析游标。
     */
    public static TaskCursor parse(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        String normalized = cursor.trim();
        int separatorIndex = normalized.indexOf(DELIMITER);
        if (separatorIndex <= 0 || separatorIndex >= normalized.length() - 1) {
            throw invalidCursorException();
        }
        String epochMillisText = normalized.substring(0, separatorIndex);
        String taskId = normalized.substring(separatorIndex + 1);
        if (!StringUtils.hasText(taskId)) {
            throw invalidCursorException();
        }
        try {
            long epochMillis = Long.parseLong(epochMillisText);
            return new TaskCursor(Instant.ofEpochMilli(epochMillis), taskId);
        } catch (RuntimeException exception) {
            throw invalidCursorException();
        }
    }

    private static ErrorCodeException invalidCursorException() {
        return new ErrorCodeException(HttpStatus.BAD_REQUEST,
                INVALID_TASK_CURSOR_CODE,
                INVALID_TASK_CURSOR_REASON);
    }

    /**
     * 游标键对象。
     */
    public record TaskCursor(Instant updatedAt, String taskId) {
    }
}

