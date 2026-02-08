# memory 包 P2 具体改造任务清单

- 来源文档：`doc/memory-package-review-202602082040.md`
- 目标：在 P1 完成基础稳定性治理后，继续提升可维护性、复用性与架构演进能力
- 适用范围：`src/main/java/com/example/agent/capabilities/memory`

---

## 1. P2 总体目标

1. 将“重流程、重职责”类进行职责拆分，降低单类复杂度与变更冲突概率。
2. 抽取可复用能力，消除跨服务重复逻辑，形成统一基础组件。
3. 建立清晰分层（编排层、策略层、转换层、工具层），让后续新增能力可平滑接入。
4. 保持外部行为稳定（API 输入输出不变），以内部重构为主。

---

## 2. 任务分解（P2）

| 任务编号 | 任务名称 | 优先级 | 预估工作量 | 风险等级 | 依赖 |
| --- | --- | --- | --- | --- | --- |
| P2-01 | 拆分 `MemoryRecallService` 为“解析-检索-后处理”流水线 | P2 | 2 人天 | 中 | 无 |
| P2-02 | 拆分 `MemoryStore` 为“写入编排-检索编排-维护触发” | P2 | 2 人天 | 中 | P2-01 |
| P2-03 | 提取 memory 公共工具组件，去重文本与优先级逻辑 | P2 | 1 人天 | 低 | P2-01 |
| P2-04 | 建立 memory 包内分层目录结构与依赖约束 | P2 | 1 人天 | 中 | P2-02, P2-03 |
| P2-05 | 完善重构后测试体系（单测+契约回归） | P2 | 1.5 人天 | 中 | P2-01~P2-04 |

---

## 3. 详细任务清单

### P2-01：拆分 `MemoryRecallService`

#### 改造目标

- 消除 `MemoryRecallService` 的职责堆叠问题（上下文解析、策略解析、检索、脱敏、摘要构建、指标上报）。
- 保留当前入口方法签名和结果结构，避免上游调用调整。

#### 建议新增组件

1. `RecallContextResolver`
   - 职责：解析 `TaskRequest/context` 的召回配置开关、阈值、limit、includeCompressed。
2. `RecallPolicyResolver`
   - 职责：解析 `ContextPolicy`（对象或 Map）并输出标准化策略对象。
3. `RecallExecutionPlanner`
   - 职责：根据策略生成检索执行计划（优先级顺序、敏感开关、裁剪参数）。
4. `RecallPostProcessor`
   - 职责：记录裁剪、脱敏、摘要构建。
5. `RecallMetricsRecorder`
   - 职责：统一指标打点与标签格式。

#### 涉及文件

- 主要修改：
  - `src/main/java/com/example/agent/capabilities/memory/MemoryRecallService.java`
- 建议新增：
  - `src/main/java/com/example/agent/capabilities/memory/recall/RecallContextResolver.java`
  - `src/main/java/com/example/agent/capabilities/memory/recall/RecallPolicyResolver.java`
  - `src/main/java/com/example/agent/capabilities/memory/recall/RecallExecutionPlanner.java`
  - `src/main/java/com/example/agent/capabilities/memory/recall/RecallPostProcessor.java`
  - `src/main/java/com/example/agent/capabilities/memory/recall/RecallMetricsRecorder.java`

#### 验收标准

1. `MemoryRecallService` 主流程方法体控制在 120 行以内。
2. 每个新组件仅负责一个职责域，且有中文类注释与关键方法注释。
3. 现有召回功能测试全部通过，外部行为不变。

---

### P2-02：拆分 `MemoryStore`

#### 改造目标

- 将 `MemoryStore` 从“大而全”编排器拆成更清晰的子服务。
- 降低保存、检索、维护（自动压缩/过期清理）之间的耦合。

#### 建议新增组件

1. `MemorySaveOrchestrator`
   - 职责：保存主流程、向量写入、基础字段补全。
2. `MemorySearchOrchestrator`
   - 职责：按优先级聚合 recent/semantic/summary 检索结果。
3. `MemoryMaintenanceService`
   - 职责：自动压缩触发与过期清理触发（含节流）。

#### 涉及文件

- 主要修改：
  - `src/main/java/com/example/agent/capabilities/memory/MemoryStore.java`
- 建议新增：
  - `src/main/java/com/example/agent/capabilities/memory/store/MemorySaveOrchestrator.java`
  - `src/main/java/com/example/agent/capabilities/memory/store/MemorySearchOrchestrator.java`
  - `src/main/java/com/example/agent/capabilities/memory/store/MemoryMaintenanceService.java`

