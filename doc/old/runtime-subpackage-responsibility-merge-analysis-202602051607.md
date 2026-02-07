# runtime 包按职责合并子包分析（2026-02-05）

## 1. 分析范围
本分析面向 `src/main/java/com/example/agent/runtime` 下所有 Java 文件，目标是给出“按职责合并子包”的可执行方案，不直接改代码。

分析时点：`2026-02-05 16:07`

## 2. 当前结构现状
### 2.1 子包数量与规模
- `(root)`：34 个类，职责混合最严重
- `codec`：1 个类
- `model/plan`：2 个类
- `model/result`：8 个类
- `raw`：3 个类
- `structured`：7 个类
- `io`：0 个类（空目录）

### 2.2 关键职责观察
- `model/plan`：计划阶段模型（`StepSpec`、`StepPolicy`）
- `model/result`：步骤输出模型（`StepResult*`）
- `raw`：原始结果引用与存储接口（`RawRef`、`RawResultStore`）
- `structured`：结构化结果模型与提取器（`StructuredResult*`、`StructuredExtractor*`）
- `codec`：`StepResultJsonCodec`，仅服务序列化边界
- `io`：无落盘代码，可直接清理

## 3. 当前耦合与职责冲突
### 3.1 模型边界被拆得过细
- `StepResult` 直接依赖 `RawRef` 和 `StructuredResult`，说明 `model/result`、`raw`、`structured` 在语义上属于同一结果模型簇。
- `StepRuntimeService` 需要同时 import `model/result + raw + structured`，服务层需要跨多个子包拼装同一个“步骤输出对象”。

### 3.2 反向依赖存在
- `StepResultMeta`（位于 `model/result`）依赖 `runtime` 根包中的 `StepState`，形成“模型层反向依赖根包”的方向问题。

### 3.3 子包粒度与职责不匹配
- `codec` 只有 1 个类，且主要被 `JdbcStepRecordRepository` 使用，更像存储边界细节，不适合作为独立顶级子包。
- `io` 空目录已无承载价值。

### 3.4 对外影响点
在 `runtime` 包外部，引用最频繁的是：
- `StepSpec`（5 处）
- `StepRecord`（3 处）
- `RuntimeResult`（3 处）
- `RawRef` / `RawResultStore`（各 2 处）

这意味着模型类迁移会影响规划、编排、工具执行、模型调用等链路，需要分阶段改 import。

## 4. 合并原则
- 以“领域语义完整性”优先，而不是按技术动作拆碎。
- 同一语义对象只在一个包簇维护，避免服务层跨包拼装。
- “模型”“转换提取”“存储边界”三类职责明确分层。
- 对外高频类型优先保持稳定命名，先迁包再渐进收敛。

## 5. 推荐目标结构（建议采用）

```text
com.example.agent.runtime
  model/                 # 运行时核心领域模型
  transform/             # 结构化提取与结果转换
  store/                 # 存储边界与编解码
```

### 5.1 `model`（合并结果模型簇）
建议并入 `model`：
- 计划模型：`StepSpec`、`StepPolicy`
- 结果模型：`StepResult`、`StepResultMeta`、`StepResultRaw`、`StepResultSummary`、`StepResultDigest`、`StepResultRefSet`、`StepResultTiming`、`StepResultError`
- 原始结果引用模型：`RawRef`
- 结构化结果模型：`StructuredResult`、`StructuredRefs`、`StructuredQuality`、`ResultKind`
- 状态枚举：`StepState`（从根包迁入）

说明：这一步是本次“职责合并”的核心收益点，能显著降低 `StepRuntimeService` 的跨包拼装复杂度。

### 5.2 `transform`（合并提取与映射逻辑）
建议放入 `transform`：
- `StructuredExtractor`
- `GenericStructuredExtractor`
- `StructuredExtractorRegistry`
- 可选并入：`RawOutputEnvelopeBuilder`、`StepOutputSummaryBuilder`（都属于结果整形/摘要转换能力）

### 5.3 `store`（合并存储边界）
建议放入 `store`：
- `RawResultStore`
- `InMemoryRawResultStore`
- `StepResultJsonCodec`（从 `codec` 并入）

可选扩展：后续把 `StepRecordRepository` 系列统一归到 `store/record`，但可放到第二阶段执行。

## 6. 类迁移映射（建议版）
- `runtime/model/plan/*` -> `runtime/model/*`
- `runtime/model/result/*` -> `runtime/model/*`
- `runtime/raw/RawRef` -> `runtime/model/RawRef`
- `runtime/structured/StructuredResult` -> `runtime/model/StructuredResult`
- `runtime/structured/StructuredRefs` -> `runtime/model/StructuredRefs`
- `runtime/structured/StructuredQuality` -> `runtime/model/StructuredQuality`
- `runtime/structured/ResultKind` -> `runtime/model/ResultKind`
- `runtime/structured/StructuredExtractor*` -> `runtime/transform/*`
- `runtime/raw/RawResultStore` -> `runtime/store/RawResultStore`
- `runtime/raw/InMemoryRawResultStore` -> `runtime/store/InMemoryRawResultStore`
- `runtime/codec/StepResultJsonCodec` -> `runtime/store/StepResultJsonCodec`
- `runtime/io` -> 删除空目录

## 7. 分阶段落地顺序
### 阶段一：模型收敛（低风险高收益）
- 先迁 `model/plan + model/result + RawRef + StructuredResult* + StepState`
- 批量修复 import
- 编译验证 `runtime + planning + reflection + orchestrator + gateway` 相关模块

### 阶段二：转换层与存储层合并
- 迁 `StructuredExtractor*` 到 `transform`
- 迁 `RawResultStore*` 与 `StepResultJsonCodec` 到 `store`
- 修复 `ToolExecutor`、`ModelInvocationService`、`JdbcStepRecordRepository` 相关引用

### 阶段三：清理与稳态
- 删除空包 `io`
- 清理旧包名残留 import
- 补充回归测试，重点覆盖 `StepRuntimeService` 和 `JdbcStepRecordRepository`

## 8. 风险与规避
- 风险：迁包引发大面积 import 冲突
  - 规避：按阶段迁移，单阶段只处理一类职责，不跨阶段混改
- 风险：`StepState` 迁移导致序列化或枚举解析差异
  - 规避：保持枚举名不变，仅迁包，保留同样的字符串值
- 风险：`RawRef`/`RawResultStore` 被外部模块依赖
  - 规避：先迁 `RawRef`，`RawResultStore` 在第二阶段再迁，减少一次性冲击

## 9. 建议结论
建议按“`model` -> `transform` -> `store`”三段式合并执行。

原因：
- 当前主要复杂度来自结果模型跨 `model/result + raw + structured` 分散。
- 先收敛模型可立即降低服务层复杂度，并减少后续迁移的重复修改。
- 存储边界最后收敛，可避免过早影响外部调用链。

若你确认采用该方案，我可以下一步直接给出第一阶段的详细改包清单（到文件级别）与提交拆分建议。