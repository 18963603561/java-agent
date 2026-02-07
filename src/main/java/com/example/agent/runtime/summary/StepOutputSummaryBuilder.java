package com.example.agent.runtime.summary;

import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.summary.SummaryComputationModels.OutputSnapshot;
import com.example.agent.runtime.summary.SummaryComputationModels.SummaryLimits;
import com.example.agent.runtime.summary.SummaryComputationModels.TruncationState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 步骤输出摘要构建门面。
 *
 * <p>用途：编排摘要构建流程，聚合输出摘要、工具摘要、步骤摘要、输入摘要与 digest。
 * <p>输入：步骤记录、步骤输入、输出对象、工具名与异常。
 * <p>输出：统一摘要结构映射。
 * <p>边界：摘要开关关闭时返回空映射。
 */
@Component
public class StepOutputSummaryBuilder {

    /**
     * 摘要配置。
     */
    private final StepSummaryProperties properties;

    /**
     * 快照服务。
     */
    private final SummarySnapshotService summarySnapshotService;

    /**
     * 输入清理服务。
     */
    private final SummaryInputSanitizer summaryInputSanitizer;

    /**
     * 文本摘要服务。
     */
    private final StepSummaryTextService stepSummaryTextService;

    /**
     * 摘要辅助服务。
     */
    private final SummaryDigestService summaryDigestService;

    public StepOutputSummaryBuilder(StepSummaryProperties properties,
                                    SummarySnapshotService summarySnapshotService,
                                    SummaryInputSanitizer summaryInputSanitizer,
                                    StepSummaryTextService stepSummaryTextService,
                                    SummaryDigestService summaryDigestService) {
        this.properties = properties;
        this.summarySnapshotService = summarySnapshotService;
        this.summaryInputSanitizer = summaryInputSanitizer;
        this.stepSummaryTextService = stepSummaryTextService;
        this.summaryDigestService = summaryDigestService;
    }

    /**
     * 判断摘要是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return properties != null && properties.isEnable();
    }

    /**
     * 构建摘要。
     *
     * @param record 步骤记录
     * @param stepInput 步骤输入
     * @param output 输出
     * @param toolName 工具名称
     * @param error 错误对象
     * @return 摘要结果
     */
    public Map<String, Object> build(StepRecord record,
                                     Map<String, Object> stepInput,
                                     Object output,
                                     String toolName,
                                     Object error) {
        if (!isEnabled()) {
            return Collections.emptyMap();
        }

        String stepId = record != null ? record.getStepId() : null;
        String stepType = record != null ? record.getType() : null;
        String status = record != null && record.getStatus() != null ? record.getStatus().name() : null;
        Integer attempt = record != null ? record.getAttempt() : null;
        Map<String, Object> effectiveInput = stepInput != null ? stepInput : record != null ? record.getInput() : null;

        SummaryLimits limits = SummaryLimits.from(properties);
        TruncationState truncation = new TruncationState();

        OutputSnapshot snapshot = summarySnapshotService.buildSnapshot(output, limits, truncation);
        String resolvedToolName = stepSummaryTextService.resolveToolName(toolName, output);

        Map<String, Object> outputSummary = new LinkedHashMap<>();
        putIfNotNull(outputSummary, "status", status);
        if (output == null) {
            outputSummary.put("hasOutput", false);
            putIfNotNull(outputSummary, "stepId", stepId);
            putIfNotNull(outputSummary, "type", stepType);
            outputSummary.put("summary", "no output");
        }
        if (attempt != null) {
            outputSummary.put("attempt", attempt);
        }
        if (snapshot.getKeys() != null && !snapshot.getKeys().isEmpty()) {
            outputSummary.put("keyFields", snapshot.getKeys());
        }
        if (StringUtils.hasText(snapshot.getSample())) {
            outputSummary.put("sample", snapshot.getSample());
        }
        String errorText = stepSummaryTextService.resolveErrorText(error, limits, truncation);
        if (StringUtils.hasText(errorText)) {
            outputSummary.put("error", errorText);
        }

        Object toolResultPayload = extractToolResultPayload(output);
        OutputSnapshot toolSnapshot = toolResultPayload != null
                ? summarySnapshotService.buildSnapshot(toolResultPayload, limits, truncation)
                : snapshot;

        Map<String, Object> toolResultSummary = new LinkedHashMap<>();
        if (StringUtils.hasText(resolvedToolName)) {
            toolResultSummary.put(OutputKeys.TOOL_NAME, resolvedToolName);
        }
        if (toolSnapshot.getKeys() != null && !toolSnapshot.getKeys().isEmpty()) {
            toolResultSummary.put("resultKeys", toolSnapshot.getKeys());
        }
        if (StringUtils.hasText(toolSnapshot.getSample()) && !toolSnapshot.getSample().equals(snapshot.getSample())) {
            toolResultSummary.put("sample", toolSnapshot.getSample());
        }

        Map<String, Object> stepSummary = new LinkedHashMap<>();
        putIfNotNull(stepSummary, "stepId", stepId);
        putIfNotNull(stepSummary, "type", stepType);
        putIfNotNull(stepSummary, "status", status);
        if (attempt != null) {
            stepSummary.put("attempt", attempt);
        }
        if (StringUtils.hasText(resolvedToolName)) {
            stepSummary.put(OutputKeys.TOOL_NAME, resolvedToolName);
        }
        String summaryText = stepSummaryTextService.buildStepSummaryText(
                stepType,
                status,
                resolvedToolName,
                snapshot,
                limits,
                truncation
        );
        if (StringUtils.hasText(summaryText)) {
            stepSummary.put("summary", summaryText);
        }

        TruncationState inputTruncation = new TruncationState();
        Map<String, Object> inputSummary = buildInputSummary(
                effectiveInput,
                resolvedToolName,
                limits,
                inputTruncation,
                stepInput != null ? "stepInput" : "record"
        );
        OutputSnapshot inputSnapshot = summarySnapshotService.buildSnapshot(effectiveInput, limits, inputTruncation);

        Map<String, Object> inputDigest = new LinkedHashMap<>();
        inputDigest.put(OutputKeys.KEY_COUNT, inputSnapshot.getKeyCount());
        inputDigest.put(OutputKeys.KEYS, inputSnapshot.getKeys());
        inputDigest.put(OutputKeys.CHAR_COUNT, inputSnapshot.getCharCount());
        inputDigest.put(OutputKeys.TRUNCATED, inputTruncation.isTruncated());

        Map<String, Object> outputDigest = new LinkedHashMap<>();
        outputDigest.put(OutputKeys.KEY_COUNT, snapshot.getKeyCount());
        outputDigest.put(OutputKeys.KEYS, snapshot.getKeys());
        outputDigest.put(OutputKeys.CHAR_COUNT, snapshot.getCharCount());
        outputDigest.put(OutputKeys.TRUNCATED, truncation.isTruncated());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(OutputKeys.OUTPUT_SUMMARY, outputSummary);
        result.put(OutputKeys.TOOL_RESULT_SUMMARY, toolResultSummary);
        result.put(OutputKeys.STEP_SUMMARY, stepSummary);
        if (inputSummary != null && !inputSummary.isEmpty()) {
            result.put(OutputKeys.INPUT_SUMMARY, inputSummary);
        }
        if (inputSnapshot.getKeyCount() > 0 || inputSnapshot.getCharCount() > 0) {
            result.put(OutputKeys.INPUT_DIGEST, inputDigest);
        }
        result.put(OutputKeys.OUTPUT_DIGEST, outputDigest);
        result.put(OutputKeys.TRUNCATED, truncation.isTruncated());
        return result;
    }

