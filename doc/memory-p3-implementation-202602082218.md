# memory 包 P3 改造实施与验收记录

- 实施依据：`doc/memory-p3-tasklist-202602082203.md`
- 实施时间：2026-02-08 22:03 ~ 22:18
- 实施范围：`src/main/java/com/example/agent/capabilities/memory` 与 `src/test/java/com/example/agent/memory`

---

## 1. 实施结果总览

P3-01 ~ P3-05 已全部落地完成：

1. 已清理无效模型与未落地字段。
2. 已修复 `HashEmbeddingService` 下标边界风险。
3. 已为序列化降级路径补齐 `warn` 日志与指标打点。
4. 已新增 memory 分层依赖守护测试。
5. 已完成本次实施记录文档收口。

---

## 2. 分阶段实施记录

### P3-01：清理无效模型与未使用配置字段

- 删除类：`MemoryChunk`
- 删除字段：`CompressionRequest.strategy`
- 删除配置项：`MemoryVectorProperties.provider`
- 结果：memory 包内相关残留引用已清空。

### P3-03：修复 Embedding 边界风险

- 修改点：`HashEmbeddingService`
  - 下标计算从 `Math.abs(hash) % dimension` 改为 `Math.floorMod(hash, dimension)`。
  - 增加非法维度配置兜底与告警日志。
- 新增测试：`HashEmbeddingServiceTest`
  - 覆盖 `dimension <= 0` 回退行为。
  - 覆盖 `Integer.MIN_VALUE` 哈希边界行为。

### P3-02：序列化降级可观测性增强

- 修改点：`MemoryWriteService`
  - 增加指标：`memory_write_output_serialize_fallback_total`
  - `serializeOutput` 降级分支新增 `warn` 日志（含 tenant/workflow/session/task 上下文）。
  - 保持降级策略不变（返回 `String.valueOf(output)`）。
- 新增测试场景：
  - `MemoryWriteServiceTest.saveTaskMemoryShouldFallbackWhenSerializeFailed`
  - 验证降级路径可写入且指标递增。

### P3-04：分层依赖守护测试

- 新增测试：`MemoryArchitectureGuardTest`
  - 守护 `support` 不依赖 `store/recall`。
  - 守护 `MemoryPolicy` 不依赖 `store/recall`。
  - 守护 `repository/vector` 不依赖 `recall`。
  - 守护 `MemoryStore` 保持 Facade 委派，不回流重逻辑。

### P3-05：文档与交付收口

- 输出本实施与验收文档。

---

## 3. 测试验收

按阶段门禁执行全量测试命令：

```bash
mvn -q test
```

执行结论：

1. P3-01 完成后：全量通过。
2. P3-03 完成后：全量通过。
3. P3-02 完成后：全量通过。
4. P3-04 首轮新增守护测试后：发现 1 个守护断言误报，修正断言后复跑全量通过。
5. P3-05 收口后：可再次执行全量回归，作为最终门禁。

---

## 4. 风险与后续建议

1. 当前 guard test 采用源码文本守护，优点是轻量、无额外依赖；后续可评估 ArchUnit 增强表达力。
2. 若后续 memory 再分目录（如 model/policy/repository/vector 物理迁移），需同步更新 guard 规则白名单。
3. 当前序列化降级指标使用全局计数，后续可按场景追加标签维度（如 workflowId）以支持更精细观测。