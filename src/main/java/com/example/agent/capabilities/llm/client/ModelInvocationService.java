package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.client.events.LlmEventPayloadMapper;
import com.example.agent.capabilities.llm.client.events.LlmInvocationMetadata;
import com.example.agent.capabilities.llm.client.events.LlmParseEventPayload;
import com.example.agent.capabilities.llm.client.events.LlmPromptEventPayload;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRequest;
import com.example.agent.capabilities.llm.provider.ModelResponse;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.capabilities.llm.provider.ModelScene;
import com.example.agent.capabilities.llm.provider.ProviderErrorMapper;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.store.RawResultStore;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 妯″瀷璋冪敤鏈嶅姟锛岃礋璐ｆā鍨嬭矾鐢便€佽皟鐢ㄤ笌浜嬩欢鍙戝竷銆? *
 * <p>鐢ㄩ€旓細灏佽妯″瀷璋冪敤閾捐矾锛岀粺涓€璁板綍鎻愮ず璇嶄笌杈撳嚭浜嬩欢銆? * <p>杈撳叆锛氭ā鍨嬭姹傘€佸満鏅€佺鎴蜂笂涓嬫枃銆佸伐浣滄祦鏍囪瘑銆侀樁娈典笌鍏冩暟鎹€? * <p>杈撳嚭锛氭ā鍨嬪搷搴斿璞°€? * <p>杈圭晫锛氬叧閿叆鍙傜己澶辨椂鎶涘嚭鏄庣‘寮傚父锛涜皟鐢ㄥ紓甯哥粺涓€灏佽閿欒鐮併€? */
@Service
public class ModelInvocationService {

    private static final Logger log = LoggerFactory.getLogger(ModelInvocationService.class);
    /**
     * rawRef 鎸傝浇鐨勬渶澶у唴瀹归暱搴︼紝瓒呰繃闃堝€煎悗浠呬繚鐣欐棩蹇楋紝涓嶅啀鎵ц瀛樺偍銆?     */
    private static final int RAW_REF_MAX_CONTENT_CHARS = 1_000_000;

    private final LlmClient llmClient;
    private final ModelRouter modelRouter;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final RawResultStore rawResultStore;
    private final LlmEventPayloadMapper eventPayloadMapper;
    private final ValidationSupport validationSupport;
    private final ProviderErrorMapper providerErrorMapper;
    private final MetricsPublisher metricsPublisher;

    @Value("${agent.llm.event.publish-enabled:true}")
    private boolean llmEventPublishEnabled;

    public ModelInvocationService(LlmClient llmClient,
                                  ModelRouter modelRouter,
                                  ApplicationEventPublisher eventPublisher,
                                  EventStreamService eventStreamService,
                                  ObjectProvider<RawResultStore> rawResultStoreProvider,
                                  LlmEventPayloadMapper eventPayloadMapper,
                                  ValidationSupport validationSupport,
                                  ProviderErrorMapper providerErrorMapper,
                                  ObjectProvider<MetricsPublisher> metricsPublisherProvider) {
        this.llmClient = llmClient;
        this.modelRouter = modelRouter;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.rawResultStore = rawResultStoreProvider.getIfAvailable();
        this.eventPayloadMapper = eventPayloadMapper;
        this.validationSupport = validationSupport;
        this.providerErrorMapper = providerErrorMapper;
        this.metricsPublisher = metricsPublisherProvider.getIfAvailable();
        if (this.rawResultStore == null) {
            log.warn("鏈娴嬪埌 RawResultStore 瀹炵幇锛屾ā鍨嬭緭鍑哄皢璺宠繃 rawRef 鎸傝浇");
        }
        if (this.metricsPublisher == null) {
            log.warn("鏈娴嬪埌 MetricsPublisher 瀹炵幇锛屾ā鍨嬭皟鐢ㄩ敊璇寚鏍囧皢璺宠繃鍙戝竷");
        }
    }