    private Object extractToolResultPayload(Object output) {
        if (!(output instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Object rawResult = map.get(OutputKeys.RAW_RESULT);
        if (rawResult != null) {
            return rawResult;
        }
        Object result = map.get(OutputKeys.RESULT);
        if (result != null) {
            return result;
        }
        return null;
    }

    private Map<String, Object> buildInputSummary(Map<String, Object> input,
                                                  String toolName,
                                                  SummaryLimits limits,
                                                  TruncationState truncation,
                                                  String source) {
        if ((input == null || input.isEmpty()) && !StringUtils.hasText(toolName)) {
            return Collections.emptyMap();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        String resolvedToolName = stepSummaryTextService.resolveToolName(toolName, input);
        if (StringUtils.hasText(resolvedToolName)) {
            summary.put(OutputKeys.TOOL_NAME, resolvedToolName);
        }
        if (input != null && !input.isEmpty()) {
            summaryDigestService.putTextSummary(summary, "query", input.get("query"), limits, truncation);
            summaryDigestService.putTextSummary(summary, "question", input.get("question"), limits, truncation);
            summaryDigestService.putTextSummary(summary, "topic", input.get("topic"), limits, truncation);

            Object arguments = input.get("arguments");
            Object sanitizedArguments = summaryInputSanitizer.sanitizeInputValue(
                    arguments,
                    limits,
                    truncation,
                    2,
                    new java.util.IdentityHashMap<>()
            );
            summaryDigestService.putStructuredSummary(summary, "arguments", sanitizedArguments);

            Object filters = input.containsKey("filters") ? input.get("filters")
                    : input.containsKey("filter") ? input.get("filter")
                    : input.get("conditions");
            Object sanitizedFilters = summaryInputSanitizer.sanitizeInputValue(
                    filters,
                    limits,
                    truncation,
                    2,
                    new java.util.IdentityHashMap<>()
            );
            summaryDigestService.putStructuredSummary(summary, "filters", sanitizedFilters);

            Object timeRange = input.containsKey("timeRange") ? input.get("timeRange")
                    : input.containsKey("dateRange") ? input.get("dateRange")
                    : input.get("range");
            Object sanitizedTimeRange = summaryInputSanitizer.sanitizeInputValue(
                    timeRange,
                    limits,
                    truncation,
                    2,
                    new java.util.IdentityHashMap<>()
            );
            summaryDigestService.putStructuredSummary(summary, "timeRange", sanitizedTimeRange);

            summaryDigestService.putTextSummary(summary, "from", input.get("from"), limits, truncation);
            summaryDigestService.putTextSummary(summary, "to", input.get("to"), limits, truncation);
            summaryDigestService.putTextSummary(summary, "startTime", input.get("startTime"), limits, truncation);
            summaryDigestService.putTextSummary(summary, "endTime", input.get("endTime"), limits, truncation);
        }
        if (summary.isEmpty()) {
            return Collections.emptyMap();
        }
        if (StringUtils.hasText(source)) {
            summary.put("source", source);
        }
        return summary;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
