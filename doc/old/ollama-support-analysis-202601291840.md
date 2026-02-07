# `Ollama` 支持分析

## 结论概述
- 当前实现未显式支持 `provider: ollama`，直接配置会回退到本地输出。
- 如 `Ollama` 启用 `OpenAI` 兼容接口，可通过 `provider: openai` + `endpoint` 指向 `/v1` 来复用现有调用逻辑，无需改动代码。
- 如需使用 `Ollama` 原生接口（例如 `/api/chat` 或 `/api/generate`），需要新增调用分支与请求响应适配。

## 当前实现判定依据
- `src/main/java/com/example/agent/model/DefaultModelProvider.java` 仅在 `provider` 包含 `openai` 或 `deepseek` 时进入兼容接口调用逻辑。
- 兼容接口调用路径固定为 `POST /chat/completions`，请求体字段使用 `model`、`messages`、`tools`、`toolChoice`。
- 当 `endpoint` 或 `model-id` 为空时会回退本地输出。

## 无需改动的兼容接口方案
说明：如 `Ollama` 提供 `OpenAI` 兼容接口，可通过配置切换即可。

配置示例：
```yaml
agent:
  model:
    http:
      api-key: ""
    models:
      ollama:
        model-id: llama3
        provider: openai
        endpoint: http://localhost:11434/v1
        input-cost-usd: 0
        output-cost-usd: 0
        max-tokens: 8192
    routes:
      planner: ollama
      reflect: ollama
      research: ollama
      cheap: ollama
```
说明要点：
- `endpoint` 需包含 `/v1`，并由兼容接口支持 `POST /chat/completions`。
- `agent.model.http.api-key` 为空表示不启用鉴权。

## 原生接口支持的改动建议
说明：如需使用 `Ollama` 原生接口，需新增协议分支与映射逻辑。

建议改动文件与内容：
- `src/main/java/com/example/agent/model/DefaultModelProvider.java`
  - 增加 `provider` 识别 `ollama` 的分支。
  - 新增 `invokeOllama` 方法，按 `/api/chat` 或 `/api/generate` 发送请求并解析响应。
  - 根据 `ModelRequest` 补齐消息与参数映射，处理 `stream`、`options` 等字段。
- `src/main/java/com/example/agent/model/ModelRequest.java`
  - 如需支持 `Ollama` 专有参数（例如上下文大小或推理选项），建议增加可选字段或扩展配置承载。
- `src/main/resources/application.yml`
  - 新增 `ollama` 场景示例，明确 `provider`、`endpoint` 与可选参数配置。
- `doc/application-yml-config-202601291406.md`
  - 新增场景章节，说明 `Ollama` 兼容接口与原生接口的差异与示例。
- 测试文件（建议新增或修改）
  - `src/test/java/com/example/agent/model/DefaultModelProviderTest.java`
  - 重点覆盖请求体构建、响应解析与异常回退逻辑。

## 兼容性风险与验证建议
- `toolChoice` 字段为驼峰命名，若兼容接口仅接受 `tool_choice`，可能导致工具调用失效。
- `messages` 角色包含 `developer`，如 `Ollama` 不支持该角色需做映射或降级处理。
- 建议验证以下链路：
  - 基础对话调用成功并返回内容。
  - 工具调用场景返回工具选择与参数。
  - 失败场景是否按预期回退或抛出异常。