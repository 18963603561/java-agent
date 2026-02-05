package com.example.agent.runtime;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.runtime.model.result.StepResult;
import com.example.agent.runtime.model.result.StepResultDigest;
import com.example.agent.runtime.model.result.StepResultError;
import com.example.agent.runtime.model.result.StepResultMeta;
import com.example.agent.runtime.model.result.StepResultRaw;
import com.example.agent.runtime.model.result.StepResultRefSet;
import com.example.agent.runtime.model.result.StepResultSummary;
import com.example.agent.runtime.model.result.StepResultTiming;
import com.example.agent.runtime.raw.RawRef;
import com.example.agent.runtime.structured.StructuredExtractorRegistry;
import com.example.agent.runtime.structured.StructuredRefs;
import com.example.agent.runtime.structured.StructuredResult;
import com.example.agent.streaming.EventStreamService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 步骤运行时服务，负责步骤记录落地与事件发布。
 */
@Service
public class StepRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(StepRuntimeService.class);

    private final StepStateMachine stateMachine = new StepStateMachine();
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final MetricsPublisher metricsPublisher;
    private final TracingPublisher tracingPublisher;
    private final StepRecordRepository stepRecordRepository;
    private final StepOutputSummaryBuilder stepOutputSummaryBuilder;
    private final RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder;
    private final StructuredExtractorRegistry structuredExtractorRegistry;

    public StepRuntimeService(ApplicationEventPublisher eventPublisher,
                              EventStreamService eventStreamService,
                              MetricsPublisher metricsPublisher,
                              TracingPublisher tracingPublisher,
                              StepRecordRepository stepRecordRepository,
                              StepOutputSummaryBuilder stepOutputSummaryBuilder,
                              RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder,
                              StructuredExtractorRegistry structuredExtractorRegistry) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.metricsPublisher = metricsPublisher;
        this.tracingPublisher = tracingPublisher;
        this.stepRecordRepository = stepRecordRepository;
        this.stepOutputSummaryBuilder = stepOutputSummaryBuilder;
        this.rawOutputEnvelopeBuilder = rawOutputEnvelopeBuilder;
        this.structuredExtractorRegistry = structuredExtractorRegistry;
    }

    /**
     * 开始步骤并发布事件。
     *
     * @param workflowId 工作流标识
     * @param stepType 步骤类型
     * @param attempt 尝试次数
     * @param input 输入内容
     * @param tenantContext 租户上下文
     * @param seqCounter 序列号计数器
     * @return 步骤记录
     */
    public StepRecord startStep(String workflowId,
                                String stepType,
                                int attempt,
                                Map<String, Object> input,
                                TenantContext tenantContext,
                                AtomicLong seqCounter) {
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        StepRecord record = new StepRecord();
        record.setStepId(UUID.randomUUID().toString());
        record.setWorkflowId(workflowId);
        record.setStepSeq(seq);
        record.setType(stepType);
        record.setStatus(stateMachine.transition(null, StepState.STARTED));
        record.setAttempt(attempt);
        record.setInput(input);
        record.setTenantId(tenantContext.getTenantId());
        record.setStartedAt(Instant.now());

        saveRecord(record);
        metricsPublisher.increment("step.count", resolveTraceId(tenantContext));
        publishStepEvent(EventType.STEP_STARTED, record, seqCounter, seq, Map.of(
                "stepId", record.getStepId(),
                "stepSeq", record.getStepSeq(),
                "status", record.getStatus().name(),
                "type", record.getType(),
                "attempt", record.getAttempt()
        ));
        log.info("步骤开始, tenantId={}, workflowId={}, stepId={}, seq={}",
                tenantContext.getTenantId(), workflowId, record.getStepId(), seq);
        return record;
    }

    /**
     * 完成步骤并发布事件。
     *
     * @param record 步骤记录
     * @param output 输出内容
     * @param seqCounter 序列号计数器
     * @return 更新后的步骤记录
     */
    public StepRecord completeStep(StepRecord record, Map<String, Object> output, AtomicLong seqCounter) {
        record.setStatus(stateMachine.transition(record.getStatus(), StepState.COMPLETED));
        Map<String, Object> outputWithSummary = output;
        Map<String, Object> summary = Collections.emptyMap();
        if (stepOutputSummaryBuilder != null && stepOutputSummaryBuilder.isEnabled()) {
            long summaryStart = System.nanoTime();
            summary = stepOutputSummaryBuilder.build(
                    record,
                    null,
                    outputWithSummary,
                    null,
                    null
            );
            long summaryMs = (System.nanoTime() - summaryStart) / 1_000_000;
                if (summary != null && !summary.isEmpty()) {
                Map<String, Object> digest = summary.get("outputDigest") instanceof Map<?, ?> map
                        ? new HashMap<>(map.size())
                        : null;
                if (summary.get("outputDigest") instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> digest.put(String.valueOf(key), value));
                }
                Integer charCount = digest != null ? resolveInt(digest.get("charCount")) : null;
                Integer keyCount = digest != null ? resolveInt(digest.get("keyCount")) : null;
                Boolean truncated = summary.get("truncated") instanceof Boolean value ? value : null;
                String toolName = null;
                if (summary.get("stepSummary") instanceof Map<?, ?> stepSummary) {
                    Object tool = stepSummary.get("tool");
                    if (tool != null) {
                        toolName = String.valueOf(tool);
                    }
                }
                log.info("步骤摘要生成完成, tenantId={}, workflowId={}, stepType={}, toolName={}, truncated={}, charCount={}, keyCount={}, durationMs={}",
                        record.getTenantId(),
                        record.getWorkflowId(),
                        record.getType(),
                        toolName,
                        truncated,
                        charCount,
                        keyCount,
                        summaryMs);
            }
        }
        StepResult stepResult = buildStepResult(record, outputWithSummary, summary);
        record.setOutput(stepResult);
        record.setCompletedAt(Instant.now());
        metricsPublisher.recordTime("step.duration.ms", calcDuration(record), resolveTraceId(null));

        long seq = nextSeq(new TenantContext(record.getTenantId(), null, Collections.emptyList(), null, null),
                record.getWorkflowId(), seqCounter);
        publishStepEvent(EventType.STEP_COMPLETED, record, seqCounter, seq, Map.of(
                "stepId", record.getStepId(),
                "stepSeq", record.getStepSeq(),
                "status", record.getStatus().name()
        ));
        saveRecord(record);
        log.info("步骤完成, tenantId={}, workflowId={}, stepId={}, seq={}",
                record.getTenantId(), record.getWorkflowId(), record.getStepId(), record.getStepSeq());
        return record;
    }

    /**
     * 步骤失败并发布事件。
     *
     * @param record 步骤记录
     * @param errorCode 错误码
     * @param details 失败详情
     * @param seqCounter 序列号计数器
     * @return 更新后的步骤记录
     */
    public StepRecord failStep(StepRecord record,
                               String errorCode,
                               Map<String, Object> details,
                               AtomicLong seqCounter) {
        record.setStatus(stateMachine.transition(record.getStatus(), StepState.FAILED));
        record.setErrorCode(errorCode);
        record.setCompletedAt(Instant.now());
        metricsPublisher.increment("step.failure.count", resolveTraceId(null));

        long seq = nextSeq(new TenantContext(record.getTenantId(), null, Collections.emptyList(), null, null),
                record.getWorkflowId(), seqCounter);
        publishStepEvent(EventType.STEP_FAILED, record, seqCounter, seq, Map.of(
                "stepId", record.getStepId(),
                "stepSeq", record.getStepSeq(),
                "status", record.getStatus().name(),
                "errorCode", errorCode,
                "details", details == null ? Collections.emptyMap() : details
        ));
        saveRecord(record);
        log.warn("步骤失败, tenantId={}, workflowId={}, stepId={}, errorCode={}",
                record.getTenantId(), record.getWorkflowId(), record.getStepId(), errorCode);
        return record;
    }

    /**
     * 查询步骤时间线。
     *
     * @param workflowId 工作流标识
     * @param cursor 游标
     * @param size 分页大小
     * @param tenantContext 租户上下文
     * @return 步骤时间线
     */
    public StepTimelineResponse listSteps(String workflowId,
                                          String cursor,
                                          Integer size,
                                          TenantContext tenantContext) {
        List<StepRecord> records = stepRecordRepository.findByWorkflow(tenantContext.getTenantId(), workflowId);
        if (records == null || records.isEmpty()) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "步骤时间线不存在");
        }

        ArrayList<StepRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparingLong(StepRecord::getStepSeq));

        int startIndex = 0;
        if (cursor != null && !cursor.isBlank()) {
            for (int i = 0; i < sorted.size(); i++) {
                if (cursor.equals(sorted.get(i).getStepId())) {
                    startIndex = i + 1;
                    break;
                }
            }
        }

        int pageSize = (size != null && size > 0) ? size : sorted.size();
        int endIndex = Math.min(startIndex + pageSize, sorted.size());
        List<StepRecord> page = startIndex < endIndex
                ? new ArrayList<>(sorted.subList(startIndex, endIndex))
                : new ArrayList<>();

        String nextCursor = null;
        boolean hasMore = false;
        if (endIndex < sorted.size() && endIndex > 0) {
            nextCursor = sorted.get(endIndex - 1).getStepId();
            hasMore = true;
        }

        return new StepTimelineResponse(workflowId, page, nextCursor, hasMore);
    }

    /**
     * 获取步骤记录列表，用于回放与内部查询。
     *
     * @param workflowId 工作流标识
     * @param tenantContext 租户上下文
     * @return 步骤记录列表
     */
    public List<StepRecord> getSteps(String workflowId, TenantContext tenantContext) {
        List<StepRecord> records = stepRecordRepository.findByWorkflow(tenantContext.getTenantId(), workflowId);
        if (records == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(records);
    }

    private void saveRecord(StepRecord record) {
        stepRecordRepository.save(record);
    }

    private long nextSeq(TenantContext tenantContext, String workflowId, AtomicLong seqCounter) {
        if (seqCounter != null) {
            return seqCounter.incrementAndGet();
        }
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
    }

    private void publishStepEvent(EventType type,
                                  StepRecord record,
                                  AtomicLong seqCounter,
                                  long seq,
                                  Map<String, Object> payload) {
        StreamEvent event = new StreamEvent();
        String streamId = record.getWorkflowId();
        Map<String, Object> mutable = payload == null ? new java.util.HashMap<>() : new java.util.HashMap<>(payload);
        mutable.putIfAbsent("traceId", resolveTraceId(null));
        event.setEventId(streamId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(record.getWorkflowId());
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(streamId);
        event.setTenantId(record.getTenantId());
        event.setPayload(mutable);
        eventPublisher.publishEvent(event);
    }

    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && tenantContext.getTraceId() != null
                && !tenantContext.getTraceId().isBlank()) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }

    private long calcDuration(StepRecord record) {
        if (record.getStartedAt() == null || record.getCompletedAt() == null) {
            return 0;
        }
        return Duration.between(record.getStartedAt(), record.getCompletedAt()).toMillis();
    }

    private Integer resolveInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private StepResult buildStepResult(StepRecord record,
                                       Map<String, Object> rawOutput,
                                       Map<String, Object> summaryMap) {
        StepResult result = new StepResult();
        result.setMeta(buildMeta(record, rawOutput));
        result.setRawRef(resolveRawRef(rawOutput));
        result.setRaw(resolveRaw(rawOutput));
        result.setStructured(resolveStructured(record, rawOutput, result.getRawRef()));
        result.setSummary(resolveSummary(summaryMap));
        result.setRefs(resolveRefs(rawOutput, result.getRawRef()));
        result.setErrors(List.of());
        return result;
    }

    private StepResultMeta buildMeta(StepRecord record, Map<String, Object> rawOutput) {
        StepResultMeta meta = new StepResultMeta();
        meta.setStepId(record.getStepId());
        meta.setSeq(record.getStepSeq());
        meta.setType(record.getType());
        meta.setStatus(record.getStatus());
        meta.setAttempt(record.getAttempt());
        Object tool = rawOutput != null ? rawOutput.get("tool") : null;
        if (tool != null) {
            meta.setToolName(String.valueOf(tool));
        }
        StepResultTiming timing = new StepResultTiming();
        timing.setStartedAt(record.getStartedAt());
        timing.setEndedAt(record.getCompletedAt() != null ? record.getCompletedAt() : Instant.now());
        timing.setDurationMs(calcDuration(record));
        meta.setTiming(timing);
        return meta;
    }

    private RawRef resolveRawRef(Map<String, Object> rawOutput) {
        if (rawOutput == null) {
            return null;
        }
        Object rawRefValue = rawOutput.get("rawRef");
        if (!(rawRefValue instanceof String rawRefText) || rawRefText.isBlank()) {
            if (rawOutput.get("rawResult") instanceof Map<?, ?> rawResultMap) {
                rawRefValue = rawResultMap.get("rawRef");
            }
        }
        if (!(rawRefValue instanceof String rawRefText) || rawRefText.isBlank()) {
            if (rawOutput.get("result") instanceof Map<?, ?> resultMap) {
                rawRefValue = resultMap.get("rawRef");
            }
        }
        if (!(rawRefValue instanceof String rawRefText) || rawRefText.isBlank()) {
            if (rawOutput.get("raw") instanceof Map<?, ?> rawMap) {
                rawRefValue = rawMap.get("rawRef");
            }
        }
        if (!(rawRefValue instanceof String rawRefText) || rawRefText.isBlank()) {
            return null;
        }
        RawRef rawRef = new RawRef();
        rawRef.setStore("memory");
        rawRef.setKey(rawRefText);
        rawRef.setMediaType("application/json");
        rawRef.setCreatedAt(Instant.now().toString());
        return rawRef;
    }

    private StepResultRaw resolveRaw(Map<String, Object> rawOutput) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return null;
        }
        if (rawOutputEnvelopeBuilder == null) {
            StepResultRaw raw = new StepResultRaw();
            raw.setData(rawOutput);
            raw.setTruncated(false);
            return raw;
        }
        Map<String, Object> envelope = rawOutputEnvelopeBuilder.build(rawOutput);
        if (envelope == null || envelope.isEmpty()) {
            return null;
        }
        StepResultRaw raw = new StepResultRaw();
        if (envelope.get("data") instanceof Map<?, ?> dataMap) {
            Map<String, Object> data = new HashMap<>();
            dataMap.forEach((mapKey, mapValue) -> data.put(String.valueOf(mapKey), mapValue));
            raw.setData(data);
        }
        if (envelope.get("truncated") instanceof Boolean truncated) {
            raw.setTruncated(truncated);
        } else {
            raw.setTruncated(false);
        }
        return raw;
    }

    private StepResultRefSet resolveRefs(Map<String, Object> rawOutput, RawRef rawRef) {
        StepResultRefSet refs = new StepResultRefSet();
        if (rawRef != null && StringUtils.hasText(rawRef.getKey())) {
            refs.setRawRef(rawRef.getKey());
        }
        if (rawOutputEnvelopeBuilder != null && rawOutput != null && !rawOutput.isEmpty()) {
            Map<String, String> extracted = rawOutputEnvelopeBuilder.resolveRefs(rawOutput);
            if (extracted != null && !extracted.isEmpty()) {
                refs.setDecisionRawRef(extracted.get("decisionRawRef"));
                refs.setSummaryRawRef(extracted.get("summaryRawRef"));
                refs.setToolRawRef(extracted.get("toolRawRef"));
                refs.setModelRawRef(extracted.get("modelRawRef"));
            }
        }
        if (!StringUtils.hasText(refs.getRawRef())
                && !StringUtils.hasText(refs.getDecisionRawRef())
                && !StringUtils.hasText(refs.getSummaryRawRef())
                && !StringUtils.hasText(refs.getToolRawRef())
                && !StringUtils.hasText(refs.getModelRawRef())) {
            return null;
        }
        return refs;
    }

    private StructuredResult resolveStructured(StepRecord record, Map<String, Object> rawOutput, RawRef rawRef) {
        if (structuredExtractorRegistry == null) {
            return null;
        }
        Map<String, Object> resultMap = new HashMap<>();
        if (rawOutput != null) {
            Object result = rawOutput.get("result");
            if (result instanceof Map<?, ?> map) {
                map.forEach((key, value) -> resultMap.put(String.valueOf(key), value));
            } else {
                resultMap.putAll(rawOutput);
            }
        }
        String toolName = rawOutput != null && rawOutput.get("tool") != null ? String.valueOf(rawOutput.get("tool")) : null;
        String rawRefKey = rawRef != null ? rawRef.getKey() : null;
        StructuredResult structured = structuredExtractorRegistry.extract(record.getType(), toolName, resultMap, rawRefKey);
        if (structured == null) {
            return null;
        }
        StructuredRefs refs = structured.getRefs();
        if (refs == null) {
            refs = new StructuredRefs();
            structured.setRefs(refs);
        }
        if (!StringUtils.hasText(refs.getRawRef()) && StringUtils.hasText(rawRefKey)) {
            refs.setRawRef(rawRefKey);
        }
        if (rawOutputEnvelopeBuilder != null && rawOutput != null && !rawOutput.isEmpty()) {
            Map<String, String> extracted = rawOutputEnvelopeBuilder.resolveRefs(rawOutput);
            if (extracted != null && !extracted.isEmpty()) {
                if (!StringUtils.hasText(refs.getDecisionRawRef())) {
                    refs.setDecisionRawRef(extracted.get("decisionRawRef"));
                }
                if (!StringUtils.hasText(refs.getSummaryRawRef())) {
                    refs.setSummaryRawRef(extracted.get("summaryRawRef"));
                }
                if (!StringUtils.hasText(refs.getToolRawRef())) {
                    refs.setToolRawRef(extracted.get("toolRawRef"));
                }
                if (!StringUtils.hasText(refs.getModelRawRef())) {
                    refs.setModelRawRef(extracted.get("modelRawRef"));
                }
            }
        }
        return structured;
    }

    private StepResultSummary resolveSummary(Map<String, Object> summaryMap) {
        if (summaryMap == null || summaryMap.isEmpty()) {
            return null;
        }
        StepResultSummary summary = new StepResultSummary();
        if (summaryMap.get("stepSummary") instanceof Map<?, ?> stepSummaryMap) {
            Object text = stepSummaryMap.get("summary");
            if (text != null) {
                summary.setText(String.valueOf(text));
            }
            summary.setStepSummary(copyObjectMap(stepSummaryMap));
        }
        if (summaryMap.get("outputSummary") instanceof Map<?, ?> outputSummaryMap) {
            summary.setOutputSummary(copyObjectMap(outputSummaryMap));
        }
        if (summaryMap.get("toolResultSummary") instanceof Map<?, ?> toolSummaryMap) {
            summary.setToolResultSummary(copyObjectMap(toolSummaryMap));
        }
        if (summaryMap.get("inputSummary") instanceof Map<?, ?> inputSummaryMap) {
            summary.setInputSummary(copyObjectMap(inputSummaryMap));
        }
        if (summaryMap.get("inputDigest") instanceof Map<?, ?> inputDigestMap) {
            summary.setInputDigest(toDigest(inputDigestMap));
        }
        if (summaryMap.get("outputDigest") instanceof Map<?, ?> outputDigestMap) {
            summary.setOutputDigest(toDigest(outputDigestMap));
        }
        boolean truncated = summaryMap.get("truncated") instanceof Boolean value && value;
        summary.setTruncated(truncated);
        summary.setReason(truncated ? "summary_limit" : null);
        return summary;
    }

    private StepResultDigest toDigest(Map<?, ?> map) {
        StepResultDigest digest = new StepResultDigest();
        if (map.get("keyCount") instanceof Number number) {
            digest.setKeyCount(number.intValue());
        }
        if (map.get("keys") instanceof List<?> list) {
            List<String> keys = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    keys.add(String.valueOf(item));
                }
            }
            digest.setKeys(keys);
        }
        if (map.get("charCount") instanceof Number number) {
            digest.setCharCount(number.intValue());
        }
        if (map.get("truncated") instanceof Boolean value) {
            digest.setTruncated(value);
        }
        return digest;
    }

    private Map<String, Object> copyObjectMap(Map<?, ?> map) {
        Map<String, Object> copied = new HashMap<>();
        map.forEach((key, value) -> copied.put(String.valueOf(key), value));
        return copied;
    }
}
