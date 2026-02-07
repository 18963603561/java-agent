# 语义向量记忆使用方案（含 Embedding 不可用回退 Hash）

## 依据与前提
- 依据文档：
  - doc/semantic-memory-embedding-null-analysis-202601301119.md
  - doc/semantic-memory-embedding-api-support-202601301443.md
- 语义检索入口：SemanticMemoryStore.search(...)
- 语义检索生效的必要条件：VectorStore 启用 + EmbeddingService 可用

## 使用方案
### 方案一：仅使用 HashEmbeddingService（默认兜底）
- 适用场景：不对接外部模型 API，或外部不可用时稳定运行
- 配置建议：
  - agent.memory.vector.enabled=true（需要向量库支持检索）
  - agent.memory.embedding.enabled=false（或不配置该项）
- 说明：HashEmbeddingService 为默认实现，不依赖外部 API

### 方案二：启用模型 API Embedding
- 适用场景：需要真实语义向量
- 配置要点：
  - agent.memory.vector.enabled=true
  - agent.memory.embedding.enabled=true
  - agent.memory.embedding.base-url / api-key / model 等配置齐备
  - agent.memory.embedding.dimension 与 agent.memory.vector.dimension 保持一致
- 示例：
```yaml
agent:
  memory:
    vector:
      enabled: true
      dimension: 1024
    embedding:
      enabled: true
      provider: custom
      base-url: https://api.example.com
      api-key: xxx
      model: text-embedding-001
      timeout-seconds: 10
      dimension: 1024
```

### 方案三：Embedding 不可用时回退 Hash（推荐落地）
- 目标：外部模型不可用时，语义检索仍可用（质量降低但不中断）
- 回退策略（两种层级，至少落地一种）：
  1) 配置层回退（立即可用）
     - 发生模型不可用时，将 agent.memory.embedding.enabled 改为 false 并重启
     - 由 HashEmbeddingService 接管
  2) 运行时自动回退（建议实现）
     - 在 ApiEmbeddingService 内部捕获异常
     - 记录 warn/error 日志，携带请求上下文
     - 调用 HashEmbeddingService 生成向量并继续流程
- 运行时回退实现建议：
  - 新增 FallbackEmbeddingService（包装 ApiEmbeddingService + HashEmbeddingService）
  - 或在 ApiEmbeddingService 中注入 HashEmbeddingService 作为兜底
  - 增加配置开关：agent.memory.embedding.fallback-to-hash（默认 true）

## 日志与可观测性要求
- 外部 API 调用需记录 info / warn / error 日志
- 关键路径需记录：开始、成功、失败、回退
- 异常必须打印堆栈与关键上下文（模型、超时、请求标识）

## 需要同步修改的文档
- doc/semantic-memory-embedding-api-support-202601301443.md
  - 增加“Embedding 不可用时回退 Hash”的策略说明与配置示例
- doc/semantic-memory-embedding-null-analysis-202601301119.md
  - 补充“Embedding 可用但 API 失败”的场景与排查点
- doc/（新增）
  - 本文档作为统一使用方案说明

## 风险提示
- 向量维度不一致会导致写入或检索失败
- API 超时会影响检索时延，需配置合理超时与告警
- 回退 Hash 会降低语义质量，需在监控中标记使用率

## 结论
- 默认使用 HashEmbeddingService 可保证稳定性
- 需要语义质量时启用模型 API
- 外部不可用时应具备回退 Hash 能力，确保业务不中断