#### 验收标准

1. `MemoryStore` 保留 Facade 角色，仅负责参数入口与编排委派。
2. `MemoryStore` 复杂逻辑迁移完成后，核心私有方法数明显下降。
3. 自动压缩与过期清理行为和当前版本保持一致。

---

### P2-03：提取公共工具组件

#### 改造目标

- 消除 memory 包内部重复逻辑（`trimText`、`firstNonBlank`、优先级规范化等）。
- 提升逻辑一致性，减少后续变更漂移风险。

#### 建议新增组件

1. `MemoryTextUtils`
   - 方法建议：`trimText`、`firstNonBlank`、`safeLowercaseContains`。
2. `RetrievalPriorityUtils`
   - 方法建议：`normalizeOrder`、`formatPriorityTag`。

#### 涉及文件

- 建议新增：
  - `src/main/java/com/example/agent/capabilities/memory/support/MemoryTextUtils.java`
  - `src/main/java/com/example/agent/capabilities/memory/support/RetrievalPriorityUtils.java`
- 主要替换：
  - `MemoryWriteService`
  - `MemoryRecallService`
  - `MemoryStore`
  - `CompressedMemoryStore`
  - `MemoryPolicy`

#### 验收标准

1. memory 包内不再保留重复实现的 `trimText`/`firstNonBlank`。
2. 文本裁剪行为保持一致，相关测试通过。

---

### P2-04：包结构分层与依赖约束

#### 改造目标

- 将当前“单包多角色”结构拆为分层目录，提高可读性与团队协作效率。
- 明确依赖方向，避免策略层反向依赖编排层。

#### 建议目录结构

```text
memory/
  model/          # MemoryRecord、Query、Result、Summary 等模型
  config/         # Properties
  policy/         # MemoryPolicy 等策略
  repository/     # MemoryRepository + 实现
  vector/         # VectorStore + EmbeddingService + 实现
  store/          # recent/semantic/compressed 存取层与编排子服务
  recall/         # 召回流程组件
  support/        # 公共工具
```

#### 依赖规则（建议）

1. `support` 只能被上层依赖，不依赖业务服务。
2. `policy` 不依赖 `store`/`recall`。
3. `repository` 与 `vector` 不依赖 `recall`。

#### 验收标准

1. 目录迁移后编译通过，所有 import 更新完整。
2. 关键分层规则通过架构测试或静态规则校验（可新增轻量守护测试）。

---

### P2-05：测试体系补全

#### 改造目标

- 为重构后的组件建立“单元测试 + 流程回归测试”的双层保障。
- 防止后续 P3/P4 改造引入行为漂移。

#### 测试增补建议

1. `RecallContextResolverTest`
2. `RecallPolicyResolverTest`
3. `MemorySearchOrchestratorTest`
4. `MemoryMaintenanceServiceTest`
5. `MemoryTextUtilsTest`

#### 回归测试建议

1. 保留并通过现有 memory 相关用例。
2. 补充“等价行为快照断言”场景：
   - 召回顺序、摘要构建、脱敏计数。
   - 自动压缩触发点与过期清理触发点。

#### 验收标准

1. 新增单测覆盖核心分支（正常、空值、边界、异常降级）。
2. 重构后 `mvn -q test` 全量通过。

---

## 4. 推荐实施顺序

1. 先做 **P2-01**（召回流程拆分），降低最复杂类风险。
2. 再做 **P2-03**（公共工具提取），给 P2-02 提供复用基础。
3. 然后做 **P2-02**（MemoryStore 编排拆分）。
4. 再做 **P2-04**（目录分层迁移）。
5. 最后做 **P2-05**（测试补全与回归加固）。

---

## 5. 阶段门禁（每阶段必须执行）

```bash
mvn -q test
```

每完成一个 P2 子任务，必须满足：

1. 全量测试通过。
2. 本阶段新增类/方法具备中文注释。
3. 核心流程（保存、召回、压缩）日志无回退、无乱码。

---

## 6. 交付定义（P2 DoD）

1. P2-01~P2-05 全部完成并通过全量测试。
2. `MemoryRecallService`、`MemoryStore` 从“重实现类”降级为“轻编排 Facade”。
3. memory 包内重复逻辑显著减少，并集中到 `support` 组件。
4. 目录结构与依赖方向清晰，新增功能可按层扩展。

