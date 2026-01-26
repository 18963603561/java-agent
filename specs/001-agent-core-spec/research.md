# Research Findings: 001-agent-core-spec

**Date**: 2026-01-25
**Spec**: `F:\ai-code\java-agent\specs\001-agent-core-spec\spec.md`

## Decision 1: 构建与依赖管理
- Decision: 采用 `Spring Boot` 官方 `Starter` 体系进行依赖管理，并以 `Maven` 作为默认构建工具
- Rationale: 统一版本管理、降低冲突风险，符合 `Spring` 生态优先约束
- Alternatives considered: `Gradle`、手动依赖管理

## Decision 2: 数据访问方式
- Decision: 采用 `Spring Data JDBC` + `Spring Transaction`
- Rationale: 模型清晰、避免代理复杂度，适合模块化分层与事件记录场景
- Alternatives considered: `Spring Data JPA`、`Spring JDBC`

## Decision 3: 内部事件总线
- Decision: 采用 `Spring ApplicationEvent` + `Reactor Sinks` 作为内部事件分发机制
- Rationale: 与 `WebFlux`/`Reactor` 兼容，低耦合、易观测
- Alternatives considered: `Spring Messaging`、外部 `MQ`

## Decision 4: `SSE` 流式输出
- Decision: 采用 `Spring WebFlux` 的 `ServerSentEvent` + `Flux`
- Rationale: 与 `SSE` 规范匹配，支持断线续传与背压
- Alternatives considered: 直接 `Netty` 输出、`WebSocket`

## Decision 5: 事件持久化与缓存
- Decision: `Redis Stream` 保存全量事件并设置 `TTL`，关键事件写入 `PostgreSQL`
- Rationale: 满足实时订阅与历史审计的双重要求
- Alternatives considered: `Kafka`、仅数据库持久化

## Decision 6: 记忆与向量存储
- Decision: 保持 `VectorStore` 接口抽象，具体实现按需接入
- Rationale: 保障实现可替换与兼容边界清晰
- Alternatives considered: 直接绑定单一向量引擎

## Decision 7: 调度框架
- Decision: 基于 `Spring Scheduling`，复杂需求再引入 `Quartz`
- Rationale: 与 `Spring Boot` 集成简单，满足 `MVP` 需求
- Alternatives considered: 自研调度器

## Decision 8: 鉴权与多租户
- Decision: 使用 `Spring Security` 过滤链实现 `API Key` 鉴权，并保留 `JWT` 扩展点
- Rationale: 复用生态能力，降低安全实现风险
- Alternatives considered: 自研鉴权过滤器

## Decision 9: 可观测性
- Decision: `Micrometer` + `Spring Boot Actuator` 作为指标与健康检查基础
- Rationale: 与宪章一致，便于后续接入 `OpenTelemetry`
- Alternatives considered: 直接使用 `OpenTelemetry` SDK
