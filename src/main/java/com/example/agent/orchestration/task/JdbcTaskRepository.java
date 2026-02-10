package com.example.agent.orchestration.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * PostgreSQL 任务仓储，实现任务持久化与查询。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "postgres")
public class JdbcTaskRepository implements TaskRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTaskRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TaskStatusMapper taskStatusMapper;

    public JdbcTaskRepository(JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              TaskStatusMapper taskStatusMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.taskStatusMapper = taskStatusMapper;
    }

    @Override
    public TaskRecord save(TaskRecord record) {
        if (record == null) {
            return null;
        }
        Instant now = Instant.now();
        Instant createdAt = record.getCreatedAt() != null ? record.getCreatedAt() : now;
        Instant updatedAt = record.getUpdatedAt() != null ? record.getUpdatedAt() : now;
        TaskStatus normalizedStatus = record.getStatus();
        if (normalizedStatus == null) {
            throw new IllegalArgumentException("invalid_task_status");
        }
        String persistedStatus = taskStatusMapper.toPersistedValue(normalizedStatus);
        Timestamp createdAtTs = Timestamp.from(createdAt);
        Timestamp updatedAtTs = Timestamp.from(updatedAt);
        try {
            jdbcTemplate.update("""
                            INSERT INTO tasks
                            (task_id, workflow_id, status, request, result, idempotency_key, created_at, updated_at, tenant_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (task_id) DO UPDATE SET
                              workflow_id = EXCLUDED.workflow_id,
                              status = EXCLUDED.status,
                              request = EXCLUDED.request,
                              result = EXCLUDED.result,
                              idempotency_key = EXCLUDED.idempotency_key,
                              updated_at = EXCLUDED.updated_at
                            """,
                    record.getTaskId(),
                    record.getWorkflowId(),
                    persistedStatus,
                    toJson(record.getRequest(), "request", record.getTenantId(), record.getTaskId(),
                            record.getWorkflowId()),
                    toJson(record.getResult(), "result", record.getTenantId(), record.getTaskId(),
                            record.getWorkflowId()),
                    record.getIdempotencyKey(),
                    createdAtTs,
                    updatedAtTs,
                    record.getTenantId()
            );
            log.debug("任务入库, tenantId={}, taskId={}", record.getTenantId(), record.getTaskId());
        } catch (DataAccessException ex) {
            log.error("任务入库失败, tenantId={}, taskId={}", record.getTenantId(), record.getTaskId(), ex);
            throw new TaskRepositoryException("save", record.getTenantId(), record.getTaskId(),
                    record.getWorkflowId(), ex);
        }
        return record;
    }

    @Override
    public TaskRecord findById(String tenantId, String taskId) {
        try {
            return jdbcTemplate.query("""
                            SELECT task_id, workflow_id, status, request, result, idempotency_key,
                                   created_at, updated_at, tenant_id
                    FROM tasks
                    WHERE tenant_id = ? AND task_id = ?
                    """,
                    new TaskRowMapper(objectMapper, taskStatusMapper),
                    tenantId,
                    taskId
            ).stream().findFirst().orElse(null);
        } catch (DataAccessException ex) {
            log.error("任务查询失败, tenantId={}, taskId={}", tenantId, taskId, ex);
            throw new TaskRepositoryException("find_by_id", tenantId, taskId, null, ex);
        }
    }

    @Override
    public TaskRecord findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        try {
            return jdbcTemplate.query("""
                            SELECT task_id, workflow_id, status, request, result, idempotency_key,
                                   created_at, updated_at, tenant_id
                            FROM tasks
                            WHERE tenant_id = ? AND idempotency_key = ?
                            ORDER BY updated_at DESC
                            LIMIT 1
                            """,
                    new TaskRowMapper(objectMapper, taskStatusMapper),
                    tenantId,
                    idempotencyKey
            ).stream().findFirst().orElse(null);
        } catch (DataAccessException ex) {
            log.error("任务幂等查询失败, tenantId={}, idemKey={}", tenantId, idempotencyKey, ex);
            throw new TaskRepositoryException("find_by_idempotency_key", tenantId, null, null, ex);
        }
    }

    @Override
    public TaskPageResult listPage(TaskPageQuery query) {
        String tenantId = query.getTenantId();
        String status = query.getStatus();
        TaskStatus statusFilter = status != null ? taskStatusMapper.toDomainStatus(status) : null;
        TaskListCursorCodec.TaskCursor cursor = TaskListCursorCodec.parse(query.getCursor());
        try {
            Long total = countTasks(tenantId, statusFilter);
            List<Object> params = new ArrayList<>();
            StringBuilder sql = new StringBuilder();
            sql.append("""
                    SELECT task_id, workflow_id, status, request, result, idempotency_key,
                           created_at, updated_at, tenant_id
                    FROM tasks
                    WHERE tenant_id = ?
                    """);
            params.add(tenantId);
            if (statusFilter != null) {
                sql.append(" AND status = ?");
                params.add(taskStatusMapper.toPersistedValue(statusFilter));
            }
            if (cursor != null) {
                sql.append(" AND (updated_at < ? OR (updated_at = ? AND task_id < ?))");
                Timestamp cursorTimestamp = cursor.updatedAt() != null
                        ? Timestamp.from(cursor.updatedAt())
                        : Timestamp.from(Instant.EPOCH);
                params.add(cursorTimestamp);
                params.add(cursorTimestamp);
                params.add(cursor.taskId());
            }
            sql.append(" ORDER BY updated_at DESC, task_id DESC LIMIT ?");
            params.add(query.getSize() + 1);

            log.debug("任务分页查询, tenantId={}, status={}, hasCursor={}, size={}",
                    tenantId, status, cursor != null, query.getSize());
            List<TaskRecord> fetched = jdbcTemplate.query(sql.toString(),
                    new TaskRowMapper(objectMapper, taskStatusMapper),
                    params.toArray());

            boolean hasMore = fetched.size() > query.getSize();
            List<TaskRecord> page = hasMore
                    ? fetched.subList(0, query.getSize())
                    : fetched;
            String nextCursor = hasMore && !page.isEmpty()
                    ? TaskListCursorCodec.encode(page.get(page.size() - 1))
                    : null;
            return new TaskPageResult(page, nextCursor, hasMore, total != null ? total : 0L);
        } catch (DataAccessException ex) {
            log.error("任务分页查询失败, tenantId={}, status={}, hasCursor={}, size={}",
                    tenantId, status, cursor != null, query.getSize(), ex);
            throw new TaskRepositoryException("list_page", tenantId, null, null, ex);
        }
    }

    /**
     * 统计筛选后任务总数。
     */
    private Long countTasks(String tenantId, TaskStatus status) {
        if (status != null) {
            return jdbcTemplate.queryForObject("""
                            SELECT COUNT(*)
                            FROM tasks
                            WHERE tenant_id = ? AND status = ?
                            """,
                    SingleColumnRowMapper.newInstance(Long.class),
                    tenantId,
                    taskStatusMapper.toPersistedValue(status));
        }
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM tasks
                        WHERE tenant_id = ?
                        """,
                SingleColumnRowMapper.newInstance(Long.class),
                tenantId);
    }

    private PGobject toJson(Map<String, Object> payload,
                            String fieldName,
                            String tenantId,
                            String taskId,
                            String workflowId) {
        if (payload == null) {
            return null;
        }
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(objectMapper.writeValueAsString(payload));
            return pgObject;
        } catch (JsonProcessingException | SQLException ex) {
            log.error("任务 JSON 序列化失败, tenantId={}, taskId={}, workflowId={}, fieldName={}",
                    tenantId, taskId, workflowId, fieldName, ex);
            throw new TaskRepositoryException("serialize_json", tenantId, taskId, workflowId, ex);
        }
    }

    private static class TaskRowMapper implements RowMapper<TaskRecord> {

        private final ObjectMapper objectMapper;
        private final TaskStatusMapper taskStatusMapper;

        private TaskRowMapper(ObjectMapper objectMapper, TaskStatusMapper taskStatusMapper) {
            this.objectMapper = objectMapper;
            this.taskStatusMapper = taskStatusMapper;
        }

        @Override
        public TaskRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            TaskRecord record = new TaskRecord();
            record.setTaskId(rs.getString("task_id"));
            record.setWorkflowId(rs.getString("workflow_id"));
            record.setStatus(taskStatusMapper.toDomainStatus(rs.getString("status")));
            record.setIdempotencyKey(rs.getString("idempotency_key"));
            record.setTenantId(rs.getString("tenant_id"));
            if (rs.getTimestamp("created_at") != null) {
                record.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            }
            if (rs.getTimestamp("updated_at") != null) {
                record.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
            }
            record.setRequest(readJson(rs.getString("request"), "request", record.getTenantId(),
                    record.getTaskId(), record.getWorkflowId()));
            record.setResult(readJson(rs.getString("result"), "result", record.getTenantId(),
                    record.getTaskId(), record.getWorkflowId()));
            return record;
        }

        private Map<String, Object> readJson(String json,
                                             String fieldName,
                                             String tenantId,
                                             String taskId,
                                             String workflowId) {
            if (!StringUtils.hasText(json)) {
                return null;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(json, Map.class);
                return map;
            } catch (JsonProcessingException ex) {
                LoggerFactory.getLogger(JdbcTaskRepository.class).error(
                        "任务 JSON 反序列化失败, tenantId={}, taskId={}, workflowId={}, fieldName={}",
                        tenantId, taskId, workflowId, fieldName, ex);
                throw new TaskRepositoryException("deserialize_json_" + fieldName,
                        tenantId, taskId, workflowId, ex);
            }
        }
    }
}
