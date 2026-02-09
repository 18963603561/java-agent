# context 复评落地收口实施记录

## 1. 文档信息
- 记录时间：`2026-02-09 10:17`
- 关联评审：`doc/context-package-design-review-202602090937.md`
- 收口范围：`src/main/java/com/example/agent/capabilities/context` 及其被动依赖启动链路
- 收口目标：解决复评落地过程中出现的阻塞问题，并完成全流程回归验证

## 2. 本轮收口目标
- 修复影响应用上下文启动的核心阻塞问题。
- 确保复评改造后的代码可稳定通过测试门禁。
- 固化本轮实施过程、结论与后续建议，便于下一阶段复盘。

## 3. 问题现象
- 执行全量测试命令 `mvn -q -DskipTests=false test` 时失败。
- 失败表现为 `ApplicationContext` 启动失败并触发多用例连锁报错。
- 失败集中在网关控制器相关测试集，例如 `TaskControllerTest`、`SecurityValidationTest`、`RawResultControllerTest` 等。

## 4. 根因分析
- 关键根因位于 `EvidencePackService` 的构造注入语义。
- 该类存在多个构造器，但未显式指定 Spring 应使用的注入构造器。
- 容器创建 Bean 时未能正确选择可注入构造路径，导致服务实例化失败，并向上游依赖链扩散。
- 扩散链路表现为：`EvidencePackService` 创建失败，进而影响 `EvidencePackHookHandler`、`HookManager` 与控制器层 Bean 启动。

## 5. 本轮改造内容

### 5.1 构造注入修复
- 修改文件：`src/main/java/com/example/agent/capabilities/context/EvidencePackService.java`
- 改造动作：
  - 在单参主构造器 `EvidencePackService(MetricsPublisher metricsPublisher)` 上新增 `@Autowired`。
  - 保留双构造方案：
    - 生产路径使用主构造器并复用默认过期策略。
    - 测试路径可继续通过带 `expireMillis` 的构造器覆盖策略参数。

### 5.2 设计一致性说明
- 本次修复不引入历史兼容分支，不增加兜底逻辑。
- 维持当前新方案的单一语义：容器必须走明确、可观测、可预期的构造注入路径。
- 不改变 `EvidencePackService` 的领域行为，仅修复 Bean 生命周期管理问题。

## 6. 回归验证

### 6.1 定向验证
- 命令：`mvn -q -Dtest=TaskControllerTest -DskipTests=false test`
- 结果：通过。
- 结论：控制器链路的上下文加载已恢复正常。

### 6.2 全量验证
- 命令：`mvn -q -DskipTests=false test`
- 结果：通过。
- 结论：本轮收口未引入新的回归问题，阶段门禁满足要求。

## 7. 影响评估
- 对业务语义影响：无。
- 对运行时稳定性影响：正向提升，消除应用上下文初始化不确定性。
- 对可维护性影响：正向提升，构造注入入口更清晰，后续扩展风险降低。

## 8. 收口结论
- 本轮复评落地阻塞项已完成修复。
- 阶段性代码处于可测试、可运行、可继续迭代状态。
- 可进入下一步持续优化工作，包括更细粒度的职责拆分和结构治理。

## 9. 后续建议
- 在不改变当前语义的前提下，继续关注 `DefaultContextBuilder` 与 `EvidencePackService` 的复杂度收敛。
- 将同类多构造器 Bean 的注入规则纳入代码评审检查项，减少同类问题重复发生。
