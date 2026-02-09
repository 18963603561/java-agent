# context 根包分层收口实施记录

## 1. 实施背景
- 依据文档：`doc/context-root-layering-check-202602091026.md`
- 实施目标：按新项目标准完成 `context` 包分层收口，不保留历史兼容分支，直接按新结构落地。
- 实施时间：`2026-02-09 10:47`

## 2. 实施原则
- 不做新旧包结构并存，不引入过渡条件分支。
- 以职责内聚为目标进行包迁移，确保通用性与可复用性。
- 每个阶段改造完成后执行全量回归：`mvn -q -DskipTests=false test`。

## 3. 分阶段落地结果

### 阶段一：evidence 领域收口

#### 3.1.1 包迁移
- 将以下类从根包迁移至 `context.evidence`：
  - `EvidencePack`
  - `EvidenceItem`
  - `EvidenceIndex`
  - `EvidenceStats`
  - `EvidenceType`
  - `EvidencePackService`

#### 3.1.2 代码同步
- 全量更新主代码与测试中的 import 引用。
- 修复迁移引入的编译问题：`WorkingMemory` 补充 `EvidencePack` 新包导入。

#### 3.1.3 阶段回归
- 执行：`mvn -q -DskipTests=false test`
- 结果：通过。

### 阶段二：assembly 领域收口

#### 3.2.1 包迁移
- 将装配相关类迁移至 `context.assembly`：
  - `ContextAssembler`
  - `ContextAssemblyCommand`
  - `PromptAssemblyInput`
  - `DefaultContextAssembler`

#### 3.2.2 代码同步
- 全量更新主代码与测试 import。
- 修正类内依赖：补充 `ContextSnapshot` 等新包导入。
- 同步清理接口注释语义，`ContextAssembler` 明确以 `command` 为唯一输入载体。

#### 3.2.3 阶段回归
- 执行：`mvn -q -DskipTests=false test`
- 结果：通过。

### 阶段三：model 领域收口与根包瘦身

#### 3.3.1 包迁移
- 将模型对象迁移到 `context.model`：
  - `AuditMetadata`
  - `BudgetState`
  - `BuildMetrics`
  - `Citation`
  - `ContextPolicy`
  - `ContextSnapshot`
  - `DomainKnowledge`
  - `LongTermMemory`
  - `MemoryRef`
  - `RoleBoundary`
  - `RuntimeMeta`
  - `TaskIntent`
  - `ToolCallState`
  - `ToolState`
  - `WorkingMemory`

#### 3.3.2 代码同步
- 全量替换主代码与测试中的模型引用路径。
- 重点修复：
  - `ContextBuildRequest` 补充 `ContextPolicy` 新包导入。
  - `ContextBuildResult` 补充 `ContextSnapshot`、`BuildMetrics` 新包导入。
  - `DefaultContextBuilder` 补充 `ContextSnapshot`、`RuntimeMeta`、`BuildMetrics`、`BudgetState`、`ContextPolicy` 新包导入。

#### 3.3.3 阶段回归
- 执行：`mvn -q -DskipTests=false test`
- 结果：通过。

## 4. 收口后结构结果
- `context` 总 Java 文件：`42`
- 根包文件数：`4`
- 根包仅保留：
  - `ContextBuilder`
  - `ContextBuildRequest`
  - `ContextBuildResult`
  - `DefaultContextBuilder`
- 子包分布：
  - `model`：`15`
  - `evidence`：`6`
  - `assembly`：`5`
  - `runtime`：`5`
  - `research`：`3`
  - `builder`：`4`

## 5. 结论
- 本轮已按复核文档完成分阶段落地与测试收口。
- 根包职责显著收敛，模型、装配、证据、运行时职责边界已显式分层。
- 在“不保留历史包袱、不做兼容分支”的前提下，完成了可复用、可维护的包结构重整。
