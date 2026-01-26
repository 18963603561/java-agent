# Quickstart: 001-agent-core-spec

**Date**: 2026-01-25

## 运行前准备
- 安装 `Java 17`
- 准备 `PostgreSQL` 与 `Redis`
- 统一配置 `application.yml` 中的数据库、缓存、`SSE` 与预算策略

## 关键配置项
- `server.port`
- `spring.r2dbc.url` 或 `spring.datasource.url`
- `spring.data.redis.host` / `spring.data.redis.port`
- `agent.sse.timeoutSeconds`
- `agent.budget.enabled`

## 启动应用
- 使用 `Maven` 启动：`./mvnw spring-boot:run`

## 基础验证
- 提交任务：
  `curl -H "X-API-Key: <key>" -X POST http://localhost:8080/api/v1/tasks -d '{"query":"ping"}'`
- 订阅事件流：
  `curl -H "X-API-Key: <key>" http://localhost:8080/api/v1/stream/sse?workflow_id=<id>`
