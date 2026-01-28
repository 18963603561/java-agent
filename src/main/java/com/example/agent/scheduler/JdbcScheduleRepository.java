package com.example.agent.scheduler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import org.springframework.util.StringUtils;

/**
 * PostgreSQL 调度仓储，实现调度持久化与查询。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "postgres")
public class JdbcScheduleRepository implements ScheduleRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcScheduleRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ScheduleSpec save(ScheduleSpec spec) {
        if (spec == null) {
            return null;
        }
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);
        try {
            jdbcTemplate.update("""
                            INSERT INTO scheduled_tasks
                            (schedule_id, cron, timezone, status, idempotency_key, tenant_id, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (schedule_id) DO UPDATE SET
                              cron = EXCLUDED.cron,
                              timezone = EXCLUDED.timezone,
                              status = EXCLUDED.status,
                              idempotency_key = EXCLUDED.idempotency_key,
                              tenant_id = EXCLUDED.tenant_id,
                              updated_at = EXCLUDED.updated_at
                            """,
                    spec.getScheduleId(),
                    spec.getCron(),
                    spec.getTimezone(),
                    spec.getStatus(),
                    spec.getIdempotencyKey(),
                    spec.getTenantId(),
                    nowTs,
                    nowTs
            );
            log.debug("调度入库, tenantId={}, scheduleId={}", spec.getTenantId(), spec.getScheduleId());
            return spec;
        } catch (DataAccessException ex) {
            log.error("调度入库失败, tenantId={}, scheduleId={}",
                    spec.getTenantId(), spec.getScheduleId(), ex);
            return spec;
        }
    }

    @Override
    public ScheduleSpec findById(String tenantId, String scheduleId) {
        try {
            return jdbcTemplate.query("""
                            SELECT schedule_id, cron, timezone, status, idempotency_key, tenant_id
                            FROM scheduled_tasks
                            WHERE tenant_id = ? AND schedule_id = ?
                            """,
                    new ScheduleRowMapper(),
                    tenantId,
                    scheduleId
            ).stream().findFirst().orElse(null);
        } catch (DataAccessException ex) {
            log.error("调度查询失败, tenantId={}, scheduleId={}", tenantId, scheduleId, ex);
            return null;
        }
    }

    @Override
    public String findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT schedule_id
                            FROM scheduled_tasks
                            WHERE tenant_id = ? AND idempotency_key = ?
                            """,
                    String.class,
                    tenantId,
                    idempotencyKey
            );
        } catch (DataAccessException ex) {
            return null;
        }
    }

    @Override
    public void delete(String tenantId, String scheduleId) {
        try {
            jdbcTemplate.update("""
                            DELETE FROM scheduled_tasks
                            WHERE tenant_id = ? AND schedule_id = ?
                            """,
                    tenantId,
                    scheduleId
            );
            log.debug("调度删除, tenantId={}, scheduleId={}", tenantId, scheduleId);
        } catch (DataAccessException ex) {
            log.error("调度删除失败, tenantId={}, scheduleId={}", tenantId, scheduleId, ex);
        }
    }

    @Override
    public List<ScheduleSpec> list(String tenantId) {
        try {
            return jdbcTemplate.query("""
                            SELECT schedule_id, cron, timezone, status, idempotency_key, tenant_id
                            FROM scheduled_tasks
                            WHERE tenant_id = ?
                            ORDER BY schedule_id ASC
                            """,
                    new ScheduleRowMapper(),
                    tenantId
            );
        } catch (DataAccessException ex) {
            log.error("调度列表查询失败, tenantId={}", tenantId, ex);
            return Collections.emptyList();
        }
    }

    private static class ScheduleRowMapper implements RowMapper<ScheduleSpec> {

        @Override
        public ScheduleSpec mapRow(ResultSet rs, int rowNum) throws SQLException {
            ScheduleSpec spec = new ScheduleSpec();
            spec.setScheduleId(rs.getString("schedule_id"));
            spec.setCron(rs.getString("cron"));
            spec.setTimezone(rs.getString("timezone"));
            spec.setStatus(rs.getString("status"));
            spec.setIdempotencyKey(rs.getString("idempotency_key"));
            spec.setTenantId(rs.getString("tenant_id"));
            return spec;
        }
    }
}
