# memory 包 P3 具体改造任务清单

- 来源文档：`doc/memory-package-review-202602082040.md`
- 基线状态：P1、P2 已完成（召回组件化、Store 三职责拆分、support 抽取与测试补齐）
- 目标定位：在不改变外部 API 行为前提下，完成“可选优化项”落地，进一步提升长期可维护性与工程一致性
- 适用范围：`src/main/java/com/example/agent/capabilities/memory`

---

## 1. P3 总体目标

1. 清理已识别的死代码与未落地字段，降低认知噪音。
2. 完善降级路径可观测性，保证故障可回溯。
3. 修复低概率边界风险，提升实现稳健性。
4. 补充分层守护与回归用例，防止后续演进回退。

---

## 2. 任务拆解（P3）

| 任务编号 | 任务名称 | 优先级 | 预估工作量 | 风险等级 | 依赖 |
| --- | --- | --- | --- | --- | --- |
| P3-01 | 清理无效模型与未使用配置字段 | P3 | 0.5 人天 | 低 | 无 |
| P3-02 | 完善序列化降级链路可观测性 | P3 | 0.5 人天 | 低 | P3-01 |
| P3-03 | 修复 `HashEmbeddingService` 边界下标风险 | P3 | 0.5 人天 | 低 | 无 |
| P3-04 | 增加 memory 分层依赖守护测试 | P3 | 1 人天 | 中 | P3-01~P3-03 |
| P3-05 | 文档与配置样例收口 | P3 | 0.5 人天 | 低 | P3-01~P3-04 |

---

## 3. 详细任务清单

### P3-01：清理无效模型与未使用配置字段

#### 改造目标
- 移除对业务无价值的“占位结构”，减少维护噪音。
- 确保删除后无隐式依赖与反射引用残留。

#### 具体改造项
1. 删除未使用模型：
   - `MemoryChunk`（若确认全局无引用，直接移除）。
2. 删除未使用请求字段：
   - `CompressionRequest.strategy`（未参与任何分支决策）。
3. 删除未使用配置项：
   - `MemoryVectorProperties.provider`（若全链路无读取逻辑）。
4. 全局检索并清理相关注释、文档、测试中的陈旧说明。

#### 涉及文件（建议）
- `src/main/java/com/example/agent/capabilities/memory/MemoryChunk.java`
- `src/main/java/com/example/agent/capabilities/memory/CompressionRequest.java`
- `src/main/java/com/example/agent/capabilities/memory/MemoryVectorProperties.java`

#### 验收标准
1. 上述冗余对象清理后，`mvn -q test` 全量通过。
2. `rg` 全局检索无已删除字段的残留引用。
3. 对外请求结构与现有调用契约不受影响。

---

### P3-02：完善序列化降级链路可观测性

#### 改造目标
- 对 `MemoryWriteService.serializeOutput` 的异常降级增加可观测性闭环。
- 在“可继续执行”的前提下给出最小必要诊断信息。

#### 具体改造项
1. 在 `serializeOutput` 捕获异常时增加 `warn` 日志，日志需包含：
   - `tenantId`
   - `workflowId`
   - `sessionId`
   - `taskId`
   - 异常简要原因
2. 增加降级计数指标：
   - 建议指标名：`memory_write_output_serialize_fallback_total`
3. 保持当前降级策略：
   - 仍返回 `String.valueOf(output)`，不抛出异常、不阻断主流程。

#### 涉及文件（建议）
- `src/main/java/com/example/agent/capabilities/memory/MemoryWriteService.java`

#### 验收标准
1. 触发序列化异常时可看到 `warn` 日志与指标打点。
2. 任务主流程不中断，写入行为保持可用。
3. 原有相关单测通过，并新增异常降级场景测试。

---

### P3-03：修复 `HashEmbeddingService` 下标边界风险

#### 改造目标
- 解决 `Math.abs(Integer.MIN_VALUE)` 仍为负值的边界问题。
- 保证 embedding 维度索引计算稳定且不会越界。

#### 具体改造项
1. 将下标计算改为安全实现（示例策略二选一）：
   - `Math.floorMod(hash, dimension)`
   - 或无符号取模实现。
2. 保持向量维度与稀疏分布语义不变。
3. 对 `dimension <= 0` 的输入做显式参数防御与日志。

#### 涉及文件（建议）
- `src/main/java/com/example/agent/capabilities/memory/HashEmbeddingService.java`

#### 验收标准
1. `Integer.MIN_VALUE` 相关场景不再触发越界。
2. 常规文本 embedding 结果维度正确。
3. 新增边界单测并通过全量测试。

---

### P3-04：增加 memory 分层依赖守护测试

#### 改造目标
- 固化 P2 已建立的分层方向，防止未来回退为“大类耦合”。

#### 具体改造项
1. 新增分层依赖守护测试（可用 ArchUnit 或轻量反射检查）：
   - `support` 不依赖 `store/recall/service`。
   - `policy` 不依赖 `store/recall`。
   - `repository/vector` 不依赖 `recall`。
2. 新增关键编排行为快照测试：
   - `MemoryStore` 仅做委派，不承载复杂业务逻辑。

#### 涉及文件（建议）
- `src/test/java/com/example/agent/memory/...`（新增 `architecture` 或 `layering` 相关测试类）

#### 验收标准
1. 依赖方向检查可在 CI 稳定运行。
2. 若出现反向依赖，测试可直接失败并提示具体包路径。

---

### P3-05：文档与配置样例收口

#### 改造目标
- 让 P3 优化成果可被团队快速理解与复用。

#### 具体改造项
1. 更新 memory 相关说明文档：
   - 删除已移除字段/模型说明。
   - 增加降级日志与指标说明。
   - 增加 embedding 边界处理说明。
2. 若项目有配置样例，补充/校正对应配置注释。
3. 输出一份 P3 变更摘要（改造项、风险、回滚点）。

#### 涉及文件（建议）
- `doc/` 下 memory 相关设计与运行说明文档
- 相关配置样例文件（如存在）

#### 验收标准
1. 文档内容与代码现状一致。
2. 新同学可依据文档快速定位关键路径与排障入口。

---

## 4. 推荐实施顺序

1. 先做 **P3-01**（删除无效项，降低后续改造噪音）。
2. 再做 **P3-03**（先修复边界风险，收益直接）。
3. 再做 **P3-02**（补齐序列化降级可观测性）。
4. 接着做 **P3-04**（把分层规则固化为测试守护）。
5. 最后做 **P3-05**（文档与配置收口）。

---

## 5. 阶段门禁（每阶段必须执行）

```bash
mvn -q test
```

每完成一个 P3 子任务，必须满足：
1. 全量测试通过。
2. 新增/修改代码注释为中文且无乱码。
3. 关键日志（外部调用、主流程、异常）无缺失回退。

---

## 6. 交付定义（P3 DoD）

1. P3-01~P3-05 全部完成，且全量测试通过。
2. memory 包内无无效字段/死代码残留。
3. 序列化降级链路具备日志与指标，排障可追踪。
4. embedding 边界缺陷消除，相关测试覆盖到位。
5. 分层依赖规则被自动化测试守护。