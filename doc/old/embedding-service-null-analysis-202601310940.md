# EmbeddingService 为空原因分析与解决方案

生成时间: 2026-01-31 09:40

## 1. 现象描述
- 在 `MemoryStore` 中通过 `embeddingServiceProvider.getIfAvailable()` 获取到 `EmbeddingService` 为 `null`
- 对应日志会出现“嵌入服务不可用，跳过向量写入”

## 2. 直接原因（基于代码）
- `EmbeddingService` 通过 `ObjectProvider` 可选注入，若容器中没有 `EmbeddingService` Bean，则返回 `null`
- 仅有的实现是 `HashEmbeddingService`，它是 `@Component` 且 `@ConditionalOnMissingBean(EmbeddingService.class)`
- 因此 **当 `HashEmbeddingService` 未被 Spring 扫描注册时**，`EmbeddingService` 会为空

证据位置:
- `MemoryStore`：`src/main/java/com/example/agent/memory/MemoryStore.java`
- `EmbeddingService`：`src/main/java/com/example/agent/memory/EmbeddingService.java`
- `HashEmbeddingService`：`src/main/java/com/example/agent/memory/HashEmbeddingService.java`

## 3. 常见触发场景
1) **非 Spring 容器创建**
- 代码中通过 `new MemoryStore(...)` 手工创建，未进入 Spring 管理
- 单元测试中明确传入 `FixedObjectProvider<>(null)`，导致必然为空
- 证据：`src/test/java/com/example/agent/memory/MemoryStoreTest.java`

2) **Spring 扫描范围不包含 `com.example.agent.memory`**
- 若启动类不在 `com.example.agent` 包或使用了自定义 `@ComponentScan` 限制范围
- `HashEmbeddingService` 未注册，导致 `EmbeddingService` 不存在

3) **启动的不是 `AgentApplication`**
- 使用其他启动入口且包路径不涵盖 `com.example.agent.memory`

## 4. 排查路径（建议顺序）
1) 在运行时确认 Spring 是否注册了 `EmbeddingService` Bean
- 可通过 `ApplicationContext` 或 Actuator 的 Beans 端点验证

2) 确认启动类的扫描范围
- `AgentApplication` 在 `com.example.agent` 包下，默认可扫描 `memory` 子包

3) 若是测试或手工构造
- 检查是否使用了 `new MemoryStore(...)` 且传入了空的 `ObjectProvider`

## 5. 让 `EmbeddingService` 不为空的方案

### 方案 A（推荐）: 保证 `HashEmbeddingService` 被 Spring 扫描注册
适用场景: 生产运行或 Spring 集成测试
- 使用 `AgentApplication` 启动
- 保证扫描范围包含 `com.example.agent.memory`
- 不要在 `@ComponentScan` 中排除该包

### 方案 B: 显式声明 `EmbeddingService` Bean
适用场景: 自定义启动类或非默认扫描范围
- 在配置类中手动 `@Bean` 注册 `EmbeddingService`
- 可直接返回 `HashEmbeddingService`

### 方案 C: 测试中提供 `EmbeddingService` 实例
适用场景: 单元测试或手工构造
- 构造 `ObjectProvider` 时传入非空实例
- 或使用 Spring 测试上下文注入真实 Bean

### 方案 D: 使用外部嵌入服务实现
适用场景: 生产环境需要真实向量
- 自行实现 `EmbeddingService` 并标注 `@Component` 或 `@Bean`
- 由于 `@ConditionalOnMissingBean`，自定义实现会优先生效

## 6. 建议优先级
1) 先确认是否非 Spring 管理（最常见）
2) 再检查启动类扫描范围
3) 最后考虑显式注册或自定义实现

## 7. 关于“乱码”说明
- 之前的 `doc/embedding-service-null-analysis-202601301947.md` 极可能不是 UTF-8 无 BOM 写入
- 本文档使用 UTF-8 无 BOM 重新生成，可直接替代旧文档
