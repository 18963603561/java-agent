package com.example.agent.capabilities.context.compression.application;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.application.model.LlmCompressionCommand;
import com.example.agent.capabilities.context.compression.application.model.LlmCompressionResult;
import com.example.agent.capabilities.context.compression.parser.CompressionParseResult;
import com.example.agent.capabilities.context.compression.parser.CompressionResponseParser;
import com.example.agent.capabilities.context.compression.prompt.CompressionPromptBuilder;
import com.example.agent.capabilities.context.compression.summary.CompressionSummaryGuard;
import com.example.agent.capabilities.context.compression.summary.CompressionSummaryGuardResult;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.common.error.ErrorCodeException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM 压缩编排服务。
 */
@Component
public class LlmCompressionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LlmCompressionOrchestrator.class);

    private static final String PHASE = "context_compress";

    private final ModelInvocationService modelInvocationService;
    private final CompressionPromptBuilder promptBuilder;
    private final CompressionResponseParser responseParser;
    private final CompressionSummaryGuard summaryGuard;
    private final ContextCompressionProperties properties;

    public LlmCompressionOrchestrator(ModelInvocationService modelInvocationService,
                                      CompressionPromptBuilder promptBuilder,
                                      CompressionResponseParser responseParser,
                                      CompressionSummaryGuard summaryGuard,
                                      ContextCompressionProperties properties) {
        this.modelInvocationService = modelInvocationService;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.summaryGuard = summaryGuard;
        this.properties = properties;
    }

    /**
     * 执行 LLM 压缩。
     *
     * @param command 压缩命令
     * @return 压缩结果
     */
    public LlmCompressionResult compress(LlmCompressionCommand command) {
        LlmCompressionResult result = new LlmCompressionResult();
        if (command == null || command.getSnapshot() == null) {
            result.setSuccess(false);
            result.setFailureReason("INVALID_REQUEST");
            return result;
        }
        if (modelInvocationService == null || promptBuilder == null || responseParser == null || summaryGuard == null) {
            result.setSuccess(false);
            result.setFailureReason("DEPENDENCY_UNAVAILABLE");
            return result;
        }

        int retryLimit = resolveRetryLimit();
        long startNs = System.nanoTime();
        for (int attempt = 0; attempt <= retryLimit; attempt++) {
            result.setRetryCount(attempt);
            // 提示词构建：按统一模板生成压缩输入，隔离调用方与模板细节。
            String prompt = promptBuilder.buildPrompt(command);
            ModelRequest request = new ModelRequest(prompt, ModelScene.CONTEXT_COMPRESS);
            try {
                // 模型调用：将压缩请求发送至 context_compress 场景路由。
                ModelResponse response = modelInvocationService.invoke(
                        request,
                        ModelScene.CONTEXT_COMPRESS,
                        command.getTenantContext(),
                        command.getWorkflowId(),
                        new AtomicLong(0),
                        PHASE,
                        Map.of("sessionId", command.getSessionId(), "triggerReason", command.getTriggerReason()));
                // 响应解析：解析并校验模型输出 JSON。
                CompressionParseResult parsed = responseParser.parse(response != null ? response.getContent() : null);
                if (!parsed.isSuccess()) {
                    result.setFailureReason(parsed.getFailureReason());
                    if (shouldRetry(parsed.getFailureReason(), attempt, retryLimit)) {
                        continue;
                    }
                    break;
                }
                // 摘要治理：执行长度与脱敏治理，保证输出可安全回填。
                CompressionSummaryGuardResult guardResult = summaryGuard.guard(parsed.getSummary());
                if (!guardResult.isPassed()) {
                    result.setFailureReason(guardResult.getFailureReason());
                    break;
                }
                // 成功回填：记录结构化摘要与调用元信息。
                result.setSuccess(true);
                result.setSummary(guardResult.getSummary());
                result.setSummaryVersion(parsed.getSummaryVersion());
                result.setInputTokens(response != null ? response.getInputTokens() : null);
                result.setOutputTokens(response != null ? response.getOutputTokens() : null);
                result.setFallbackApplied(false);
                break;
            } catch (ErrorCodeException exception) {
                // 异常处理：标准错误码映射为 LLM 失败原因，并按策略决定是否重试。
                result.setFailureReason(mapFailureReason(exception.getErrorCode()));
                if (shouldRetry(result.getFailureReason(), attempt, retryLimit)) {
                    continue;
                }
                log.warn("LLM 压缩调用失败, workflowId={}, sessionId={}, attempt={}, errorCode={}",
                        command.getWorkflowId(), command.getSessionId(), attempt, exception.getErrorCode());
                break;
            } catch (RuntimeException exception) {
                // 异常处理：未知运行时异常按统一失败码处理，避免异常扩散到主链路。
                result.setFailureReason("LLM_UNKNOWN_ERROR");
                if (shouldRetry(result.getFailureReason(), attempt, retryLimit)) {
                    continue;
                }
                log.error("LLM 压缩运行异常, workflowId={}, sessionId={}, attempt={}",
                        command.getWorkflowId(), command.getSessionId(), attempt, exception);
                break;
            }
        }
        result.setDurationMs(Math.max(0, (System.nanoTime() - startNs) / 1_000_000));
        if (!result.isSuccess() && !StringUtils.hasText(result.getFailureReason())) {
            result.setFailureReason("LLM_EMPTY");
        }
        return result;
    }

    /**
     * 解析重试次数。
     */
    private int resolveRetryLimit() {
        if (properties == null || properties.getLlm() == null) {
            return 0;
        }
        int retry = properties.getLlm().getRetry();
        if (retry < 0) {
            return 0;
        }
        return Math.min(retry, 3);
    }

    /**
     * 判断是否继续重试。
     */
    private boolean shouldRetry(String failureReason, int attempt, int retryLimit) {
        if (attempt >= retryLimit) {
            return false;
        }
        if (!StringUtils.hasText(failureReason)) {
            return false;
        }
        return switch (failureReason) {
            case "LLM_TIMEOUT", "LLM_RATE_LIMITED", "LLM_UNAVAILABLE", "LLM_NETWORK_ERROR", "LLM_UNKNOWN_ERROR" -> true;
            default -> false;
        };
    }

    /**
     * 映射失败原因。
     */
    private String mapFailureReason(String errorCode) {
        if (!StringUtils.hasText(errorCode)) {
            return "LLM_UNKNOWN_ERROR";
        }
        return switch (errorCode) {
            case "MODEL_TIMEOUT" -> "LLM_TIMEOUT";
            case "MODEL_RATE_LIMITED" -> "LLM_RATE_LIMITED";
            case "MODEL_UNAVAILABLE" -> "LLM_UNAVAILABLE";
            case "MODEL_NETWORK_ERROR" -> "LLM_NETWORK_ERROR";
            default -> "LLM_UNKNOWN_ERROR";
        };
    }
}

