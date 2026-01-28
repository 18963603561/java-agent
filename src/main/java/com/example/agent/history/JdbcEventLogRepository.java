package com.example.agent.history;

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
 * PostgreSQL 事件日志仓储，实现持久化事件记录。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "postgres")
public class JdbcEventLogRepository implements EventLogRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcEventLogRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcEventLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean saveIfAbsent(EventLogRecord record) {
        if (record == null || !StringUtils.hasText(record.getEventId())) {
            return false;
        }
        Instant timestamp = record.getTimestamp() != null ? record.getTimestamp() : Instant.now();
        Timestamp timestampTs = Timestamp.from(timestamp);
        Timestamp createdAtTs = Timestamp.from(Instant.now());
        Long seq = parseSeq(record.getEventId());
        PGobject payload = toJson(record.getPayload());
        try {
            int updated = jdbcTemplate.update("""
                            INSERT INTO event_logs
                            (event_id, workflow_id, type, timestamp, payload, tenant_id, seq, stream_id, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (event_id) DO NOTHING
                            """,
                    record.getEventId(),
                    record.getWorkflowId(),
                    record.getType(),
                    timestampTs,
                    payload,
                    record.getTenantId(),
                    seq,
                    record.getWorkflowId(),
                    createdAtTs
            );
            log.debug("事件日志入库, tenantId={}, workflowId={}, eventId={}, updated={}",
                    record.getTenantId(), record.getWorkflowId(), record.getEventId(), updated);
            return updated > 0;
        } catch (DataAccessException ex) {
            log.error("事件日志入库失败, tenantId={}, workflowId={}, eventId={}",
                    record.getTenantId(), record.getWorkflowId(), record.getEventId(), ex);
            return false;
        }
    }

    @Override
    public List<EventLogRecord> findByWorkflow(String tenantId, String workflowId) {
        try {
            return jdbcTemplate.query("""
                            SELECT event_id, workflow_id, type, timestamp, payload, tenant_id
                            FROM event_logs
                            WHERE tenant_id = ? AND workflow_id = ?
                            ORDER BY timestamp ASC
                            """,
                    new EventLogRowMapper(objectMapper),
                    tenantId,
                    workflowId
            );
        } catch (DataAccessException ex) {
            log.error("事件日志查询失败, tenantId={}, workflowId={}", tenantId, workflowId, ex);
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
            log.warn("事件日志 payload 序列化失败", ex);
            return null;
        }
    }

    private Long parseSeq(String eventId) {
        int index = eventId.lastIndexOf(':');
        if (index < 0 || index == eventId.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(eventId.substring(index + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static class EventLogRowMapper implements RowMapper<EventLogRecord> {

        private final ObjectMapper objectMapper;

        private EventLogRowMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public EventLogRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            EventLogRecord record = new EventLogRecord();
            record.setEventId(rs.getString("event_id"));
            record.setWorkflowId(rs.getString("workflow_id"));
            record.setType(rs.getString("type"));
            record.setTimestamp(rs.getTimestamp("timestamp").toInstant());
            record.setTenantId(rs.getString("tenant_id"));
            String payloadJson = rs.getString("payload");
            if (StringUtils.hasText(payloadJson)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
                    record.setPayload(payload);
                } catch (JsonProcessingException ex) {
                    record.setPayload(Collections.emptyMap());
                }
            }
            return record;
        }
    }
}