    /**
     * 璋冪敤妯″瀷骞跺彂甯冩彁绀鸿瘝涓庤緭鍑轰簨浠躲€?     *
     * @param request 妯″瀷璇锋眰
     * @param scene 鍦烘櫙
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param seqCounter 浜嬩欢搴忓彿璁℃暟鍣?     * @param phase 闃舵鏍囪瘑
     * @param metadata 鍏冩暟鎹?     * @return 妯″瀷鍝嶅簲
     */
    public ModelResponse invoke(ModelRequest request,
                                ModelScene scene,
                                TenantContext tenantContext,
                                String workflowId,
                                AtomicLong seqCounter,
                                String phase,
                                Map<String, Object> metadata) {
        ModelScene resolvedScene = scene != null ? scene : ModelScene.CHEAP;
        String resolvedPhase = validationSupport.normalizeText(phase, "unknown");
        ModelRequest safeRequest = request != null ? request : new ModelRequest();
        safeRequest.setScene(resolvedScene);
        ModelDefinition definition = modelRouter.route(resolvedScene);
        String modelId = definition != null ? definition.getModelId() : null;
        String provider = definition != null ? definition.getProvider() : null;
        String traceId = tenantContext != null ? tenantContext.getTraceId() : null;

        Map<String, Object> runtimeMetadata = metadata != null ? new java.util.LinkedHashMap<>(metadata)
                : new java.util.LinkedHashMap<>();
        runtimeMetadata.putIfAbsent("scene", resolvedScene.name());
        if (provider != null && !provider.isBlank()) {
            runtimeMetadata.putIfAbsent("provider", provider);
        }
        String promptScene = resolvePromptScene(resolvedPhase, runtimeMetadata);
        PromptTrace trace = PromptTrace.fromPrompt(promptScene, safeRequest.getPrompt());
        runtimeMetadata.put("promptTrace", trace);

        publishPromptEvent(tenantContext, workflowId, seqCounter, resolvedPhase, modelId, safeRequest, runtimeMetadata);

        long startNs = System.nanoTime();
        try {
            log.info("妯″瀷璋冪敤寮€濮? tenantId={}, workflowId={}, scene={}, modelId={}, phase={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    resolvedScene,
                    modelId,
                    resolvedPhase);
            ModelResponse response = llmClient.generate(safeRequest);
            attachRawRef(response, resolvedScene, resolvedPhase);
            LlmExecutionResult executionResult = LlmExecutionResult.success(
                    traceId,
                    resolvedScene.name(),
                    workflowId,
                    provider,
                    response);
            publishOutputExecutionEvent(tenantContext, workflowId, seqCounter, resolvedPhase, executionResult, runtimeMetadata);
            log.info("妯″瀷璋冪敤瀹屾垚, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}, latencyMs={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    resolvedScene,
                    response != null ? response.getModelId() : modelId,
                    resolvedPhase,
                    (System.nanoTime() - startNs) / 1_000_000);
            return response;
        } catch (ErrorCodeException ex) {
            LlmExecutionResult executionResult = buildFailureResult(traceId,
                    workflowId,
                    resolvedScene,
                    provider,
                    modelId,
                    ex);
            publishOutputExecutionEvent(tenantContext, workflowId, seqCounter, resolvedPhase, executionResult, runtimeMetadata);
            boolean retriable = executionResult.getFailure() != null && executionResult.getFailure().isRetriable();
            log.error("妯″瀷璋冪敤澶辫触, tenantId={}, workflowId={}, scene={}, phase={}, traceId={}, provider={}, modelId={}, errorCode={}, retriable={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    resolvedScene,
                    resolvedPhase,
                    traceId,
                    provider,
                    modelId,
                    ex.getErrorCode(),
                    retriable,
                    ex);
            throw ex;
        } catch (Exception ex) {
            ErrorCodeException mapped = providerErrorMapper.mapThrowable(ex);
            LlmExecutionResult executionResult = buildFailureResult(traceId,
                    workflowId,
                    resolvedScene,
                    provider,
                    modelId,
                    mapped);
            publishOutputExecutionEvent(tenantContext, workflowId, seqCounter, resolvedPhase, executionResult, runtimeMetadata);
            boolean retriable = executionResult.getFailure() != null && executionResult.getFailure().isRetriable();
            log.error("妯″瀷璋冪敤寮傚父, tenantId={}, workflowId={}, scene={}, phase={}, traceId={}, provider={}, modelId={}, errorCode={}, retriable={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    resolvedScene,
                    resolvedPhase,
                    traceId,
                    provider,
                    modelId,
                    mapped.getErrorCode(),
                    retriable,
                    ex);
            throw mapped;
        }
    }

