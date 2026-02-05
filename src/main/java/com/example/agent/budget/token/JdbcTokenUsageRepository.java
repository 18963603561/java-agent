package com.example.agent.budget.token;

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

/**
 * PostgreSQL 预算记录仓储，实现预算持久化与查询。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "postgres")
public class JdbcTokenUsageRepository implements TokenUsageRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTokenUsageRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcTokenUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean saveIfAbsent(TokenUsageRecord record) {
        if (record == null || record.getUsageId() == null) {
            return false;
        }
        Instant createdAt = record.getCreatedAt() != null ? record.getCreatedAt() : Instant.now();
        Timestamp createdAtTs = Timestamp.from(createdAt);
        try {
            int updated = jdbcTemplate.update("""
                            INSERT INTO token_usage
                            (record_id, usage_id, task_id, agent_id, model, provider, input_tokens, output_tokens,
                             total_tokens, cost_usd, created_at, tenant_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (tenant_id, usage_id) DO NOTHING
                            """,
                    record.getRecordId(),
                    record.getUsageId(),
                    record.getTaskId(),
                    record.getAgentId(),
                    record.getModel(),
                    record.getProvider(),
                    record.getInputTokens(),
                    record.getOutputTokens(),
                    record.getTotalTokens(),
                    record.getCostUsd(),
                    createdAtTs,
                    record.getTenantId()
            );
            log.debug("预算记录入库, tenantId={}, usageId={}, updated={}",
                    record.getTenantId(), record.getUsageId(), updated);
            return updated > 0;
        } catch (DataAccessException ex) {
            log.error("预算记录入库失败, tenantId={}, usageId={}",
                    record.getTenantId(), record.getUsageId(), ex);
            return false;
        }
    }

    @Override
    public List<TokenUsageRecord> findByTask(String tenantId, String taskId) {
        try {
            return jdbcTemplate.query("""
                            SELECT record_id, usage_id, task_id, agent_id, model, provider, input_tokens, output_tokens,
                                   total_tokens, cost_usd, created_at, tenant_id
                            FROM token_usage
                            WHERE tenant_id = ? AND task_id = ?
                            ORDER BY created_at ASC
                            """,
                    new TokenUsageRowMapper(),
                    tenantId,
                    taskId
            );
        } catch (DataAccessException ex) {
            log.error("预算记录查询失败, tenantId={}, taskId={}", tenantId, taskId, ex);
            return Collections.emptyList();
        }
    }

    private static class TokenUsageRowMapper implements RowMapper<TokenUsageRecord> {

        @Override
        public TokenUsageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            TokenUsageRecord record = new TokenUsageRecord();
            record.setRecordId(rs.getString("record_id"));
            record.setUsageId(rs.getString("usage_id"));
            record.setTaskId(rs.getString("task_id"));
            record.setAgentId(rs.getString("agent_id"));
            record.setModel(rs.getString("model"));
            record.setProvider(rs.getString("provider"));
            record.setInputTokens(rs.getInt("input_tokens"));
            record.setOutputTokens(rs.getInt("output_tokens"));
            record.setTotalTokens(rs.getInt("total_tokens"));
            record.setCostUsd(rs.getDouble("cost_usd"));
            record.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            record.setTenantId(rs.getString("tenant_id"));
            return record;
        }
    }
}
