package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.config.ModelProviderHttpProperties;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.common.error.ErrorCodeException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * OpenAI 兼容接口适配器。
 */
@Component
public class OpenAiProviderAdapter implements ModelProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProviderAdapter.class);

    private final WebClient.Builder webClientBuilder;
    private final ModelProviderHttpProperties httpProperties;
    private final ModelRequestBodyBuilder requestBodyBuilder;
    private final ModelResponseExtractor responseExtractor;
    private final ProviderErrorMapper errorMapper;
    private final BlockingCallExecutor blockingCallExecutor;
    private final ModelApiKeyResolver apiKeyResolver;

    public OpenAiProviderAdapter(WebClient.Builder webClientBuilder,
                                 ModelProviderHttpProperties httpProperties,
                                 ModelRequestBodyBuilder requestBodyBuilder,
                                 ModelResponseExtractor responseExtractor,
                                 ProviderErrorMapper errorMapper,
                                 BlockingCallExecutor blockingCallExecutor,
                                 ModelApiKeyResolver apiKeyResolver) {
        this.webClientBuilder = webClientBuilder;
        this.httpProperties = httpProperties;
        this.requestBodyBuilder = requestBodyBuilder;
        this.responseExtractor = responseExtractor;
        this.errorMapper = errorMapper;
        this.blockingCallExecutor = blockingCallExecutor;
        this.apiKeyResolver = apiKeyResolver;
    }

    @Override
    public boolean supports(ModelDefinition definition) {
        if (definition == null) {
            return false;
        }
        ProviderType providerType = definition.resolveProviderType();
        boolean providerMatched = providerType == ProviderType.OPENAI || providerType == ProviderType.DEEPSEEK;
        if (!providerMatched) {
            return false;
        }
        return StringUtils.hasText(definition.getEndpoint()) && StringUtils.hasText(definition.getModelId());
    }

    @Override
    public ModelResponse invoke(ModelDefinition definition, ModelRequest request) {
        String baseUrl = definition != null ? definition.getEndpoint() : null;
        String modelId = definition != null ? definition.getModelId() : null;
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelId)) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    LlmErrorCode.MODEL_CONFIG_INVALID.getCode(),
                    "模型配置缺失");
        }

        Map<String, Object> body = requestBodyBuilder.buildOpenAiRequestBody(modelId, request, definition);
        int toolCount = resolveToolCount(body);
        long startNs = System.nanoTime();
        log.info("兼容接口调用开始, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount);

        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        WebClient.RequestBodySpec requestSpec = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON);
        String resolvedApiKey = apiKeyResolver.resolve(definition);
        if (StringUtils.hasText(resolvedApiKey)) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + resolvedApiKey);
        }
        WebClient.RequestBodySpec finalRequestSpec = requestSpec;

        Map<String, Object> response;
        try {
            response = blockingCallExecutor.execute(() -> finalRequestSpec.bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            result -> result.bodyToMono(String.class)
                                    .defaultIfEmpty("model_call_failed")
                                    .flatMap(message -> {
                                        HttpStatus status = HttpStatus.resolve(result.statusCode().value());
                                        return Mono.error(errorMapper.mapModelError(status, message));
                                    }))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .timeout(Duration.ofSeconds(httpProperties.getTimeoutSeconds()))
                    .block());
        } catch (Exception ex) {
            log.error("兼容接口调用异常, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount, ex);
            throw errorMapper.mapThrowable(ex);
        }

        if (response == null) {
            log.error("兼容接口调用失败, 模型标识={}, 服务地址={}, 工具数={}", modelId, baseUrl, toolCount);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    LlmErrorCode.MODEL_UNAVAILABLE.getCode(),
                    "模型调用失败");
        }

        String content = responseExtractor.extractOpenAiContent(response);
        int inputTokens = responseExtractor.extractOpenAiTokens(response, "prompt_tokens");
        int outputTokens = responseExtractor.extractOpenAiTokens(response, "completion_tokens");
        String responseModel = response.get("model") instanceof String value ? value : modelId;
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        log.info("兼容接口调用完成, 模型标识={}, 服务地址={}, 延迟毫秒={}, 输入令牌={}, 输出令牌={}",
                responseModel, baseUrl, durationMs, inputTokens, outputTokens);
        return new ModelResponse(responseModel, content, inputTokens, outputTokens);
    }

    private int resolveToolCount(Map<String, Object> body) {
        if (body == null) {
            return 0;
        }
        Object tools = body.get("tools");
        if (tools instanceof java.util.List<?> list) {
            return list.size();
        }
        return 0;
    }
}
