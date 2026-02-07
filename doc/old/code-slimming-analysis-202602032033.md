# 代码瘦身与通用化分析报告 (2026-02-03 20:33)

## 范围说明
- 基于源码静态扫描与关键类抽查, 未执行构建与测试

## 优先级说明
- P0: 影响核心流程一致性或易引发错误
- P1: 高频重复, 维护成本高
- P2: 中等重复或结构不清导致可读性下降
- P3: 低风险重构与规范化
- P4: 可选优化与技术债记录

## 模块级改进点(摘要)
- `runtime`: P0 工具名解析不一致; P1 执行控制与审批重复; P2 `AgentRuntime` 过重
- `planning`: P0 `tool`/`toolName` 读取分散; P1 解析修复流程重复; P2 规则规划与模型规划耦合
- `model`: P0 工具选择口径不一致; P1 响应解析混杂; P2 本地降级构建重复
- `agentcore`: P1 工具参数校验重复; P1 工具调用上下文拼装重复; P2 `ToolRegistry.resolve` 职责不清
- `tools`: P2 工具目录接口重复; P2 `MCP` 请求响应映射分散
- `context`: P1 读取工具方法重复; P1 字段名硬编码; P2 `Map` 构建复杂
- `memory`: P1 上下文读取重复; P1 `ContextPolicy` 解析重复; P2 `JDBC` 与内存仓储结构重复
- `reasoning`: P1 解析修复与追踪流程重复
- `reflection`: P1 解析修复与追踪流程重夋
- `research`: P1 解析修复与追踪流程重复
- `multiagent`: P1 解析修复与追踪流程重复
- `budget`: P2 裁剪与预算遍历逻辑重复
- `orchestrator`: P1 `JDBC` `JSON` 转换重复; P2 状态流转分散
- `scheduler`: P2 `JDBC` `JSON` 转换重复
- `history`: P1 `JDBC` `JSON` 转换重复
- `approval`: P0 工具名归一化规则分散; P2 高风险工具匹配可缓存
- `sandbox`: P0 工具名归一化缺少统一入口
- `gateway`: P2 控制器日志与异常包装重复
- `common`: P3 上下文字段常量缺失; P4 部分注释编码乱码

## 跨模块可抽取能力
- `ToolNameResolver`/`ToolContextKeys`: 统一 `tool`/`toolName`/`tool.name`
- `JsonOutputParsingFlow`: 解析+修复+错误归类+追踪
- `PromptTraceRecorder`/`PromptBundleApplier`: 统一提示词注入与追踪
- `ToolArgumentValidation`: 统一工具参数校验与规范化
- `JdbcJsonSupport`: 统一 `PGobject`/`JSON` 转换与异常处理
- `ContextValueReader`/`ContextKeys`: 统一上下文字段读取

## 模块内抽取建议(摘要)
- `runtime`: 拆分审批/直达/回退流程协作者, 收敛摘要生成入口
- `planning`: `PlanParser` 与 `PlanHeuristicSelector`
- `model`: 提供方响应解析器与 `LocalFallbackBuilder`
- `budget`: `SectionBudgetHelper` 与 `TokenSummaryBuilder`
- `orchestrator`/`scheduler`/`history`: 查询条件构造器统一

## 建议落地顺序
1. P0 统一 `tool`/`toolName` 规范与解析入口
2. P1 抽取 `JsonOutputParsingFlow` 与 `PromptTraceRecorder`
3. P1 统一 `ToolArgumentValidation` 与 `JdbcJsonSupport`
4. P2 拆分超大类并引入解析器
