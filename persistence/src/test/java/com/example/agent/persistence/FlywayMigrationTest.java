package com.example.agent.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移验证测试，确保核心表与索引存在。
 */
@Testcontainers
class FlywayMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("agent")
            .withUsername("agent")
            .withPassword("agent");

    @Test
    void migrateAndVerifySchema() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertTableExists(connection, "task_run");
            assertTableExists(connection, "task_step");
            assertTableExists(connection, "event_log");
            assertTableExists(connection, "audit_log");

            assertIndexExists(connection, "task_run", "idx_task_run_tenant_id");
            assertIndexExists(connection, "task_run", "idx_task_run_status");
            assertIndexExists(connection, "task_step", "idx_task_step_task_id");
            assertIndexExists(connection, "task_step", "idx_task_step_status");
            assertIndexExists(connection, "task_step", "idx_task_step_agent_id");
            assertIndexExists(connection, "event_log", "idx_event_log_task_id");
            assertIndexExists(connection, "event_log", "idx_event_log_tenant_id");
            assertIndexExists(connection, "event_log", "idx_event_log_timestamp");
            assertIndexExists(connection, "audit_log", "idx_audit_log_tenant_id");
            assertIndexExists(connection, "audit_log", "idx_audit_log_timestamp");
            assertIndexExists(connection, "audit_log", "idx_audit_log_action");
        }
    }

    private void assertTableExists(Connection connection, String tableName) throws SQLException {
        String sql = "select 1 from information_schema.tables where table_schema = 'public' and table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "表不存在: " + tableName);
            }
        }
    }

    private void assertIndexExists(Connection connection, String tableName, String indexName) throws SQLException {
        String sql = "select 1 from pg_indexes where schemaname = 'public' and tablename = ? and indexname = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "索引不存在: " + indexName);
            }
        }
    }
}
