# context 包第一阶段改造任务清单（执行版）

## 1. 阶段目标
- 目标周期：`1` 周内
- 目标性质：先修复语义风险与可观测性短板，不做大规模架构拆分
- 完成标准：
  - `ResearchPipeline` 对外行为一致且可用
  - 研究解析失败路径可观测
  - `EvidencePackService` 具备最小可控回收能力

## 2. 改造范围
- `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java`
- `src/main/java/com/example/agent/capabilities/context/EvidencePackService.java`
- 相关测试目录：
  - `src/test/java/com/example/agent/research/`
  - `src/test/java/com/example/agent/context/`

## 3. 任务清单与执行状态

### T1 修复 `run(String query)` 空实现（高优先级）
- 状态：**已完成**
- 实际落地：
  - `run(String query)` 已统一委托到 `runWithRawRef(query, null, null, null)`。
  - 删除了“固定返回空列表”的历史实现。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java`

### T2 增强研究解析失败日志（高优先级）
- 状态：**已完成**
- 实际落地：
  - 在解析输入为空、结构缺失、JSON 解析异常、修复上下文序列化失败、修复输出为空等场景增加了 `warn` 日志。
  - 日志统一包含 `workflowId`、`queryLength`、`stage` 等关键上下文。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java`

### T3 统一研究流水线兜底语义（高优先级）
- 状态：**已完成**
- 实际落地：
  - 新增统一解析结果对象 `ParseOutcome`，统一承载 `parseErrorType/repairAttempted/repairSuccess`。
  - 解析失败后统一进入 `buildFallbackCitations`，保证返回结构稳定。
  - `recordPromptTrace` 与日志使用同一失败语义。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java`

### T4 为 `EvidencePackService` 增加按键清理接口（高优先级）
- 状态：**已完成**
- 实际落地：
  - 新增 `removePack(String tenantId, String workflowId)`，返回 `boolean`。
  - 删除后可通过 `getPack(...)` 立即验证为空。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/EvidencePackService.java`

### T5 增加清理操作可观测性（高优先级）
- 状态：**已完成**
- 实际落地：
  - `removePack` 增加 `info` 日志记录。
  - 增加指标 `evidence_pack_remove_total`，并带 `removed=true/false` 标签。
- 影响文件：
  - `src/main/java/com/example/agent/capabilities/context/EvidencePackService.java`

### T6 补充 `ResearchPipeline` 回归测试（高优先级）
- 状态：**已完成**
- 实际落地：
  - 新增单参数入口覆盖：
    - `runSingleArgumentDelegatesToFullPipeline`
    - `runSingleArgumentFallsBackWhenModelOutputInvalid`
  - 验证单参数入口可正常产出结果或走兜底。
- 影响文件：
  - `src/test/java/com/example/agent/research/ResearchPipelineTest.java`

### T7 补充 `EvidencePackService` 清理测试（高优先级）
- 状态：**已完成**
- 实际落地：
  - 新增清理相关测试：
    - `removePackReturnsTrueWhenPackExists`
    - `removePackReturnsFalseWhenPackNotExists`
    - `removePackThenCreateAgainWorks`
- 影响文件：
  - `src/test/java/com/example/agent/context/EvidencePackTest.java`

### T8 第一阶段文档同步（中优先级）
- 状态：**已完成**
- 实际落地：
  - 更新本执行清单为“落地完成版”。
  - 在评审文档补充“第一阶段已实施结果”章节。
- 影响文件：
  - `doc/context-phase1-task-list-202602090738.md`
  - `doc/context-package-design-review-202602090716.md`

## 4. 阶段产出摘要
- `ResearchPipeline`：完成从“历史占位实现”到“统一解析/修复/兜底流水线”的切换。
- `EvidencePackService`：新增显式清理能力，具备最小可观测闭环。
- 测试：新增与改造点对应的回归测试，并通过全量测试验证。

## 5. 本阶段完成定义（DoD）
- 代码完成：`T1~T5`（已完成）
- 测试完成：`T6~T7`（已完成）
- 文档完成：`T8`（已完成）
- 质量门禁：相关测试通过，无新增严重告警（已满足）
