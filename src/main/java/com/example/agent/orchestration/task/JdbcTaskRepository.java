package com.example.agent.orchestration.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

    public JdbcTaskRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskRecord save(TaskRecord record) {
        if (record == null) {
            return null;
        }
        Instant now = Instant.now();
        Instant createdAt = record.getCreatedAt() != null ? record.getCreatedAt() : now;
        Instant updatedAt = record.getUpdatedAt() != null ? record.getUpdatedAt() : now;
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
                    record.getStatus(),
                    toJson(record.getRequest()),
                    toJson(record.getResult()),
                    record.getIdempotencyKey(),
                    createdAtTs,
                    updatedAtTs,
                    record.getTenantId()
            );
            log.debug("任务入库, tenantId={}, taskId={}", record.getTenantId(), record.getTaskId());
        } catch (DataAccessException ex) {
            log.error("任务入库失败, tenantId={}, taskId={}", record.getTenantId(), record.getTaskId(), ex);
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
                    new TaskRowMapper(objectMapper),
                    tenantId,
                    taskId
            ).stream().findFirst().orElse(null);
        } catch (DataAccessException ex) {
            log.error("任务查询失败, tenantId={}, taskId={}", tenantId, taskId, ex);
            return null;
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
                    new TaskRowMapper(objectMapper),
                    tenantId,
                    idempotencyKey
            ).stream().findFirst().orElse(null);
        } catch (DataAccessException ex) {
            log.error("任务幂等查询失败, tenantId={}, idemKey={}", tenantId, idempotencyKey, ex);
            return null;
        }
    }

    @Override
    public List<TaskRecord> listByTenant(String tenantId, String status) {
        try {
            if (StringUtils.hasText(status)) {
                return jdbcTemplate.query("""
                                SELECT task_id, workflow_id, status, request, result, idempotency_key,
                                       created_at, updated_at, tenant_id
                                FROM tasks
                                WHERE tenant_id = ? AND status = ?
                                ORDER BY updated_at DESC
                                """,
                        new TaskRowMapper(objectMapper),
                        tenantId,
                        status
                );
            }
            return jdbcTemplate.query("""
                            SELECT task_id, workflow_id, status, request, result, idempotency_key,
                                   created_at, updated_at, tenant_id
                            FROM tasks
                            WHERE tenant_id = ?
                            ORDER BY updated_at DESC
                            """,
                    new TaskRowMapper(objectMapper),
                    tenantId
            );
        } catch (DataAccessException ex) {
            log.error("任务列表查询失败, tenantId={}", tenantId, ex);
            return Collections.emptyList();
        }
    }

    private PGobject toJson(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(objectMapper.writeValueAsString(payload));
            return pgObject;
        } catch (JsonProcessingException | SQLException ex) {
            log.warn("任务 JSON 序列化失败", ex);
            return null;
        }
    }

    private static class TaskRowMapper implements RowMapper<TaskRecord> {

        private final ObjectMapper objectMapper;

        private TaskRowMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public TaskRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            TaskRecord record = new TaskRecord();
            record.setTaskId(rs.getString("task_id"));
            record.setWorkflowId(rs.getString("workflow_id"));
            record.setStatus(rs.getString("status"));
            record.setIdempotencyKey(rs.getString("idempotency_key"));
            record.setTenantId(rs.getString("tenant_id"));
            if (rs.getTimestamp("created_at") != null) {
                record.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            }
            if (rs.getTimestamp("updated_at") != null) {
                record.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
            }
            record.setRequest(readJson(rs.getString("request")));
            record.setResult(readJson(rs.getString("result")));
            return record;
        }

        private Map<String, Object> readJson(String json) {
            if (!StringUtils.hasText(json)) {
                return null;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(json, Map.class);
                return map;
            } catch (JsonProcessingException ex) {
                return null;
            }
        }
    }
}
