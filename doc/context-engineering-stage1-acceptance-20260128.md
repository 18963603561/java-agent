# 阶段一验收用例清单

## 适用范围
- 上下文构建与装配
- 三段式提示词与多角色消息
- 工具目录摘要与按需加载
- 预算分配与裁剪
- 上下文快照与事件载荷

## 用例列表

### A1 上下文快照生成
输入
- 任务请求包含 query、sessionId、context
- 记忆召回返回 summary 与 records

步骤
1. 提交任务触发运行时流程
2. 观察运行时上下文中的 contextSnapshot

预期
- 快照包含 runtimeMeta、taskIntent、workingMemory
- workingMemory.summary 等于召回摘要
- 快照包含 snapshotId

### A2 上下文预算分配
输入
- 配置总预算与预留预算

步骤
1. 触发上下文构建
2. 查看 contextBudget 分段

预期
- 分段总和等于 totalTokens - reservedTokens
- USER_INPUT、WORKING_MEMORY 等分段均有配额

### A3 裁剪触发与摘要
输入
- workingMemory.summary 超出预算

步骤
1. 构建上下文并触发裁剪
2. 查看 contextPrune.removedItems 与 summary

预期
- summary 被裁剪
- removedItems 记录 itemType 与 reason

### A4 三段式提示词输出
输入
- 任务请求包含 contextSnapshot

步骤
1. 触发模型调用
2. 查看 ModelRequest.messages

预期
- messages 依次包含 SYSTEM、DEVELOPER、USER
- USER 内容等于原 prompt

### A5 工具摘要列表
输入
- ToolRegistry 注册多个工具

步骤
1. 调用 ToolCatalog.listSummaries

预期
- 返回列表仅包含摘要字段
- 不包含输入输出 schema

### A6 按需加载工具定义
输入
- 指定工具名

步骤
1. 调用 ToolCatalog.getDefinition

预期
- 返回完整工具定义
- 缓存命中时仍能返回定义

### A7 上下文事件载荷
输入
- 触发上下文构建流程

步骤
1. 订阅事件流
2. 捕获 CONTEXT_SNAPSHOT_CREATED 与 CONTEXT_PRUNED

预期
- payload 包含 snapshotId、sections、budget 信息
- 裁剪事件包含 removedCount 与 summary