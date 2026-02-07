# 阶段三执行批次计划

## 参考文档
- `doc/stage3-context-engineering-plan.md`
- `doc/tasks-stage3.md`

## 批次一 工具上下文减负（`P1`）

### 进入条件
- 前置任务：无，确认阶段二上下文工程任务完成并通过回归测试
- 工具注册与工具摘要查询接口稳定可用
- 日志与指标基础设施可用，包含`MetricsPublisher`

### 退出条件
- 验收点：工具摘要默认注入且不包含输入结构字段
- 验收点：指定工具时按需加载输入结构并生效
- 验收点：缓存命中时不重复访问工具注册表
- 验收点：指标输出包含`schema_cache_hit`与`schema_loaded`
- 验收点：工具注入体量指标`tools_injected_size`可观测
- 测试命令：`mvn test -Dtest=DefaultToolCatalogTest`
- 测试命令：`mvn test -Dtest=ModelToolResolverTest`

### 回滚点
- 里程碑提交点：`stage3-b1-start`
- 里程碑提交点：`stage3-b1-task1`
- 里程碑提交点：`stage3-b1-task2`

### 风险清单与应对
- 风险：按需加载导致首次调用延迟，应对：预热高频工具并监控`schema_loaded`
- 风险：摘要字段不足影响选择质量，应对：保持字段白名单并回归校验
- 风险：缓存过期策略不当导致旧结构使用，应对：配置化并支持手动失效

## 批次二 证据包基础链路（`P2`）

### 进入条件
- 前置任务：批次一完成并通过退出条件测试
- 上下文快照与事件发布链路稳定
- `EvidencePack`与`WorkingMemory`基础结构可用

### 退出条件
- 验收点：`EvidencePack`结构覆盖`citations`、`toolCalls`、`memoriesUsed`
- 验收点：`ToolExecutor`写入工具调用引用
- 验收点：`MemoryRecallService`写入记忆引用
- 验收点：`DefaultContextBuilder`合并证据包到工作记忆
- 验收点：事件载荷输出证据包统计字段且保持兼容
- 测试命令：`mvn test -Dtest=DefaultContextPrunerTest`
- 测试命令：`mvn test -Dtest=ToolExecutorTest`
- 测试命令：`mvn test -Dtest=MemoryRecallServiceTest`
- 测试命令：`mvn test -Dtest=DefaultContextBuilderTest`
- 测试命令：`mvn test -Dtest=ContextEventPublisherTest`

### 回滚点
- 里程碑提交点：`stage3-b2-start`
- 里程碑提交点：`stage3-b2-task1`
- 里程碑提交点：`stage3-b2-task2`
- 里程碑提交点：`stage3-b2-task3`

### 风险清单与应对
- 风险：证据包膨胀导致上下文回涨，应对：设置条目上限并联动裁剪
- 风险：事件载荷字段变更影响消费者，应对：保持旧字段并做兼容测试
- 风险：证据包写入链路遗漏，应对：在关键路径补充日志并覆盖测试

## 批次三 预算驱动裁剪联动（`P3`）

### 进入条件
- 前置任务：批次二完成并通过退出条件测试
- 预算分配与裁剪基础能力稳定
- 记忆压缩链路可用且可配置

### 退出条件
- 验收点：`ContextBudgetPolicy`支持预算比例与裁剪顺序配置
- 验收点：`DefaultContextBudgetAllocator`在无策略时保持默认行为
- 验收点：超预算裁剪顺序固化并记录原因与指标
- 验收点：预算触发压缩与`TokenBudgetManager`联动
- 测试命令：`mvn test -Dtest=DefaultContextBudgetAllocatorTest`
- 测试命令：`mvn test -Dtest=DefaultContextPrunerTest`
- 测试命令：`mvn test -Dtest=MemoryStoreTest`

### 回滚点
- 里程碑提交点：`stage3-b3-start`
- 里程碑提交点：`stage3-b3-task1`
- 里程碑提交点：`stage3-b3-task2`
- 里程碑提交点：`stage3-b3-task3`

### 风险清单与应对
- 风险：裁剪顺序不当导致关键内容丢失，应对：固定顺序并增加回归用例
- 风险：压缩触发过频影响召回质量，应对：设置阈值与冷却时间
- 风险：预算分配失衡影响输出质量，应对：提供默认比例与配置校验

## 批次四 脱敏与拒写（可选，`P4`）

### 进入条件
- 前置任务：批次三完成并通过退出条件测试
- 风险门禁与策略字段定义完成
- 数据合规与安全要求确认

### 退出条件
- 验收点：门禁开启时敏感字段脱敏生效
- 验收点：拒写最小化对记忆落盘生效
- 验收点：门禁关闭时行为与现状一致
- 测试命令：`mvn test -Dtest=MemoryWriteServiceTest`

### 回滚点
- 里程碑提交点：`stage3-b4-start`
- 里程碑提交点：`stage3-b4-task1`

### 风险清单与应对
- 风险：误判导致信息丢失，应对：默认关闭并引入白名单
- 风险：脱敏规则过严影响可用性，应对：分级策略与灰度开关
- 风险：拒写降低可追溯性，应对：保留审计摘要与指标

## 批次五 审批确认（可选，`P5`）

### 进入条件
- 前置任务：批次三完成并通过退出条件测试
- 审批链路基础能力可用并完成联调
- 风险评估输出稳定，包含高风险判定

### 退出条件
- 验收点：高风险工具调用触发审批请求
- 验收点：审批拒绝时工具调用被阻断
- 验收点：低风险调用不受影响
- 测试命令：`mvn test -Dtest=AgentRuntimeApprovalIntegrationTest`

### 回滚点
- 里程碑提交点：`stage3-b5-start`
- 里程碑提交点：`stage3-b5-task1`

### 风险清单与应对
- 风险：审批等待导致流程阻塞，应对：设置超时与降级策略
- 风险：重复审批影响体验，应对：同一工作流缓存审批结果
- 风险：审批策略配置错误，应对：提供回退开关与审计日志