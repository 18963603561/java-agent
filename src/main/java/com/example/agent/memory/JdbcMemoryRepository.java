package com.example.agent.memory;

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
import org.springframework.util.StringUtils;

/**
 * PostgreSQL 记忆仓储，实现记忆持久化与查询。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "postgres")
public class JdbcMemoryRepository implements MemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MemoryRecord save(MemoryRecord record) {
        if (record == null || !StringUtils.hasText(record.getMemoryId())) {
            return null;
        }
        Instant createdAt = record.getCreatedAt() != null ? record.getCreatedAt() : Instant.now();
        try {
            jdbcTemplate.update("""
                            INSERT INTO memory_records
                            (memory_id, session_id, task_id, content, summary, embedding_ref, tenant_id, layer, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (memory_id) DO UPDATE SET
                              session_id = EXCLUDED.session_id,
                              task_id = EXCLUDED.task_id,
                              content = EXCLUDED.content,
                              summary = EXCLUDED.summary,
                              embedding_ref = EXCLUDED.embedding_ref,
                              tenant_id = EXCLUDED.tenant_id,
                              layer = EXCLUDED.layer,
                              created_at = EXCLUDED.created_at
                            """,
                    record.getMemoryId(),
                    record.getSessionId(),
                    record.getTaskId(),
                    record.getContent(),
                    record.getSummary(),
                    record.getEmbeddingRef(),
                    record.getTenantId(),
                    record.getLayer(),
                    createdAt
            );
            log.debug("记忆入库, tenantId={}, memoryId={}", record.getTenantId(), record.getMemoryId());
        } catch (DataAccessException ex) {
            log.error("记忆入库失败, tenantId={}, memoryId={}", record.getTenantId(), record.getMemoryId(), ex);
        }
        return record;
    }

    @Override
    public List<MemoryRecord> findBySession(String tenantId, String sessionId) {
        try {
            return jdbcTemplate.query("""
                            SELECT memory_id, session_id, task_id, content, summary, embedding_ref, tenant_id, layer, created_at
                            FROM memory_records
                            WHERE tenant_id = ? AND session_id = ?
                            ORDER BY created_at ASC
                            """,
                    new MemoryRowMapper(),
                    tenantId,
                    sessionId
            );
        } catch (DataAccessException ex) {
            log.error("记忆查询失败, tenantId={}, sessionId={}", tenantId, sessionId, ex);
            return Collections.emptyList();
        }
    }

    @Override
    public List<MemoryRecord> search(String tenantId, String sessionId, String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        try {
            String like = "%" + query + "%";
            return jdbcTemplate.query("""
                            SELECT memory_id, session_id, task_id, content, summary, embedding_ref, tenant_id, layer, created_at
                            FROM memory_records
                            WHERE tenant_id = ? AND session_id = ?
                              AND (content ILIKE ? OR summary ILIKE ?)
                            ORDER BY created_at DESC
                            LIMIT ?
                            """,
                    new MemoryRowMapper(),
                    tenantId,
                    sessionId,
                    like,
                    like,
                    limit
            );
        } catch (DataAccessException ex) {
            log.error("记忆搜索失败, tenantId={}, sessionId={}", tenantId, sessionId, ex);
            return Collections.emptyList();
        }
    }

    private static class MemoryRowMapper implements RowMapper<MemoryRecord> {

        @Override
        public MemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            MemoryRecord record = new MemoryRecord();
            record.setMemoryId(rs.getString("memory_id"));
            record.setSessionId(rs.getString("session_id"));
            record.setTaskId(rs.getString("task_id"));
            record.setContent(rs.getString("content"));
            record.setSummary(rs.getString("summary"));
            record.setEmbeddingRef(rs.getString("embedding_ref"));
            record.setTenantId(rs.getString("tenant_id"));
            record.setLayer(rs.getString("layer"));
            if (rs.getTimestamp("created_at") != null) {
                record.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            }
            return record;
        }
    }
}
