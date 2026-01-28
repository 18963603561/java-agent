package com.example.agent.model;

import com.example.agent.common.ErrorCodeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

/**
 * 默认模型提供商实现，支持本地规则输出与 OpenAI 兼容调用。
 */
@Component
public class DefaultModelProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultModelProvider.class);

    private static final String PLAN_MARKER = "PLAN_CONTEXT_JSON:";
    private static final String REFLECTION_MARKER = "REFLECTION_CONTEXT_JSON:";
    private static final String FINAL_MARKER = "FINAL_CONTEXT_JSON:";
    private static final String RESEARCH_MARKER = "RESEARCH_CONTEXT_JSON:";
    private static final String DEBATE_MARKER = "DEBATE_CONTEXT_JSON:";
    private static final String MULTI_AGENT_MARKER = "MULTI_AGENT_CONTEXT_JSON:";
    private static final String COT_MARKER = "COT_CONTEXT_JSON:";

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${agent.model.http.timeout-seconds:30}")
    private long timeoutSeconds;

    @Value("${agent.model.http.temperature:0.2}")
    private double temperature;

    public DefaultModelProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public ModelResponse invoke(ModelDefinition definition, ModelRequest request) {
        String provider = definition != null ? definition.getProvider() : null;
        if (provider != null) {
            String normalized = provider.toLowerCase(Locale.ROOT);
            if (normalized.contains("openai") || normalized.contains("deepseek")) {
                return invokeOpenAiCompatible(definition, request);
            }
        }
        return invokeLocal(definition, request);
    }

    private ModelResponse invokeLocal(ModelDefinition definition, ModelRequest request) {
        String prompt = request != null ? request.getPrompt() : null;
        String modelId = definition != null ? definition.getModelId() : "local";
        int inputTokens = prompt != null ? prompt.length() : 0;
        String content = buildLocalResponse(prompt);
        int outputTokens = content.length();
        return new ModelResponse(modelId, content, inputTokens, outputTokens);
    }

    private ModelResponse invokeOpenAiCompatible(ModelDefinition definition, ModelRequest request) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        String baseUrl = System.getenv("DEEPSEEK_BASE_URL");
        String modelId = System.getenv("DEEPSEEK_MODEL");
        if (!StringUtils.hasText(baseUrl) && definition != null) {
            baseUrl = definition.getEndpoint();
        }
        if (!StringUtils.hasText(modelId) && definition != null) {
            modelId = definition.getModelId();
        }
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelId) || !StringUtils.hasText(apiKey)) {
            log.warn("模型配置缺失，回退本地输出, baseUrl={}, modelId={}, apiKeyPresent={}",
                    baseUrl, modelId, StringUtils.hasText(apiKey));
            return invokeLocal(definition, request);
        }

        Map<String, Object> body = buildOpenAiRequestBody(modelId, request);

        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        Map<String, Object> response = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        result -> result.bodyToMono(String.class)
                                .defaultIfEmpty("model_call_failed")
                                .flatMap(message -> Mono.error(new ErrorCodeException(
                                        HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", message))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

        if (response == null) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "模型调用失败");
        }
        String content = extractContent(response);
        int inputTokens = extractTokens(response, "prompt_tokens");
        int outputTokens = extractTokens(response, "completion_tokens");
        String responseModel = response.get("model") instanceof String value ? value : modelId;
        return new ModelResponse(responseModel, content, inputTokens, outputTokens);
    }

    /**
     * 构建 OpenAI 兼容请求体，工具为空时保持纯 prompt 行为。
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
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        if (request != null && request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
            Object toolChoice = buildToolChoiceValue(request.getToolChoice());
            if (toolChoice != null) {
                body.put("toolChoice", toolChoice);
            }
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
                Map<String, Object> value = new HashMap<>();
                value.put("type", "specified");
                value.put("name", toolChoice.getToolName());
                yield value;
            }
        };
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
}