    /**
     * 璋冪敤妯″瀷骞朵娇鐢ㄥ己绫诲瀷鍏冩暟鎹€?     *
     * @param request 妯″瀷璇锋眰
     * @param scene 鍦烘櫙
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param seqCounter 浜嬩欢搴忓彿璁℃暟鍣?     * @param phase 闃舵鏍囪瘑
     * @param metadata 璋冪敤鍏冩暟鎹璞?     * @return 妯″瀷鍝嶅簲
     */
    public ModelResponse invokeWithMetadata(ModelRequest request,
                                            ModelScene scene,
                                            TenantContext tenantContext,
                                            String workflowId,
                                            AtomicLong seqCounter,
                                            String phase,
                                            LlmInvocationMetadata metadata) {
        Map<String, Object> runtimeMetadata = metadata != null ? metadata.toMetadataMap() : Map.of();
        return invoke(request, scene, tenantContext, workflowId, seqCounter, phase, runtimeMetadata);
    }

    /**
     * 鍙戝竷鎻愮ず璇嶄簨浠躲€?     *
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param seqCounter 搴忓彿璁℃暟鍣?     * @param phase 闃舵鏍囪瘑
     * @param modelId 妯″瀷鏍囪瘑
     * @param request 妯″瀷璇锋眰
     * @param metadata 鍏冩暟鎹?     */
    private void publishPromptEvent(TenantContext tenantContext,
                                    String workflowId,
                                    AtomicLong seqCounter,
                                    String phase,
                                    String modelId,
                                    ModelRequest request,
                                    Map<String, Object> metadata) {
        if (tenantContext == null || !hasWorkflowId(workflowId)) {
            log.debug("璺宠繃 LLM 鎻愮ず璇嶄簨浠跺彂甯? tenantContext/workflowId 缂哄け, workflowId={}", workflowId);
            return;
        }
        if (!llmEventPublishEnabled) {
            log.info("LLM 鎻愮ず璇嶄簨浠跺彂甯冨凡鍏抽棴, tenantId={}, workflowId={}, phase={}, modelId={}",
                    tenantContext.getTenantId(), workflowId, phase, modelId);
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        List<String> messageRoles = resolveMessageRoles(request);
        Integer messageCount = messageRoles.isEmpty() ? null : messageRoles.size();
        LlmPromptEventPayload payload = new LlmPromptEventPayload(
                phase,
                request != null && request.getScene() != null ? request.getScene().name() : null,
                metadata != null && metadata.get("provider") != null ? String.valueOf(metadata.get("provider")) : null,
                modelId,
                workflowId,
                tenantContext.getTraceId(),
                LlmResultStatus.SUCCESS.toPayloadValue(),
                null,
                request != null ? request.getPrompt() : null,
                messageCount,
                messageRoles,
                trace,
                metadata);
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_PROMPT, eventPayloadMapper.toMap(payload));
    }

