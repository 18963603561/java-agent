# `runtime` 包目录整理建议

- 生成时间：2026-02-06 09:10
- 范围：`src/main/java/com/example/agent/runtime`

## 1. 现状结论

- 当前 `engine` 包承载过多职责（门面编排、步骤执行器、`ReAct` 领域对象、步骤记录仓储与运行时服务混杂）。
- `engine` 内既有接口也有实现，且跨子域依赖密集，后续继续演进会放大回归面与认知负担。

### 1.1 当前子包类数量（按目录统计）

| 目录 | Java 文件数 |
|---|---:|
| `src/main/java/com/example/agent/runtime/engine` | 33 |
| `src/main/java/com/example/agent/runtime/model` | 10 |
| `src/main/java/com/example/agent/runtime/control` | 7 |
| `src/main/java/com/example/agent/runtime/structured` | 7 |
| `src/main/java/com/example/agent/runtime/recovery` | 5 |
| `src/main/java/com/example/agent/runtime/raw` | 4 |
| `src/main/java/com/example/agent/runtime/output` | 2 |
| `src/main/java/com/example/agent/runtime/prepare` | 2 |
| `src/main/java/com/example/agent/runtime/summary` | 2 |
| `src/main/java/com/example/agent/runtime/codec` | 1 |
| `src/main/java/com/example/agent/runtime/finalize` | 1 |

## 2. 是否需要调整包目录

- 结论：建议调整，优先治理 `engine`。
- 原因：让 `engine` 回归“门面/编排”；其余职责下沉到可独立测试的组件包，符合“门面 + 组件”结构。

## 3. 建议的目标包结构（聚焦将 `engine` 拆分）

- `com.example.agent.runtime.engine`：仅保留门面编排与结果对象。
- `com.example.agent.runtime.step`：步骤运行时（记录、状态机、执行请求/委托）。
- `com.example.agent.runtime.step.executor`：步骤执行器与路由。
- `com.example.agent.runtime.step.repository`：步骤记录仓储接口与实现。
- `com.example.agent.runtime.react`：`ReAct` 循环与相关领域对象。
- `com.example.agent.runtime.llm`：`LLM` 步骤服务。
- `com.example.agent.runtime.recovery`：恢复策略与恢复服务。
- `com.example.agent.runtime.control`：审批/暂停/恢复/取消门禁与审批请求 DTO。

## 4. 迁移清单（CSV）

- 迁移映射表：`doc/runtime-package-migration-202602060908.csv`

## 5. 迁移实施建议（避免一次性大搬家）

- 建议按子域拆分为多个 PR：先迁移 `ApprovalDecisionRequest` 与 `StepFailureRecoveryService`，再迁移 `react`，再迁移 `step` 与 `executor/repository`，最后迁移 `llm`。
- 每个 PR 只做“移动包 + `import` 更新 + 通过 `mvn test`”，不改行为逻辑。

