package com.example.agent.scheduler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL 调度执行记录仓储，实现调度执行历史持久化。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "postgres")
public class JdbcScheduleExecutionRepository implements ScheduleExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcScheduleExecutionRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcScheduleExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ScheduleExecutionRecord record) {
        if (record == null) {
            return;
        }
        Instant startedAt = record.getStartedAt() != null ? record.getStartedAt() : Instant.now();
        try {
            jdbcTemplate.update("""
                            INSERT INTO scheduled_task_executions
                            (execution_id, schedule_id, status, started_at, completed_at, cost_usd, token_usage, tenant_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    record.getExecutionId(),
                    record.getScheduleId(),
                    record.getStatus(),
                    startedAt,
                    record.getEndedAt(),
                    record.getCostUsd(),
                    record.getTokenUsage(),
                    record.getTenantId()
            );
            log.debug("调度执行记录入库, tenantId={}, executionId={}",
                    record.getTenantId(), record.getExecutionId());
        } catch (DataAccessException ex) {
            log.error("调度执行记录入库失败, tenantId={}, executionId={}",
                    record.getTenantId(), record.getExecutionId(), ex);
        }
    }

    @Override
    public List<ScheduleExecutionRecord> findBySchedule(String tenantId, String scheduleId) {
        try {
            return jdbcTemplate.query("""
                            SELECT execution_id, schedule_id, status, started_at, completed_at, cost_usd,
                                   token_usage, tenant_id
                            FROM scheduled_task_executions
                            WHERE tenant_id = ? AND schedule_id = ?
                            ORDER BY started_at ASC
                            """,
                    new ScheduleExecutionRowMapper(),
                    tenantId,
                    scheduleId
            );
        } catch (DataAccessException ex) {
            log.error("调度执行记录查询失败, tenantId={}, scheduleId={}", tenantId, scheduleId, ex);
            return Collections.emptyList();
        }
    }

    private static class ScheduleExecutionRowMapper implements RowMapper<ScheduleExecutionRecord> {

        @Override
        public ScheduleExecutionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            ScheduleExecutionRecord record = new ScheduleExecutionRecord();
            record.setExecutionId(rs.getString("execution_id"));
            record.setScheduleId(rs.getString("schedule_id"));
            record.setStatus(rs.getString("status"));
            if (rs.getTimestamp("started_at") != null) {
                record.setStartedAt(rs.getTimestamp("started_at").toInstant());
            }
            if (rs.getTimestamp("completed_at") != null) {
                record.setEndedAt(rs.getTimestamp("completed_at").toInstant());
            }
            record.setCostUsd(rs.getDouble("cost_usd"));
            record.setTokenUsage(rs.getInt("token_usage"));
            record.setTenantId(rs.getString("tenant_id"));
            return record;
        }
    }
}
