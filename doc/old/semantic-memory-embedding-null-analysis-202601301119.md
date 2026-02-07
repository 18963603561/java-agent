# SemanticMemoryStore.search 中 EmbeddingService 为空的原因分析

## 结论摘要
- 当前项目内置的 `EmbeddingService` 实现为 `HashEmbeddingService`，属于本地哈希向量兜底，不依赖外部 API。
- 代码中没有配置外部 embedding API 的能力与配置项；如需真实向量，需要新增实现与配置。
- 如果 `EmbeddingService` 在运行时为空，通常是因为 **Spring 上下文没有加载该 Bean**（组件扫描被裁剪、测试切片、手工 new 对象等），而不是配置项关闭。

## 现象与触发点
- 文件：`src/main/java/com/example/agent/memory/SemanticMemoryStore.java`
- 逻辑：`EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();`
- 行为：当 `embeddingService` 为空时，方法直接返回空列表。

## 当前 EmbeddingService 实现
- 接口：`src/main/java/com/example/agent/memory/EmbeddingService.java`
- 默认实现：`src/main/java/com/example/agent/memory/HashEmbeddingService.java`
  - 注解：`@Component` + `@ConditionalOnMissingBean(EmbeddingService.class)`
  - 作用：生成简易哈希向量，作为无外部模型时的兜底
  - 依赖：`MemoryVectorProperties`（向量维度等参数）
  - 外部依赖：无

## 为什么可能为空
1) 组件未被扫描
- 使用自定义 `@ComponentScan` 或测试切片时只扫描部分包，导致 `HashEmbeddingService` 未注册。

2) 运行在测试切片或非完整 Spring Boot 上下文
- 使用 `@WebFluxTest`、`@DataJpaTest` 等切片测试，默认不加载 `HashEmbeddingService`。
- 手工 `new SemanticMemoryStore(...)`，未经过 Spring 容器管理时，`ObjectProvider` 为空。

3) Bean 创建失败导致未注册
- `HashEmbeddingService` 依赖 `MemoryVectorProperties`，若该 Bean 未创建或绑定失败，会导致 `HashEmbeddingService` 无法实例化。
- 这种情况通常会在启动日志中出现 Bean 创建异常。

4) 自定义 EmbeddingService 配置被排除
- 若项目后续新增自定义 `EmbeddingService` 并通过条件注解/配置禁用，但同时排除了 `HashEmbeddingService`，会导致空值。

## 是否需要配置 embedding API
- 当前实现 **不需要** 外部 embedding API。
- 若要对接真实向量模型，需要新增新的 `EmbeddingService` 实现，并提供对应配置项（如 `apiKey`、`baseUrl`、`model`）。
- 当前 `application.yml` 仅提供 `agent.memory.vector.*` 配置用于向量库连接，不包含 embedding API 配置。

## 相关配置说明
- `agent.memory.vector.enabled`：控制 `VectorStore`（如 `QdrantVectorStore`）是否启用。
- 与 `EmbeddingService` 无直接开关关系。
- 即使 `EmbeddingService` 存在，若 `VectorStore` 未启用或不可用，语义检索仍会返回空。

## 建议排查步骤
1) 确认 Bean 是否存在
- 通过启动日志或 `ApplicationContext` 打印 `EmbeddingService` Bean 列表。

2) 确认是否运行在测试切片
- 检查测试注解是否限制了扫描范围。

3) 检查 `HashEmbeddingService` 是否被排除
- 搜索是否有 `@ComponentScan` 排除 `com.example.agent.memory` 包。

4) 检查 `MemoryVectorProperties` 是否成功加载
- 查看配置绑定日志或启动异常。

## 结论
- 目前 `EmbeddingService` 的默认实现是 `HashEmbeddingService`，不需要配置 embedding API。
- 若出现空值，优先排查 Spring 上下文是否完整加载了内存模块相关 Bean。
- 如需真实向量能力，应新增 `EmbeddingService` 实现与对应配置项。