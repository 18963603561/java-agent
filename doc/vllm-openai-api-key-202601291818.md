# `vLLM` 与 `OpenAI` 兼容接口密钥依赖分析与去除方案

## 背景与问题
当前 `application.yml` 的 `vLLM` 配置只包含模型与端点信息，并未声明 `DEEPSEEK_API_KEY`。
但是在实际运行中仍需设置 `DEEPSEEK_API_KEY=inner-token`，否则会回退到本地输出，导致 `vLLM` 不被调用。

## 原因分析
`vLLM` 调用是否生效由 `DefaultModelProvider` 的 `OpenAI` 兼容调用路径决定，逻辑要点如下：
1. 当 `provider` 包含 `openai` 或 `deepseek` 时，进入 `OpenAI` 兼容分支。
2. 该分支优先读取环境变量 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`。
3. 若 `baseUrl`、`modelId` 或 `apiKey` 为空，则直接回退本地输出，不会请求 `vLLM`。

因此即使 `vLLM` 不启用鉴权，也需要提供一个非空的 `DEEPSEEK_API_KEY` 才能触发远程调用。

涉及位置：
- `src/main/resources/application.yml`
- `src/main/java/com/example/agent/model/DefaultModelProvider.java`

## 目标
去除对 `DEEPSEEK_API_KEY` 的强依赖，统一使用 `OpenAI` 兼容接口模型服务，支持无鉴权或自定义鉴权。

## 方案一：改为通用 `OpenAI` 风格环境变量
### 改造思路
- 将环境变量读取由 `DEEPSEEK_API_KEY` 改为 `OPENAI_API_KEY`。
- 可选支持 `OPENAI_BASE_URL` 与 `OPENAI_MODEL`。
- 当 `apiKey` 为空时不强制回退本地输出，允许无鉴权服务。

### 代码改造要点
- 在 `DefaultModelProvider` 中替换 `DEEPSEEK_*` 读取逻辑。
- 仅在 `apiKey` 非空时设置 `Authorization` 头。

### 配置示例
```yaml
agent:
  model:
    models:
      qwen3:
        model-id: Qwen3-8B-Instruct
        provider: openai
        endpoint: http://vllm.internal:8000/v1
        input-cost-usd: 0
        output-cost-usd: 0
        max-tokens: 8192
    routes:
      planner: qwen3
      reflect: qwen3
      research: qwen3
      cheap: qwen3
```

### 环境变量示例
```bash
OPENAI_API_KEY=inner-token
```

## 方案二：改为 `application.yml` 可配置密钥
### 改造思路
- 新增配置项 `agent.model.http.api-key`。
- `DefaultModelProvider` 优先读取该配置，避免强依赖环境变量。
- 当配置为空时不附加 `Authorization` 头，支持无鉴权 `vLLM`。

### 配置示例
```yaml
agent:
  model:
    http:
      api-key: inner-token
    models:
      qwen3:
        model-id: Qwen3-8B-Instruct
        provider: openai
        endpoint: http://vllm.internal:8000/v1
        input-cost-usd: 0
        output-cost-usd: 0
        max-tokens: 8192
    routes:
      planner: qwen3
      reflect: qwen3
      research: qwen3
      cheap: qwen3
```

## 迁移建议
1. 先引入通用密钥配置（方案一或方案二）。
2. 保留 `DEEPSEEK_API_KEY` 作为兼容读取项，设置优先级为低。
3. 确认新配置生效后，移除对 `DEEPSEEK_API_KEY` 的依赖提示与文档说明。

## 验证步骤
1. 保证 `provider` 为 `openai`，`endpoint` 指向 `vLLM` 的 `/v1`。
2. 清空或移除 `DEEPSEEK_API_KEY`。
3. 使用新密钥方式启动服务并发起一次模型调用。
4. 观察日志确认走 `OpenAI` 兼容路径，未回退本地输出。

## 注意事项
- 若 `vLLM` 需要鉴权，必须提供非空 `apiKey`。
- 若 `vLLM` 不需要鉴权，需在代码中允许空密钥并跳过 `Authorization` 头。
- 若同时配置了环境变量与配置项，应明确优先级并在文档中标注。
