# 语义向量记忆支持方案（通过配置模型 API）

## 目标与范围
- 目标：说明如何在现有代码基础上，通过配置模型 API 来提供真实向量嵌入，从而支持语义向量记忆。
- 范围：基于当前 `EmbeddingService` / `VectorStore` 的设计提出实现方案与配置项，不包含具体第三方模型 API 的厂商细节。

## 现状梳理
- 语义检索入口：`SemanticMemoryStore.search(...)`。
- 触发条件：`VectorStore` 与 `EmbeddingService` 必须同时可用，否则直接返回空结果。
- 当前默认嵌入实现：`HashEmbeddingService`（`@ConditionalOnMissingBean`）。
- 向量存储实现：`QdrantVectorStore`，通过 `agent.memory.vector.enabled` 控制启用。

## 支持向量记忆的必要条件
1. 启用向量存储：`agent.memory.vector.enabled=true`。
2. 提供可用嵌入服务：`EmbeddingService` Bean 存在且可调用。
3. 嵌入维度一致：向量模型输出维度需与 `agent.memory.vector.dimension` 一致。

## 通过配置模型 API 的实现方案
### 1) 新增配置项（建议）
新增 `agent.memory.embedding.*` 配置，用于控制外部模型 API：
- `agent.memory.embedding.enabled`：是否启用模型 API 嵌入服务
- `agent.memory.embedding.provider`：提供方标识（用于区分不同实现）
- `agent.memory.embedding.base-url`：模型 API 地址
- `agent.memory.embedding.api-key`：鉴权密钥
- `agent.memory.embedding.model`：模型名称
- `agent.memory.embedding.timeout-seconds`：超时秒数
- `agent.memory.embedding.dimension`：模型输出维度（用于校验与提示）

### 2) 新增配置类
- 新增 `MemoryEmbeddingProperties`（`@ConfigurationProperties(prefix = "agent.memory.embedding")`）。
- 负责读取上面的配置项。

### 3) 新增 EmbeddingService 实现
- 新增 `ApiEmbeddingService implements EmbeddingService`：
  - 使用 `WebClient` 调用模型 API。
  - 将文本转换为向量（`List<Float>`）。
  - 出错时记录错误日志并返回空或抛出异常，由调用方兜底。
- 增加条件装配：
  - `@ConditionalOnProperty(prefix = "agent.memory.embedding", name = "enabled", havingValue = "true")`
  - 这样在开启配置时自动替代 `HashEmbeddingService`。

### 4) 维度一致性与校验
- `QdrantVectorStore` 集合创建使用 `agent.memory.vector.dimension`。
- 若模型向量维度与集合维度不一致，可能导致写入或检索失败。
- 建议：
  - 在 `ApiEmbeddingService` 初始化时校验 `embedding.dimension` 与 `vector.dimension` 一致，若不一致则打印 `error` 日志并拒绝启动。

### 5) 应用层触发路径保持不变
- `MemoryStore.save(...)` 仍负责：保存记忆 + 嵌入向量写入 `VectorStore`。
- `SemanticMemoryStore.search(...)` 仍负责：嵌入查询 + 向量检索。
- 因此只需引入新的 `EmbeddingService` 实现，无需改动调用方逻辑。

## 需要新增/调整的代码位置（建议清单）
- 新增：`src/main/java/com/example/agent/memory/MemoryEmbeddingProperties.java`
- 新增：`src/main/java/com/example/agent/memory/ApiEmbeddingService.java`
- 可选：在配置类中增加维度一致性校验逻辑。
- 更新配置：`src/main/resources/application.yml` 增加 `agent.memory.embedding` 配置块。

## 参考配置示例（示意）
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

## 风险与注意事项
- 外部 API 不可用时：语义检索会回退为空结果，建议加 `warn/error` 日志并做指标监控。
- 超时与重试：建议设置合理超时与可配置的重试策略，避免影响主流程。
- 向量存储依赖：模型 API 正常但 `VectorStore` 未启用时，语义检索仍为空。

## 结论
- 当前项目已具备向量检索框架，只缺少“可配置的真实嵌入服务”。
- 通过新增 `agent.memory.embedding.*` 配置与 `ApiEmbeddingService` 实现，即可支持“通过配置模型 API”方式启用向量记忆。