    /**
     * 鍙戝竷杈撳嚭瑙ｆ瀽浜嬩欢銆?     *
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param seqCounter 搴忓彿璁℃暟鍣?     * @param phase 闃舵鏍囪瘑
     * @param response 妯″瀷鍝嶅簲
     * @param metadata 鍏冩暟鎹?     */
    private void publishOutputExecutionEvent(TenantContext tenantContext,
                                             String workflowId,
                                             AtomicLong seqCounter,
                                             String phase,
                                             LlmExecutionResult executionResult,
                                             Map<String, Object> metadata) {
        if (tenantContext == null || !hasWorkflowId(workflowId) || executionResult == null) {
            return;
        }
        if (!llmEventPublishEnabled) {
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        LlmExecutionUsage usage = executionResult.getUsage();
        LlmExecutionFailure failure = executionResult.getFailure();
        LlmParseEventPayload payload = new LlmParseEventPayload(
                phase,
                executionResult.getScene(),
                executionResult.getProvider(),
                executionResult.getModelId(),
                workflowId,
                executionResult.getTraceId(),
                executionResult.getStatus() != null ? executionResult.getStatus().toPayloadValue() : null,
                executionResult.getRawRef(),
                executionResult.getRawText(),
                usage != null ? usage.getInputTokens() : null,
                usage != null ? usage.getOutputTokens() : null,
                usage != null ? usage.getTotalTokens() : null,
                failure != null ? failure.getErrorCode() : null,
                failure != null ? failure.getErrorMessage() : null,
                failure != null ? failure.getExceptionType() : null,
                failure != null ? failure.isRetriable() : null,
                trace,
                metadata);
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_PARSE, eventPayloadMapper.toMap(payload));
    }

