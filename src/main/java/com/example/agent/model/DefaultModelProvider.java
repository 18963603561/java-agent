package com.example.agent.model;

import com.example.agent.common.ErrorCodeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 默认模型提供商实现，支持本地规则输出与兼容接口调用。
 * <p>用途：根据模型定义选择调用方式，并提供本地兜底输出。
 * <p>输入：模型定义与模型请求。
 * <p>输出：模型响应对象。
 * <p>边界：配置缺失时回退本地输出；远程调用失败时抛出业务异常。
 * <p>示例：
 * <pre>{@code
 * ModelResponse response = modelProvider.invoke(definition, request);
 * }</pre>
 */
@Component
public class DefaultModelProvider implements ModelProvider {

    /**
     * 日志记录器。
     * <p>示例：记录模型调用耗时与异常原因。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultModelProvider.class);

    /**
     * 规划上下文标记。
     * <p>示例：{@code PLAN_CONTEXT_JSON:}。
     */
    private static final String PLAN_MARKER = "PLAN_CONTEXT_JSON:";
    /**
     * 反思上下文标记。
     * <p>示例：{@code REFLECTION_CONTEXT_JSON:}。
     */
    private static final String REFLECTION_MARKER = "REFLECTION_CONTEXT_JSON:";
    /**
     * 最终输出上下文标记。
     * <p>示例：{@code FINAL_CONTEXT_JSON:}。
     */
    private static final String FINAL_MARKER = "FINAL_CONTEXT_JSON:";
    /**
     * 研究上下文标记。
     * <p>示例：{@code RESEARCH_CONTEXT_JSON:}。
     */
    private static final String RESEARCH_MARKER = "RESEARCH_CONTEXT_JSON:";
    /**
     * 辩论上下文标记。
     * <p>示例：{@code DEBATE_CONTEXT_JSON:}。
     */
    private static final String DEBATE_MARKER = "DEBATE_CONTEXT_JSON:";
    /**
     * 多智能体上下文标记。
     * <p>示例：{@code MULTI_AGENT_CONTEXT_JSON:}。
     */
    private static final String MULTI_AGENT_MARKER = "MULTI_AGENT_CONTEXT_JSON:";
    /**
     * 链式推理上下文标记。
     * <p>示例：{@code COT_CONTEXT_JSON:}。
     */
    private static final String COT_MARKER = "COT_CONTEXT_JSON:";

    /**
     * 序列化工具。
     * <p>示例：序列化请求或本地输出结构。
     */
    private final ObjectMapper objectMapper;
    /**
     * 调用客户端构建器。
     * <p>示例：创建远程调用客户端。
     */
    private final WebClient.Builder webClientBuilder;

    /**
     * 请求超时时间秒数。
     * <p>示例：{@code 30}。
     */
    @Value("${agent.model.http.timeout-seconds:30}")
    private long timeoutSeconds;

    /**
     * 采样温度默认值。
     * <p>示例：{@code 0.2}。
     */
    @Value("${agent.model.http.temperature:0.2}")
    private double temperature;

    /**
     * 兼容接口密钥，留空表示不需要鉴权。
     * <p>示例：{@code "sk-xxx"}。
     */
    @Value("${agent.model.http.api-key:}")
    private String apiKey;

    /**
     * 构造模型提供方。
     *
     * <p>输入：序列化工具与调用客户端构建器。
     * <p>输出：初始化后的模型提供方。
     * <p>示例：
     * <pre>{@code
     * new DefaultModelProvider(objectMapper, webClientBuilder);
     * }</pre>
     *
     * @param objectMapper 序列化工具
     * @param webClientBuilder 调用客户端构建器
     */
    public DefaultModelProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * 调用模型并返回响应。
     *
     * <p>输入：模型定义与模型请求。
     * <p>输出：模型响应对象。
     * <p>边界：当提供方未匹配时回退本地输出。
     * <p>示例：
     * <pre>{@code
     * ModelResponse response = invoke(definition, request);
     * }</pre>
     *
     * @param definition 模型定义
     * @param request 模型请求
     * @return 模型响应
     */
    @Override
    public ModelResponse invoke(ModelDefinition definition, ModelRequest request) {
        String provider = definition != null ? definition.getProvider() : null;
        if (provider != null) {
            String normalized = provider.toLowerCase(Locale.ROOT);
            if (normalized.contains("ollama")) {
                return invokeOllama(definition, request);
            }
            if (normalized.contains("openai") || normalized.contains("deepseek")) {
                return invokeOpenAiCompatible(definition, request);
            }
        }
        return invokeLocal(definition, request);
    }

