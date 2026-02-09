# context 根包分层彻底性复核报告

## 1. 复核目标
- 复核目录：`src/main/java/com/example/agent/capabilities/context`
- 复核问题：根包分层是否已经彻底。
- 复核时间：`2026-02-09 10:26`

## 2. 结构事实快照
- Java 文件总数：`42`
- 根包直接文件数：`29`
- 子包文件数：`13`
- 根包占比：`69.05%`

### 2.1 子包分布
- `runtime`：`5`
- `research`：`3`
- `builder`：`1`
- `builder/policy`：`1`
- `builder/budget`：`1`
- `builder/exception`：`1`
- `assembly`：`1`

### 2.2 大文件分布（按行数）
- `src/main/java/com/example/agent/capabilities/context/builder/ContextSnapshotFactory.java`：`596` 行
- `src/main/java/com/example/agent/capabilities/context/EvidencePackService.java`：`470` 行
- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java`：`446` 行
- `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java`：`446` 行

## 3. 结论判定

### 3.1 判定结论
**结论：根包分层仍不彻底。**

### 3.2 等级评估
- 分层彻底性等级：**B（7.6/10）**
- 坏味道等级：**B-（中等偏高治理压力）**

## 4. 判定依据

### 4.1 根包仍然承担过多职责
- 根包同时承载：
  - 领域模型对象（如 `ContextSnapshot`、`WorkingMemory`、`RuntimeMeta`）。
  - 编排实现（如 `DefaultContextBuilder`、`DefaultContextAssembler`）。
  - 运行状态服务（如 `EvidencePackService`）。
  - 装配命令与输入对象（如 `ContextAssemblyCommand`、`PromptAssemblyInput`）。
- 这类“模型 + 服务 + 编排 + 协议对象”混放，会削弱包语义边界。

### 4.2 根包仍存在高复杂实现类
- `EvidencePackService` 与 `DefaultContextBuilder` 仍位于根包，且体量偏大。
- 子包虽然已建立（`runtime`、`builder`、`assembly`），但核心复杂类尚未完全迁移到对应职责域。

### 4.3 子包抽取不均衡
- `builder` 下仅少量组件，且主要复杂度集中在 `ContextSnapshotFactory`。
- `assembly` 当前仅一个策略类，根包仍保留装配核心实现。
- 现状属于“有分层方向，但尚未完成分层收口”。

## 5. 主要坏味道清单

### S1：职责混放（高优先级）
- 症状：根包对象类型跨度大，调用关系跨职责交织。
- 风险：变更容易产生连锁影响，评审与测试成本上升。

### S1：复杂类停留在根包（高优先级）
- 症状：核心实现类未下沉到职责子包。
- 风险：根包持续膨胀，分层规则容易被后续开发继续稀释。

### S2：结构语义不够显式（中优先级）
- 症状：仅靠类名难快速判断其层级职责。
- 风险：新成员理解成本增加，容易在根包继续新增“临时类”。

## 6. 建议的收口方向

### 6.1 建议目标包形态
- `context/model`：纯领域模型与值对象。
- `context/evidence`：证据包模型、索引、服务。
- `context/builder`：构建编排、工厂、预算与策略解析。
- `context/assembly`：提示词装配命令、输入对象、策略与装配器实现。
- `context/runtime`：运行时上下文键与类型化读写门面。
- `context/research`：研究流程与研究结果对象。

### 6.2 最小落地顺序
1. 先迁移 `EvidencePackService` 及相关证据对象到 `evidence` 子包。
2. 再将 `DefaultContextAssembler` 与装配输入对象收敛到 `assembly`。
3. 最后将根包仅保留对外门面接口与少量稳定抽象。

## 7. 复核结语
- 当前状态不是“无分层”，而是“分层进行中”。
- 从治理角度看，已经有正确方向，但距离“根包清爽、职责明确、层次稳定”的目标仍有一段收口工作。
- 若按建议完成结构收敛，分层彻底性可提升到 **A-** 水平。
