# 上下文剪枝阶段无显式开关的原因分析与方案

## 1. 背景问题
- `DefaultContextBuilder.build(...)` 中包含：快照生成 → 剪枝（prune）→ 裁剪（trim）→ 压缩（compress）。
- `application.yml` 中可看到：
  - `agent.context.budget.enabled`（预算与裁剪相关）
  - `agent.context.compression.enabled`（压缩开关）
- 但剪枝阶段没有独立的 `enabled` 配置。

## 2. 剪枝阶段当前触发条件
代码位置：`src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- 剪枝执行条件：
  - `contextPruner != null` 且 `allocation != null`
- `allocation` 来自 `ContextBudgetAllocator`，只有在 `agent.context.budget.enabled=true` 时才会生成。
- 因此剪枝逻辑实际是“跟随预算开关”的：预算关闭时，剪枝与裁剪都会跳过。

## 3. 为什么没有独立开关（可能设计原因）
1) **剪枝属于轻量级处理**
   - `DefaultContextPruner` 主要是列表裁剪、摘要截断与统计，计算复杂度低。
   - 相比压缩（可能触发存储与摘要写入），剪枝成本更低。

2) **剪枝是策略驱动的“安全护栏”**
   - 剪枝依赖 `ContextPolicy` 中的 `maxEvidenceCount`、`maxMemoryCount`、`pruneOrder`。
   - 如果没有策略或限制为 0，很多剪枝操作天然不生效。
   - 设计上通过“策略为空即不生效”达到“隐式关闭”的效果。

3) **剪枝与预算耦合**
   - 剪枝中涉及工作记忆摘要裁剪，使用 `allocation` 中的分段预算。
   - 预算关闭时直接跳过剪枝，避免无预算判断造成不一致。

## 4. 现状可能带来的困惑
- 从配置视角看，裁剪与压缩有显式开关，但剪枝没有，容易误解为“无法关闭”。
- 实际上剪枝受预算开关与策略数据共同影响，但缺少显式说明。

## 5. 方案建议
### 方案 A（推荐）：新增剪枝开关
- 新增配置：`agent.context.prune.enabled`，默认 `true`。
- 在 `DefaultContextBuilder.build(...)` 中增加判断：
  - 若 `pruneEnabled=false` 则跳过 `contextPruner.prune(...)`。
- 优点：语义清晰、与裁剪/压缩一致。

### 方案 B：条件装配剪枝器
- 在 `DefaultContextPruner` 上添加 `@ConditionalOnProperty`。
- 配置示例：
  - `agent.context.prune.enabled=false` 时不注入 `ContextPruner` Bean。
- 优点：侵入低；缺点：依赖 Spring 条件装配，可能影响测试。

### 方案 C：策略层关闭（无需代码改动）
- 避免在 `ContextPolicy` 中设置 `maxEvidenceCount/maxMemoryCount/pruneOrder`。
- 将预算关闭（`agent.context.budget.enabled=false`）也会间接关闭剪枝。
- 优点：无代码改动；缺点：不直观。

## 6. 性能影响判断
- 剪枝为 O(n) 的列表处理，通常开销低于裁剪与压缩。
- 若运行时上下文中引用、记忆、证据数量极大，剪枝仍可能产生一定成本，但总体可控。
- 压缩阶段涉及存储与摘要生成，性能敏感度更高，因此需要独立开关。

## 7. 建议结论
- 若希望配置一致性与可控性，建议采用“方案 A”新增剪枝开关。
- 若希望快速关闭且可接受预算关闭的副作用，可使用“方案 C”。

## 8. 关联文件
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/budget/DefaultContextPruner.java`
- `src/main/java/com/example/agent/budget/ContextBudgetProperties.java`
- `src/main/java/com/example/agent/budget/ContextCompressionProperties.java`
- `src/main/resources/application.yml`