    /**
     * 使用本地规则生成模型响应。
     *
     * <p>输入：模型定义与模型请求。
     * <p>输出：基于规则构造的响应对象。
     * <p>边界：提示词为空时返回默认响应。
     * <p>示例：
     * <pre>{@code
     * ModelResponse response = invokeLocal(definition, request);
     * }</pre>
     */
    private ModelResponse invokeLocal(ModelDefinition definition, ModelRequest request) {
        String prompt = resolvePrompt(request);
        String modelId = definition != null ? definition.getModelId() : "local";
        int inputTokens = prompt != null ? prompt.length() : 0;
        String content = buildLocalResponse(prompt);
        int outputTokens = content.length();
        return new ModelResponse(modelId, content, inputTokens, outputTokens);
    }

    /**
     * 调用兼容接口的模型服务。
     *
     * <p>输入：模型定义与模型请求。
     * <p>输出：模型响应对象。
     * <p>边界：配置缺失时回退本地输出；调用异常时抛出业务异常。
     * <p>示例：
     * <pre>{@code
     * ModelResponse response = invokeOpenAiCompatible(definition, request);
     * }</pre>
     */
    private ModelResponse invokeOpenAiCompatible(ModelDefinition definition, ModelRequest request) {
        String baseUrl = definition != null ? definition.getEndpoint() : null;
        String modelId = definition != null ? definition.getModelId() : null;
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelId)) {
            log.warn("模型配置缺失，回退本地输出, 服务地址={}, 模型标识={}", baseUrl, modelId);
            return invokeLocal(definition, request);
        }

        // 构造兼容接口请求体。
        Map<String, Object> body = buildOpenAiRequestBody(modelId, request);
        int toolCount = resolveToolCount(body);
        long startNs = System.nanoTime();
        log.info("兼容接口调用开始, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount);

        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        WebClient.RequestBodySpec requestSpec = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON);
        String resolvedApiKey = resolveApiKey(definition);
        if (StringUtils.hasText(resolvedApiKey)) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + resolvedApiKey);
        }
        WebClient.RequestBodySpec finalRequestSpec = requestSpec;
        Map<String, Object> response;
        try {
            // 通过阻塞执行确保在非阻塞线程中安全调用。
            response = executeBlocking(() -> finalRequestSpec.bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            result -> result.bodyToMono(String.class)
                                    .defaultIfEmpty("model_call_failed")
                                    .flatMap(message -> Mono.error(new ErrorCodeException(
                                            HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", message))))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block());
        } catch (Exception ex) {
            log.error("兼容接口调用异常, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount, ex);
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "模型调用异常");
        }

        if (response == null) {
            log.error("兼容接口调用失败, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "模型调用失败");
        }
        // 解析响应内容与令牌消耗。
        String content = extractContent(response);
        int inputTokens = extractTokens(response, "prompt_tokens");
        int outputTokens = extractTokens(response, "completion_tokens");
        String responseModel = response.get("model") instanceof String value ? value : modelId;
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        log.info("兼容接口调用完成, 模型标识={}, 服务地址={}, 延迟毫秒={}, 输入令牌={}, 输出令牌={}",
                responseModel, baseUrl, durationMs, inputTokens, outputTokens);
        return new ModelResponse(responseModel, content, inputTokens, outputTokens);
    }

    /**
     * 调用原生接口的模型服务。
     *
     * <p>输入：模型定义与模型请求。
     * <p>输出：模型响应对象。
     * <p>边界：配置缺失时回退本地输出；调用异常时抛出业务异常。
     * <p>示例：
     * <pre>{@code
     * ModelResponse response = invokeOllama(definition, request);
     * }</pre>
     */
    private ModelResponse invokeOllama(ModelDefinition definition, ModelRequest request) {
        String baseUrl = definition != null ? definition.getEndpoint() : null;
        String modelId = definition != null ? definition.getModelId() : null;
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelId)) {
            log.warn("模型配置缺失，回退本地输出, 服务地址={}, 模型标识={}", baseUrl, modelId);
            return invokeLocal(definition, request);
        }

        // 构造原生接口请求体。
        Map<String, Object> body = buildOllamaRequestBody(modelId, request);
        int toolCount = resolveToolCount(body);
        long startNs = System.nanoTime();
        log.info("原生接口调用开始, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount);

        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        WebClient.RequestBodySpec requestSpec = client.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON);
        String resolvedApiKey = resolveApiKey(definition);
        if (StringUtils.hasText(resolvedApiKey)) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + resolvedApiKey);
        }
        WebClient.RequestBodySpec finalRequestSpec = requestSpec;
        Map<String, Object> response;
        try {
            // 通过阻塞执行确保在非阻塞线程中安全调用。
            response = executeBlocking(() -> finalRequestSpec.bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            result -> result.bodyToMono(String.class)
                                    .defaultIfEmpty("model_call_failed")
                                    .flatMap(message -> Mono.error(new ErrorCodeException(
                                            HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", message))))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block());
        } catch (Exception ex) {
            log.error("原生接口调用异常, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount, ex);
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "模型调用异常");
        }

        if (response == null) {
            log.error("原生接口调用失败, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "模型调用失败");
        }
        // 解析响应内容与令牌消耗。
        String content = extractOllamaContent(response);
        int inputTokens = extractOllamaTokens(response, "prompt_eval_count");
        int outputTokens = extractOllamaTokens(response, "eval_count");
        String responseModel = response.get("model") instanceof String value ? value : modelId;
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        log.info("原生接口调用完成, 模型标识={}, 服务地址={}, 延迟毫秒={}, 输入令牌={}, 输出令牌={}",
                responseModel, baseUrl, durationMs, inputTokens, outputTokens);
        return new ModelResponse(responseModel, content, inputTokens, outputTokens);
    }

    /**
     * 构建兼容接口请求体，工具为空时保持纯提示行为。
     *
     * <p>输入：模型标识与模型请求。
     * <p>输出：兼容接口请求体映射。
     * <p>边界：请求为空时使用空提示内容。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> body = buildOpenAiRequestBody("gpt-4", request);
     * }</pre>
     *
     * @param modelId 模型标识
     * @param request 模型请求
     * @return 请求体
     */
    Map<String, Object> buildOpenAiRequestBody(String modelId, ModelRequest request) {
        String prompt = request != null ? request.getPrompt() : "";
        Map<String, Object> body = new HashMap<>();
        body.put("model", modelId);
        Double temperatureOverride = request != null ? request.getTemperature() : null;
        body.put("temperature", temperatureOverride != null ? temperatureOverride : temperature);
        body.put("messages", buildMessages(request, prompt));
        if (request != null && request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = buildOpenAiTools(request.getTools());
            if (!tools.isEmpty()) {
                body.put("tools", tools);
                Object toolChoice = buildToolChoiceValue(request.getToolChoice());
                if (toolChoice != null) {
                    body.put("tool_choice", toolChoice);
                    body.put("toolChoice", toolChoice);
                }
            }
        }
        return body;
    }

    /**
     * 构建原生接口请求体，工具为空时仅发送消息。
     *
     * <p>输入：模型标识与模型请求。
     * <p>输出：原生接口请求体映射。
     * <p>边界：请求为空时使用空提示内容。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> body = buildOllamaRequestBody("llama", request);
     * }</pre>
     *
     * @param modelId 模型标识
     * @param request 模型请求
     * @return 请求体
     */
    Map<String, Object> buildOllamaRequestBody(String modelId, ModelRequest request) {
        String prompt = request != null ? request.getPrompt() : "";
        Map<String, Object> body = new HashMap<>();
        body.put("model", modelId);
        body.put("messages", buildOllamaMessages(request, prompt));
        body.put("stream", false);
        Map<String, Object> options = new HashMap<>();
        Double temperatureOverride = request != null ? request.getTemperature() : null;
        options.put("temperature", temperatureOverride != null ? temperatureOverride : temperature);
        body.put("options", options);
        List<ModelToolDefinition> tools = resolveOllamaTools(request);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", buildOllamaTools(tools));
        }
        return body;
    }

    /**
     * 将工具选择策略转换为可序列化对象。
     *
     * <p>输入：工具选择策略。
     * <p>输出：可序列化对象或 {@code null}。
     * <p>边界：模式为空时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * Object choice = buildToolChoiceValue(toolChoice);
     * }</pre>
     *
     * @param toolChoice 工具选择策略
     * @return 可序列化对象
     */
    private Object buildToolChoiceValue(ModelToolChoice toolChoice) {
        if (toolChoice == null || toolChoice.getMode() == null) {
            return null;
        }
        return switch (toolChoice.getMode()) {
            case AUTO -> "auto";
            case NONE -> "none";
            case REQUIRED -> "required";
            case SPECIFIED -> {
                if (!StringUtils.hasText(toolChoice.getToolName())) {
                    yield null;
                }
                Map<String, Object> function = new HashMap<>();
                function.put("name", toolChoice.getToolName());
                Map<String, Object> value = new HashMap<>();
                value.put("type", "function");
                value.put("function", function);
                yield value;
            }
        };
    }

    /**
     * 避免在 {@code Reactor} 非阻塞线程中直接阻塞调用。
     *
     * <p>输入：可调用任务。
     * <p>输出：任务返回值。
     * <p>边界：在非阻塞线程中会切换到异步执行。
     * <p>示例：
     * <pre>{@code
     * String result = executeBlocking(() -> "ok");
     * }</pre>
     */
    private <T> T executeBlocking(Callable<T> action) throws Exception {
        if (action == null) {
            return null;
        }
        if (!Schedulers.isInNonBlockingThread()) {
            return action.call();
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return action.call();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    /**
     * 构建原生接口的消息列表。
     *
     * <p>输入：模型请求与提示内容。
     * <p>输出：消息列表映射。
     * <p>边界：请求未包含消息时回退为单条用户消息。
     * <p>示例：
     * <pre>{@code
     * List<Map<String, Object>> messages = buildOllamaMessages(request, prompt);
     * }</pre>
     */
    private List<Map<String, Object>> buildOllamaMessages(ModelRequest request, String prompt) {
        if (request != null && request.getMessages() != null && !request.getMessages().isEmpty()) {
            List<Map<String, Object>> messages = new ArrayList<>();
            for (PromptMessage message : request.getMessages()) {
                if (message == null) {
                    continue;
                }
                // 原生接口不支持开发者角色，统一降级为系统角色。
                String role = toOllamaRole(message.getRole());
                messages.add(Map.of(
                        "role", role,
                        "content", message.getContent() == null ? "" : message.getContent()
                ));
            }
            return messages;
        }
        return List.of(Map.of("role", "user", "content", prompt));
    }

    /**
     * 将角色映射为原生接口支持的角色值。
     *
     * <p>输入：提示词角色。
     * <p>输出：角色字符串。
     * <p>边界：空角色默认返回 {@code user}。
     * <p>示例：
     * <pre>{@code
     * String role = toOllamaRole(PromptRole.SYSTEM);
     * }</pre>
     */
    private String toOllamaRole(PromptRole role) {
        if (role == null) {
            return "user";
        }
        return switch (role) {
            case SYSTEM -> "system";
            case DEVELOPER -> "system";
            case USER -> "user";
        };
    }

    /**
     * 根据工具选择策略过滤原生接口工具列表。
     *
     * <p>输入：模型请求对象。
     * <p>输出：工具定义列表。
     * <p>边界：未指定工具选择策略时返回全部工具。
     * <p>示例：
     * <pre>{@code
     * List<ModelToolDefinition> tools = resolveOllamaTools(request);
     * }</pre>
     */
    private List<ModelToolDefinition> resolveOllamaTools(ModelRequest request) {
        if (request == null || request.getTools() == null || request.getTools().isEmpty()) {
            return List.of();
        }
        ModelToolChoice choice = request.getToolChoice();
        if (choice == null || choice.getMode() == null) {
            return request.getTools();
        }
        return switch (choice.getMode()) {
            case NONE -> List.of();
            case SPECIFIED -> filterToolByName(request.getTools(), choice.getToolName());
            case REQUIRED, AUTO -> request.getTools();
        };
    }

    /**
     * 按名称过滤工具列表。
     *
     * <p>输入：工具列表与目标名称。
     * <p>输出：过滤后的工具列表。
     * <p>边界：名称为空时返回原列表。
     * <p>示例：
     * <pre>{@code
     * List<ModelToolDefinition> tools = filterToolByName(allTools, "search");
     * }</pre>
     */
    private List<ModelToolDefinition> filterToolByName(List<ModelToolDefinition> tools, String toolName) {
        if (!StringUtils.hasText(toolName) || tools == null || tools.isEmpty()) {
            return tools == null ? List.of() : tools;
        }
        List<ModelToolDefinition> filtered = new ArrayList<>();
        for (ModelToolDefinition tool : tools) {
            if (tool != null && StringUtils.hasText(tool.getName())
                    && tool.getName().equalsIgnoreCase(toolName)) {
                filtered.add(tool);
            }
        }
        return filtered;
    }

    /**
     * 构建原生接口的工具描述列表。
     *
     * <p>输入：工具定义列表。
     * <p>输出：可序列化的工具描述列表。
     * <p>边界：工具为空时返回空列表。
     * <p>示例：
     * <pre>{@code
     * List<Map<String, Object>> tools = buildOllamaTools(definitions);
     * }</pre>
     */
    private List<Map<String, Object>> buildOllamaTools(List<ModelToolDefinition> tools) {
        List<Map<String, Object>> payload = new ArrayList<>();
        if (tools == null) {
            return payload;
        }
        for (ModelToolDefinition tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.getName())) {
                continue;
            }
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.getName());
            if (StringUtils.hasText(tool.getDescription())) {
                function.put("description", tool.getDescription());
            }
            if (tool.getParameters() != null) {
                function.put("parameters", tool.getParameters());
            }
            Map<String, Object> item = new HashMap<>();
            item.put("type", "function");
            item.put("function", function);
            payload.add(item);
        }
        return payload;
    }

    /**
     * 构建兼容接口的工具描述列表。
     *
     * <p>输入：工具定义列表。
     * <p>输出：可序列化的工具描述列表。
     * <p>边界：工具为空时返回空列表。
     * <p>示例：
     * <pre>{@code
     * List<Map<String, Object>> tools = buildOpenAiTools(definitions);
     * }</pre>
     */
    private List<Map<String, Object>> buildOpenAiTools(List<ModelToolDefinition> tools) {
        List<Map<String, Object>> payload = new ArrayList<>();
        if (tools == null) {
            return payload;
        }
        for (ModelToolDefinition tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.getName())) {
                continue;
            }
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.getName());
            if (StringUtils.hasText(tool.getDescription())) {
                function.put("description", tool.getDescription());
            }
            if (tool.getParameters() != null) {
                function.put("parameters", tool.getParameters());
            } else {
                Map<String, Object> schema = new HashMap<>();
                schema.put("type", "object");
                schema.put("properties", Map.of());
                function.put("parameters", schema);
            }
            Map<String, Object> item = new HashMap<>();
            item.put("type", "function");
            item.put("function", function);
            payload.add(item);
        }
        return payload;
    }

    /**
     * 根据提示内容生成本地兜底响应。
     *
     * <p>输入：提示内容字符串。
     * <p>输出：结构化或简易响应内容。
     * <p>边界：提示为空时返回默认响应。
     * <p>示例：
     * <pre>{@code
     * String content = buildLocalResponse(prompt);
     * }</pre>
     */
    private String buildLocalResponse(String prompt) {
        if (prompt == null) {
            return "response:";
        }
        // 根据不同上下文标记选择本地兜底输出策略。
        if (prompt.contains(PLAN_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, PLAN_MARKER);
            return buildLocalPlan(context);
        }
        if (prompt.contains(REFLECTION_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, REFLECTION_MARKER);
            return buildLocalReflection(context);
        }
        if (prompt.contains(FINAL_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, FINAL_MARKER);
            return buildLocalFinal(context);
        }
        if (prompt.contains(RESEARCH_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, RESEARCH_MARKER);
            return buildLocalResearch(context);
        }
        if (prompt.contains(DEBATE_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, DEBATE_MARKER);
            return buildLocalDebate(context);
        }
        if (prompt.contains(MULTI_AGENT_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, MULTI_AGENT_MARKER);
            return buildLocalMultiAgent(context);
        }
        if (prompt.contains(COT_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, COT_MARKER);
            return buildLocalChainOfThought(context);
        }
        return "response:" + prompt;
    }

    /**
     * 解析模型请求中的提示内容。
     *
     * <p>输入：模型请求对象。
     * <p>输出：拼接后的提示字符串。
     * <p>边界：请求为空时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String prompt = resolvePrompt(request);
     * }</pre>
     */
    private String resolvePrompt(ModelRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getPrompt() != null) {
            return request.getPrompt();
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (PromptMessage message : request.getMessages()) {
            if (message == null) {
                continue;
            }
            builder.append(message.getRole() != null ? message.getRole().name() : "USER");
            builder.append(":");
            builder.append(message.getContent() == null ? "" : message.getContent());
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 构建兼容接口的消息列表。
     *
     * <p>输入：模型请求与提示内容。
     * <p>输出：消息列表映射。
     * <p>边界：无消息时回退为单条用户消息。
     * <p>示例：
     * <pre>{@code
     * List<Map<String, Object>> messages = buildMessages(request, prompt);
     * }</pre>
     */
    private List<Map<String, Object>> buildMessages(ModelRequest request, String prompt) {
        if (request != null && request.getMessages() != null && !request.getMessages().isEmpty()) {
            List<Map<String, Object>> messages = new java.util.ArrayList<>();
            for (PromptMessage message : request.getMessages()) {
                if (message == null) {
                    continue;
                }
                String role = toOpenAiRole(message.getRole());
                messages.add(Map.of(
                        "role", role,
                        "content", message.getContent() == null ? "" : message.getContent()
                ));
            }
            return messages;
        }
        return List.of(Map.of("role", "user", "content", prompt));
    }

    /**
     * 将角色映射为兼容接口支持的角色值。
     *
     * <p>输入：提示词角色。
     * <p>输出：角色字符串。
     * <p>边界：空角色默认返回 {@code user}。
     * <p>示例：
     * <pre>{@code
     * String role = toOpenAiRole(PromptRole.USER);
     * }</pre>
     */
    private String toOpenAiRole(PromptRole role) {
        if (role == null) {
            return "user";
        }
        return switch (role) {
            case SYSTEM -> "system";
            case DEVELOPER -> "developer";
            case USER -> "user";
        };
    }

    /**
     * 解析请求体中的工具数量。
     *
     * <p>输入：请求体映射。
     * <p>输出：工具数量。
     * <p>边界：工具字段为空时返回 {@code 0}。
     * <p>示例：
     * <pre>{@code
     * int toolCount = resolveToolCount(body);
     * }</pre>
     */
    private int resolveToolCount(Map<String, Object> body) {
        if (body == null) {
            return 0;
        }
        Object tools = body.get("tools");
        if (tools instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    /**
     * 解析调用接口的鉴权密钥。
     *
     * <p>输入：模型定义。
     * <p>输出：接口密钥或 {@code null}。
     * <p>边界：模型定义未配置时使用全局配置。
     * <p>示例：
     * <pre>{@code
     * String apiKey = resolveApiKey(definition);
     * }</pre>
     */
    private String resolveApiKey(ModelDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.getApiKey())) {
            return definition.getApiKey();
        }
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        return null;
    }

    /**
     * 从提示内容中解析标记后的 {@code JSON}。
     *
     * <p>输入：提示内容与标记字符串。
     * <p>输出：解析后的映射对象。
     * <p>边界：解析失败时返回空映射。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> context = parseJsonAfterMarker(prompt, PLAN_MARKER);
     * }</pre>
     */
    private Map<String, Object> parseJsonAfterMarker(String prompt, String marker) {
        int index = prompt.indexOf(marker);
        if (index < 0) {
            return Map.of();
        }
        String json = prompt.substring(index + marker.length()).trim();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 构建本地规划响应。
     *
     * <p>输入：上下文映射。
     * <p>输出：规划步骤的 {@code JSON} 字符串。
     * <p>边界：序列化失败时返回简化 {@code JSON}。
     * <p>示例：
     * <pre>{@code
     * String planJson = buildLocalPlan(context);
     * }</pre>
     */
    private String buildLocalPlan(Map<String, Object> context) {
        boolean disableTools = isToolsDisabled(context);
        String toolName = resolvePlanToolName(context);
        Map<String, Object> step = new HashMap<>();
        step.put("type", disableTools ? "LLM" : "TOOL");
        if (!disableTools && StringUtils.hasText(toolName)) {
            step.put("tool", toolName);
        }
        Map<String, Object> input = new HashMap<>();
        if (context.containsKey("query")) {
            input.put("query", context.get("query"));
        }
        if (context.containsKey("context")) {
            input.put("context", context.get("context"));
        }
        step.put("input", input);
        Map<String, Object> result = new HashMap<>();
        result.put("summary", "local-plan");
        result.put("steps", List.of(step));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"summary\":\"local-plan\",\"steps\":[]}";
        }
    }

    private String resolvePlanToolName(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object tool = context.get("tool");
        if (tool instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        Object toolName = context.get("toolName");
        if (toolName instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        if (context.get("context") instanceof Map<?, ?> inner) {
            Object innerTool = inner.get("tool");
            if (innerTool instanceof String value && StringUtils.hasText(value)) {
                return value;
            }
            Object innerToolName = inner.get("toolName");
            if (innerToolName instanceof String value && StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isToolsDisabled(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        if (isTruthy(context.get("disableTools"))) {
            return true;
        }
        if (context.get("context") instanceof Map<?, ?> inner && isTruthy(inner.get("disableTools"))) {
            return true;
        }
        ModelToolChoice choice = parseToolChoice(context.get("toolChoice"));
        if (choice == null && context.get("context") instanceof Map<?, ?> inner) {
            choice = parseToolChoice(inner.get("toolChoice"));
        }
        return choice != null && choice.getMode() == ModelToolChoice.Mode.NONE;
    }

    private ModelToolChoice parseToolChoice(Object raw) {
        if (raw instanceof ModelToolChoice choice) {
            return choice;
        }
        if (raw instanceof String value) {
            return ModelToolChoice.fromString(value);
        }
        if (raw instanceof Map<?, ?> map) {
            String mode = map.get("mode") != null ? map.get("mode").toString() : null;
            if (!StringUtils.hasText(mode) && map.get("type") != null) {
                mode = map.get("type").toString();
            }
            String name = map.get("toolName") != null ? map.get("toolName").toString() : null;
            if (!StringUtils.hasText(name) && map.get("name") != null) {
                name = map.get("name").toString();
            }
            if (StringUtils.hasText(mode) && "specified".equalsIgnoreCase(mode)) {
                return ModelToolChoice.specified(name);
            }
            ModelToolChoice parsed = ModelToolChoice.fromString(mode);
            if (parsed != null && parsed.getMode() == ModelToolChoice.Mode.SPECIFIED) {
                parsed.setToolName(name);
            }
            return parsed;
        }
        return null;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /**
     * 构建本地反思响应。
     *
     * <p>输入：上下文映射。
     * <p>输出：反思结果 {@code JSON} 字符串。
     * <p>边界：序列化失败时返回简化 {@code JSON}。
     * <p>示例：
     * <pre>{@code
     * String json = buildLocalReflection(context);
     * }</pre>
     */
    private String buildLocalReflection(Map<String, Object> context) {
        double score = 0.9;
        boolean retry = false;
        Object output = context.get("output");
        if (output == null || output.toString().isBlank()) {
            score = 0.4;
            retry = true;
        } else if (output.toString().toLowerCase(Locale.ROOT).contains("error")) {
            score = 0.5;
            retry = true;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        result.put("retry", retry);
        result.put("notes", retry ? "需要改进输出" : "输出质量良好");
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"score\":0.8,\"retry\":false,\"notes\":\"ok\"}";
        }
    }

    /**
     * 构建本地最终输出响应。
     *
     * <p>输入：上下文映射。
     * <p>输出：最终答复 {@code JSON} 字符串。
     * <p>边界：序列化失败时返回简化 {@code JSON}。
     * <p>示例：
     * <pre>{@code
     * String json = buildLocalFinal(context);
     * }</pre>
     */
    private String buildLocalFinal(Map<String, Object> context) {
        String answer = "已生成答复";
        Object query = context.get("query");
        if (query instanceof String value && !value.isBlank()) {
            answer = "针对问题\"" + value + "\"给出答复";
        }
        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("confidence", 0.6);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"answer\":\"ok\"}";
        }
    }

    /**
     * 构建本地研究响应。
     *
     * <p>输入：上下文映射。
     * <p>输出：研究结果 {@code JSON} 字符串。
     * <p>边界：序列化失败时返回简化 {@code JSON}。
     * <p>示例：
     * <pre>{@code
     * String json = buildLocalResearch(context);
     * }</pre>
     */
    private String buildLocalResearch(Map<String, Object> context) {
        Map<String, Object> citation = new HashMap<>();
        citation.put("title", "local-source");
        citation.put("url", "local");
        citation.put("snippet", "local research result");
        Map<String, Object> result = new HashMap<>();
        result.put("citations", List.of(citation));
        result.put("summary", "local research");
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"citations\":[],\"summary\":\"local\"}";
        }
    }

    /**
     * 构建本地辩论响应。
     *
     * <p>输入：上下文映射。
     * <p>输出：辩论结论 {@code JSON} 字符串。
     * <p>边界：序列化失败时返回简化 {@code JSON}。
     * <p>示例：
     * <pre>{@code
     * String json = buildLocalDebate(context);
     * }</pre>
     */
    private String buildLocalDebate(Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        result.put("conclusion", "local debate");
        result.put("round", 1);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"conclusion\":\"local\"}";
        }
    }

    /**
     * 构建本地多智能体响应。
     *
     * <p>输入：上下文映射。
     * <p>输出：多智能体摘要 {@code JSON} 字符串。
     * <p>边界：序列化失败时返回简化 {@code JSON}。
     * <p>示例：
     * <pre>{@code
     * String json = buildLocalMultiAgent(context);
     * }</pre>
     */
    private String buildLocalMultiAgent(Map<String, Object> context) {
        Map<String, Object> agent = new HashMap<>();
        agent.put("role", "planner");
        agent.put("name", "local-agent");
        Map<String, Object> result = new HashMap<>();
        result.put("team", List.of(agent));
        result.put("summary", "local multi-agent");
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"team\":[]}";
        }
    }

    /**
     * 本地链式推理的兜底输出，返回结构化 {@code JSON}。
     *
     * <p>输入：上下文映射。
     * <p>输出：链式推理结果 {@code JSON} 字符串。
     * <p>边界：序列化失败时返回简化 {@code JSON}。
     * <p>示例：
     * <pre>{@code
     * String json = buildLocalChainOfThought(context);
     * }</pre>
     *
     * @param context 上下文
     * @return {@code JSON} 字符串
     */
    private String buildLocalChainOfThought(Map<String, Object> context) {
        String question = context.get("question") instanceof String value ? value : "";
        Map<String, Object> result = new HashMap<>();
        result.put("stepSummary", "本地链式推理摘要");
        result.put("shouldContinue", false);
        result.put("finalAnswer", question.isBlank() ? "本地推理完成" : "已完成问题解析: " + question);
        result.put("confidence", 0.6);
        result.put("stopReason", "completed");
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"shouldContinue\":false,\"finalAnswer\":\"local\"}";
        }
    }

    /**
     * 从兼容接口响应中提取内容。
     *
     * <p>输入：响应映射。
     * <p>输出：内容字符串。
     * <p>边界：解析失败时返回空字符串。
     * <p>示例：
     * <pre>{@code
     * String content = extractContent(response);
     * }</pre>
     */
    private String extractContent(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> msg && msg.get("content") instanceof String content) {
                    return content;
                }
                Object content = choice.get("text");
                if (content instanceof String text) {
                    return text;
                }
            }
        }
        return "";
    }

    /**
     * 从兼容接口响应中提取令牌数量。
     *
     * <p>输入：响应映射与字段键。
     * <p>输出：令牌数量。
     * <p>边界：解析失败时返回 {@code 0}。
     * <p>示例：
     * <pre>{@code
     * int tokens = extractTokens(response, "prompt_tokens");
     * }</pre>
     */
    private int extractTokens(Map<String, Object> response, String key) {
        Object usage = response.get("usage");
        if (usage instanceof Map<?, ?> usageMap && usageMap.get(key) instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 从原生接口响应中提取内容。
     *
     * <p>输入：响应映射。
     * <p>输出：内容字符串。
     * <p>边界：解析失败时返回空字符串。
     * <p>示例：
     * <pre>{@code
     * String content = extractOllamaContent(response);
     * }</pre>
     */
    private String extractOllamaContent(Map<String, Object> response) {
        Object message = response.get("message");
        if (message instanceof Map<?, ?> msg && msg.get("content") instanceof String content) {
            return content;
        }
        Object responseText = response.get("response");
        if (responseText instanceof String text) {
            return text;
        }
        return "";
    }

    /**
     * 从原生接口响应中提取令牌数量。
     *
     * <p>输入：响应映射与字段键。
     * <p>输出：令牌数量。
     * <p>边界：解析失败时返回 {@code 0}。
     * <p>示例：
     * <pre>{@code
     * int tokens = extractOllamaTokens(response, "eval_count");
     * }</pre>
     */
    private int extractOllamaTokens(Map<String, Object> response, String key) {
        if (response != null && response.get(key) instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