    /**
     * 鍙戝竷缁熶竴浜嬩欢璁板綍銆?     *
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param seq 搴忓彿
     * @param type 浜嬩欢绫诲瀷
     * @param payload 浜嬩欢杞借嵎
     */
    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              long seq,
                              EventType type,
                              Map<String, Object> payload) {
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }

    /**
     * 鑾峰彇涓嬩竴鏉′簨浠跺簭鍙枫€?     *
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param seqCounter 澶栭儴搴忓彿璁℃暟鍣?     * @return 涓嬩竴搴忓彿
     */
    private long nextSeq(TenantContext tenantContext, String workflowId, AtomicLong seqCounter) {
        if (seqCounter != null) {
            return seqCounter.incrementAndGet();
        }
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
    }

    /**
     * 璁板綍鎻愮ず璇嶈拷韪俊鎭苟鍙戝竷浜嬩欢銆?     *
     * @param trace 杩借釜淇℃伅
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param seqCounter 搴忓彿璁℃暟鍣?     * @param phase 闃舵
     * @param modelId 妯″瀷鏍囪瘑
     */
    public void recordPromptTrace(PromptTrace trace,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  String phase,
                                  String modelId) {
        if (trace == null) {
            return;
        }
        log.info("鎻愮ず璇嶈拷韪褰? tenantId={}, workflowId={}, phase={}, modelId={}, promptScene={}, promptId={}, promptChars={}, tokensEstimate={}, parseSuccess={}, parseErrorType={}, repairAttempted={}, repairSuccess={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                phase,
                modelId,
                trace.getPromptScene(),
                trace.getPromptId(),
                trace.getPromptChars(),
                trace.getPromptTokensEstimate(),
                trace.getParseSuccess(),
                trace.getParseErrorType(),
                trace.getRepairAttempted(),
                trace.getRepairSuccess());
        if (tenantContext == null || !hasWorkflowId(workflowId) || !llmEventPublishEnabled) {
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        LlmParseEventPayload payload = new LlmParseEventPayload(
                phase,
                null,
                null,
                modelId,
                workflowId,
                tenantContext.getTraceId(),
                LlmResultStatus.SUCCESS.toPayloadValue(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                trace,
                Map.of());
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_PARSE, eventPayloadMapper.toMap(payload));
    }

    private String resolvePromptScene(String phase, Map<String, Object> metadata) {
        if (metadata != null) {
            Object value = metadata.get("promptScene");
            if (value instanceof String scene && !scene.isBlank()) {
                return scene.trim();
            }
        }
        if (phase == null) {
            return "unknown";
        }
        return switch (phase) {
            case "plan" -> "planner";
            case "reflect" -> "reflect";
            case "finalize" -> "final";
            case "react_think" -> "react";
            case "cot" -> "cot";
            case "research" -> "research";
            case "debate" -> "debate";
            case "multi_agent" -> "multiagent";
            case "json_repair" -> "repair";
            default -> phase;
        };
    }

    private boolean hasWorkflowId(String workflowId) {
        return workflowId != null && !workflowId.isBlank();
    }

    private List<String> resolveMessageRoles(ModelRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return List.of();
        }
        return request.getMessages().stream()
                .map(message -> message != null && message.getRole() != null ? message.getRole().name() : "USER")
                .toList();
    }

    /**
     * 涓烘ā鍨嬭緭鍑烘寕杞藉師濮嬬粨鏋滃紩鐢ㄣ€?     *
     * @param response 妯″瀷鍝嶅簲
     * @param scene 璋冪敤鍦烘櫙
     * @param phase 闃舵鏍囪瘑
     */
    private void attachRawRef(ModelResponse response, ModelScene scene, String phase) {
        if (response == null || rawResultStore == null) {
            return;
        }
        String content = response.getContent();
        if (content == null || content.isBlank()) {
            log.debug("模型输出 rawRef 跳过挂载, reason=empty_content, scene={}, phase={}, modelId={}",
                    scene,
                    phase,
                    response.getModelId());
            return;
        }
        if (content.length() > RAW_REF_MAX_CONTENT_CHARS) {
            log.warn("模型输出 rawRef 跳过挂载, reason=content_too_large, scene={}, phase={}, modelId={}, contentChars={}, maxChars={}",
                    scene,
                    phase,
                    response.getModelId(),
                    content.length(),
                    RAW_REF_MAX_CONTENT_CHARS);
            return;
        }
        String source = "model";
        if (scene != null) {
            source = source + ":" + scene.name().toLowerCase();
        }
        if (phase != null && !phase.isBlank()) {
            source = source + ":" + phase;
        }
        RawRef rawRef;
        try {
            rawRef = rawResultStore.store(source, content, "text/plain");
        } catch (Exception ex) {
            log.error("模型输出 rawRef 挂载异常, reason=store_exception, scene={}, phase={}, modelId={}, source={}",
                    scene,
                    phase,
                    response.getModelId(),
                    source,
                    ex);
            return;
        }
        if (rawRef != null && rawRef.getRefId() != null && !rawRef.getRefId().isBlank()) {
            response.setRawRef(rawRef.getRefId());
            return;
        }
        if (rawRef != null && rawRef.getKey() != null && !rawRef.getKey().isBlank()) {
            response.setRawRef(rawRef.getKey());
        }
        if (response.getRawRef() == null || response.getRawRef().isBlank()) {
            log.warn("模型输出 rawRef 挂载失败, reason=empty_ref, scene={}, phase={}, modelId={}",
                    scene,
                    phase,
                    response.getModelId());
        }
    }

    private LlmExecutionResult buildFailureResult(String traceId,
                                                  String workflowId,
                                                  ModelScene scene,
                                                  String provider,
                                                  String modelId,
                                                  ErrorCodeException exception) {
        String errorCode = exception != null ? exception.getErrorCode() : null;
        String errorMessage = exception != null ? exception.getReason() : "妯″瀷璋冪敤澶辫触";
        boolean retriable = providerErrorMapper.isRetriable(errorCode);
        LlmExecutionFailure failure = new LlmExecutionFailure(
                errorCode,
                errorMessage,
                exception != null ? exception.getClass().getSimpleName() : null,
                retriable);
        recordFailureMetrics(errorCode, provider, modelId, retriable);
        return LlmExecutionResult.failed(
                traceId,
                scene != null ? scene.name() : null,
                workflowId,
                provider,
                modelId,
                failure);
    }

    private void recordFailureMetrics(String errorCode, String provider, String modelId, boolean retriable) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.incrementWithTags("llm_invocation_error_total",
                "errorCode", normalizeTag(errorCode),
                "provider", normalizeTag(provider),
                "model", normalizeTag(modelId),
                "retriable", String.valueOf(retriable));
    }

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }

    private String resolveEventScene(Map<String, Object> metadata, String modelId) {
        if (metadata != null && metadata.get("scene") != null) {
            return String.valueOf(metadata.get("scene"));
        }
        if (metadata != null && metadata.get("promptScene") != null) {
            return String.valueOf(metadata.get("promptScene"));
        }
        if (modelId != null && !modelId.isBlank()) {
            return "unknown";
        }
        return null;
    }
}


