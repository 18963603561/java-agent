# 工具必填字段缺失分析

生成时间
2026-02-01 10:59

## 现象与证据

工具调用摘要
```
tool: search-user-by-name
arguments: {}
toolStatus: FAILED
toolErrorCode: TOOL_INVALID_REQUEST
toolErrorMessage: 缺少必填字段
```

工具返回示例提示
```
查询失败，原因是缺少必填参数。请提供要查询的用户姓名（例如：name="张三"），然后重新尝试。
```

任务输入示例
```
帮我查询一下包含h的用户信息
```

工具注入配置
```
agent:
  tool:
    inject-mode: summary
```

## 直接原因

必填字段缺失
```
name
```

工具收到空参数而触发必填校验失败。

## 根因分析

用户问题是模糊条件查询，而工具能力是按姓名精确查询。
模型在决策阶段仅收到工具摘要，未获得参数结构与必填字段提示。
工具参数校验发生在执行阶段，缺少前置的参数收集与澄清流程。
失败后的二次总结仍由模型生成，存在忽略失败状态并产生不一致输出的风险。

## 端到端流程

步骤一
任务入口与编排
`src/main/java/com/example/agent/gateway/controller/TaskController.java`
`src/main/java/com/example/agent/orchestrator/TaskExecutionService.java`
`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`

步骤二
步骤运行与模型决策
`src/main/java/com/example/agent/runtime/LlmStepService.java`
`src/main/java/com/example/agent/model/ModelToolResolver.java`
`src/main/java/com/example/agent/model/ModelInvocationService.java`

步骤三
模型生成工具调用
`src/main/java/com/example/agent/runtime/LlmStepService.java`

步骤四
工具执行与参数校验
`src/main/java/com/example/agent/agentcore/ToolExecutor.java`
`src/main/java/com/example/agent/agentcore/ToolArgumentValidator.java`

步骤五
远程工具调用
`src/main/java/com/example/agent/tools/McpToolClient.java`
`src/main/java/com/example/agent/tools/McpToolSyncService.java`

步骤六
工具结果总结与返回
`src/main/java/com/example/agent/runtime/LlmStepService.java`

## 需要改动的文件建议

必填参数前置校验与补问逻辑
`src/main/java/com/example/agent/runtime/LlmStepService.java`

工具注入策略与按需加载参数结构
`src/main/java/com/example/agent/model/ModelToolResolver.java`

工具参数校验与错误输出规范化
`src/main/java/com/example/agent/agentcore/ToolExecutor.java`
`src/main/java/com/example/agent/agentcore/ToolArgumentValidator.java`

远程工具定义与结构缓存
`src/main/java/com/example/agent/tools/McpToolSyncService.java`
`src/main/java/com/example/agent/tools/DefaultToolCatalog.java`

配置调整
`src/main/resources/application.yml`

测试补充
`src/test/java/com/example/agent/runtime/LlmStepServiceTest.java`
`src/test/java/com/example/agent/agentcore/ToolArgumentValidatorTest.java`

## 方案建议

方案一
配置优先
将工具注入模式调整为完整结构，确保模型看到必填字段。
优点是改动最小。
缺点是上下文体积增加。

方案二
参数缺失自动补问
在工具执行前校验必填字段，缺失时直接返回补问提示，不进入工具调用。
优点是稳定阻断无效调用。
缺点是需要调整步骤输出规范。

方案三
工具路由优化
当用户意图为模糊查询时，优先选择支持模糊检索的工具，或新增支持包含条件的工具。
优点是匹配用户意图。
缺点是依赖工具侧能力扩展。

## 风险与验证

风险点
模型忽略失败状态而输出虚构结果。

验证建议
构造缺少姓名的输入，期望系统主动补问。
构造包含姓名的输入，期望工具正常执行并返回结果。
构造无匹配的输入，期望返回空结果提示。