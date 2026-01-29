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
 */
@Component
public class DefaultModelProvider implements ModelProvider {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultModelProvider.class);

    /**
     * 规划上下文标记。
     */
    private static final String PLAN_MARKER = "PLAN_CONTEXT_JSON:";
    /**
     * 反思上下文标记。
     */
    private static final String REFLECTION_MARKER = "REFLECTION_CONTEXT_JSON:";
    /**
     * 最终输出上下文标记。
     */
    private static final String FINAL_MARKER = "FINAL_CONTEXT_JSON:";
    /**
     * 研究上下文标记。
     */
    private static final String RESEARCH_MARKER = "RESEARCH_CONTEXT_JSON:";
    /**
     * 辩论上下文标记。
     */
    private static final String DEBATE_MARKER = "DEBATE_CONTEXT_JSON:";
    /**
     * 多智能体上下文标记。
     */
    private static final String MULTI_AGENT_MARKER = "MULTI_AGENT_CONTEXT_JSON:";
    /**
     * 链式推理上下文标记。
     */
    private static final String COT_MARKER = "COT_CONTEXT_JSON:";

    /**
     * 序列化工具。
     */
    private final ObjectMapper objectMapper;
    /**
     * 调用客户端构建器。
     */
    private final WebClient.Builder webClientBuilder;

    /**
     * 请求超时时间秒数。
     */
    @Value("${agent.model.http.timeout-seconds:30}")
    private long timeoutSeconds;

    /**
     * 采样温度默认值。
     */
    @Value("${agent.model.http.temperature:0.2}")
    private double temperature;

    /**
     * 兼容接口密钥，留空表示不需要鉴权。
     */
    @Value("${agent.model.http.api-key:}")
    private String apiKey;

    /**
     * 构造模型提供方。
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

    private ModelResponse invokeLocal(ModelDefinition definition, ModelRequest request) {
        String prompt = resolvePrompt(request);
        String modelId = definition != null ? definition.getModelId() : "local";
        int inputTokens = prompt != null ? prompt.length() : 0;
        String content = buildLocalResponse(prompt);
        int outputTokens = content.length();
        return new ModelResponse(modelId, content, inputTokens, outputTokens);
    }

    private ModelResponse invokeOpenAiCompatible(ModelDefinition definition, ModelRequest request) {
        String baseUrl = definition != null ? definition.getEndpoint() : null;
        String modelId = definition != null ? definition.getModelId() : null;
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelId)) {
            log.warn("模型配置缺失，回退本地输出, 服务地址={}, 模型标识={}", baseUrl, modelId);
            return invokeLocal(definition, request);
        }

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
        String content = extractContent(response);
        int inputTokens = extractTokens(response, "prompt_tokens");
        int outputTokens = extractTokens(response, "completion_tokens");
        String responseModel = response.get("model") instanceof String value ? value : modelId;
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        log.info("兼容接口调用完成, 模型标识={}, 服务地址={}, 延迟毫秒={}, 输入令牌={}, 输出令牌={}",
                responseModel, baseUrl, durationMs, inputTokens, outputTokens);
        return new ModelResponse(responseModel, content, inputTokens, outputTokens);
    }

    private ModelResponse invokeOllama(ModelDefinition definition, ModelRequest request) {
        String baseUrl = definition != null ? definition.getEndpoint() : null;
        String modelId = definition != null ? definition.getModelId() : null;
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelId)) {
            log.warn("模型配置缺失，回退本地输出, 服务地址={}, 模型标识={}", baseUrl, modelId);
            return invokeLocal(definition, request);
        }

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
     * 避免在 Reactor 非阻塞线程中直接阻塞调用。
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

    private String buildLocalResponse(String prompt) {
        if (prompt == null) {
            return "response:";
        }
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

    private String resolveApiKey(ModelDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.getApiKey())) {
            return definition.getApiKey();
        }
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        return null;
    }

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

    private String buildLocalPlan(Map<String, Object> context) {
        String toolName = "demo_tool";
        if (context.get("context") instanceof Map<?, ?> inner) {
            Object tool = inner.get("tool");
            if (tool instanceof String name && !name.isBlank()) {
                toolName = name;
            }
        }
        Map<String, Object> step = new HashMap<>();
        step.put("type", "TOOL");
        step.put("tool", toolName);
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
     * 本地链式推理的兜底输出，返回结构化 JSON。
     *
     * @param context 上下文
     * @return JSON 字符串
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

    private int extractTokens(Map<String, Object> response, String key) {
        Object usage = response.get("usage");
        if (usage instanceof Map<?, ?> usageMap && usageMap.get(key) instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

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

    private int extractOllamaTokens(Map<String, Object> response, String key) {
        if (response != null && response.get(key) instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
