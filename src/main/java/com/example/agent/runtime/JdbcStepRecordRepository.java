package com.example.agent.runtime;

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
 * PostgreSQL 步骤记录仓储，实现步骤持久化与查询。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "postgres")
public class JdbcStepRecordRepository implements StepRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcStepRecordRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcStepRecordRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 将步骤记录写入数据库，重复主键时执行更新。
     *
     * @param record 步骤记录
     */
    @Override
    public void save(StepRecord record) {
        if (record == null) {
            return;
        }
        Instant startedAt = record.getStartedAt();
        Instant completedAt = record.getCompletedAt();
        Timestamp startedAtTs = startedAt != null ? Timestamp.from(startedAt) : null;
        Timestamp completedAtTs = completedAt != null ? Timestamp.from(completedAt) : null;
        try {
            // 外部数据库调用：写入步骤记录
            jdbcTemplate.update("""
                            INSERT INTO step_records
                            (step_id, workflow_id, step_seq, type, status, attempt, input, output, error_code,
                             tenant_id, started_at, completed_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (step_id) DO UPDATE SET
                              workflow_id = EXCLUDED.workflow_id,
                              step_seq = EXCLUDED.step_seq,
                              type = EXCLUDED.type,
                              status = EXCLUDED.status,
                              attempt = EXCLUDED.attempt,
                              input = EXCLUDED.input,
                              output = EXCLUDED.output,
                              error_code = EXCLUDED.error_code,
                              tenant_id = EXCLUDED.tenant_id,
                              started_at = EXCLUDED.started_at,
                              completed_at = EXCLUDED.completed_at
                            """,
                    record.getStepId(),
                    record.getWorkflowId(),
                    record.getStepSeq(),
                    record.getType(),
                    record.getStatus() != null ? record.getStatus().name() : null,
                    record.getAttempt(),
                    toJson(record.getInput()),
                    toJson(record.getOutput()),
                    record.getErrorCode(),
                    record.getTenantId(),
                    startedAtTs,
                    completedAtTs
            );
            log.debug("步骤记录入库, tenantId={}, stepId={}", record.getTenantId(), record.getStepId());
        // 异常捕获：记录上下文并按当前策略处理
        } catch (DataAccessException ex) {
            // 异常捕获：数据库写入失败，记录上下文信息便于定位
            log.error("步骤记录入库失败, tenantId={}, stepId={}", record.getTenantId(), record.getStepId(), ex);
        }
    }

    /**
     * 按租户与工作流查询步骤记录列表。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 步骤记录列表
     */
    @Override
    public List<StepRecord> findByWorkflow(String tenantId, String workflowId) {
        try {
            // 外部数据库调用：按工作流读取步骤记录
            return jdbcTemplate.query("""
                            SELECT step_id, workflow_id, step_seq, type, status, attempt, input, output, error_code,
                                   tenant_id, started_at, completed_at
                            FROM step_records
                            WHERE tenant_id = ? AND workflow_id = ?
                            ORDER BY step_seq ASC
                            """,
                    new StepRecordRowMapper(objectMapper),
                    tenantId,
                    workflowId
            );
        // 异常捕获：记录上下文并按当前策略处理
        } catch (DataAccessException ex) {
            // 异常捕获：数据库查询失败，返回空结果并记录错误
            log.error("步骤记录查询失败, tenantId={}, workflowId={}", tenantId, workflowId, ex);
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
        // 异常捕获：记录上下文并按当前策略处理
        } catch (JsonProcessingException | SQLException ex) {
            // 异常捕获：序列化失败时返回空，避免影响主流程
            log.warn("步骤记录 JSON 序列化失败", ex);
            return null;
        }
    }

    private static class StepRecordRowMapper implements RowMapper<StepRecord> {

        private final ObjectMapper objectMapper;

        private StepRecordRowMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        /**
         * 将结果集映射为步骤记录对象。
         *
         * @param rs 结果集
         * @param rowNum 行号
         * @return 步骤记录对象
         * @throws SQLException 结果集读取异常
         */
        @Override
        public StepRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            StepRecord record = new StepRecord();
            record.setStepId(rs.getString("step_id"));
            record.setWorkflowId(rs.getString("workflow_id"));
            record.setStepSeq(rs.getLong("step_seq"));
            record.setType(rs.getString("type"));
            String status = rs.getString("status");
            if (StringUtils.hasText(status)) {
                record.setStatus(StepState.valueOf(status));
            }
            record.setAttempt(rs.getInt("attempt"));
            record.setErrorCode(rs.getString("error_code"));
            record.setTenantId(rs.getString("tenant_id"));
            if (rs.getTimestamp("started_at") != null) {
                record.setStartedAt(rs.getTimestamp("started_at").toInstant());
            }
            if (rs.getTimestamp("completed_at") != null) {
                record.setCompletedAt(rs.getTimestamp("completed_at").toInstant());
            }
            record.setInput(readJson(rs.getString("input")));
            record.setOutput(readJson(rs.getString("output")));
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
            // 异常捕获：记录上下文并按当前策略处理
            } catch (JsonProcessingException ex) {
                // 异常捕获：读取结构化内容失败时返回空，避免阻断流程
                return null;
            }
        }
    }
}
