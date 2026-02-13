package com.example.agent.runtime.structured;

import com.example.agent.runtime.structured.extractor.StructuredExtractor;
import com.example.agent.runtime.structured.result.StructuredQuality;
import com.example.agent.runtime.structured.result.StructuredRefs;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.DefaultStructuredData;
import com.example.agent.runtime.structured.structured.StructuredData;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 结构化提取器注册表，统一负责提取器选择与兜底。
 */
@Component
public class StructuredExtractorRegistry {

    private static final Logger log = LoggerFactory.getLogger(StructuredExtractorRegistry.class);

    private final List<StructuredExtractor> extractors;

    /**
     * 结构化结果校验器。
     */
    private final StructuredResultValidator structuredResultValidator;

    /**
     * 结构化质量评分器。
     */
    private final StructuredQualityScorer structuredQualityScorer;

    /**
     * 结构化观测记录器。
     */
    private final StructuredMetricsRecorder metricsRecorder;

    public StructuredExtractorRegistry(List<StructuredExtractor> extractors,
                                       StructuredResultValidator structuredResultValidator,
                                       StructuredQualityScorer structuredQualityScorer,
                                       StructuredMetricsRecorder metricsRecorder) {
        // 归一化提取器列表，空时使用空集合。
        this.extractors = extractors == null ? List.of() : extractors;
        // 绑定结构化结果校验器。
        this.structuredResultValidator = structuredResultValidator;
        // 绑定结构化质量评分器。
        this.structuredQualityScorer = structuredQualityScorer;
        // 绑定结构化观测记录器。
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * 提取结构化结果。
     *
     * @param stepType 步骤类型
     * @param toolName 工具名称
     * @param rawResult 原始结果
     * @param rawRef 原始引用
     * @return 结构化结果
     */
    public StructuredResult<? extends StructuredData> extract(String stepType,
                                                              String toolName,
                                                              Map<String, Object> rawResult,
                                                              String rawRef) {
        // 设计意图：按顺序筛选可用提取器，风险是多层条件可能导致遗漏匹配。
        // 循环遍历结构化提取器列表。
        for (StructuredExtractor extractor : extractors) {
            // 判断提取器是否为空，空时跳过。
            if (extractor == null) {
                // 跳过空提取器，避免空指针。
                continue;
            }
            // 判断提取器是否支持当前结果。
            if (extractor.supports(stepType, toolName, rawResult)) {
                // 调用提取器执行结构化提取。
                StructuredResult<? extends StructuredData> result = extractor.extract(stepType, toolName, rawResult, rawRef);
                // 判断提取结果是否为空，非空时返回。
                if (result != null) {
                    // 记录匹配成功日志。
                    log.debug("匹配结构化提取器成功, extractor={}, kind={} ",
                            extractor.getClass().getSimpleName(),
                            result.getKind());
                    // 调用结构化结果校验，必要时执行降级处理。
                    StructuredResult<? extends StructuredData> validated = validateStructuredResult(result, stepType, toolName, rawRef);
                    // 追加结构化质量评分。
                    applyQualityScore(validated);
                    // 返回结构化结果。
                    return validated;
                }
            }
        }
        // 记录未匹配到提取器的告警日志。
        log.warn("未匹配到结构化提取器，使用兜底结果 stepType={}, toolName={}", stepType, toolName);
        // 返回兜底结构化结果。
        return fallback(rawRef, rawResult);
    }

    private StructuredResult<DefaultStructuredData> fallback(String rawRef, Map<String, Object> rawResult) {
        // 初始化兜底数据映射容器。
        Map<String, Object> fallbackData = new LinkedHashMap<>();
        // 写入兜底类别标识。
        fallbackData.put("category", "PARSE_ERROR");
        // 写入兜底提示信息。
        fallbackData.put("message", "未匹配到可用的结构化提取器");
        // 写入兜底行动建议。
        fallbackData.put("actionableNext", List.of("补充对应 ResultKind 的提取器"));
        // 写入原始结果大小。
        fallbackData.put("rawSize", rawResult == null ? 0 : rawResult.size());
        // 构建兜底结构化结果。
        StructuredResult<DefaultStructuredData> result = StructuredResult.defaultResult(ResultKind.ERROR, 1, fallbackData);
        // 构建兜底引用信息。
        StructuredRefs refs = new StructuredRefs();
        // 写入原始引用标识。
        refs.setRawRef(rawRef);
        // 绑定引用信息到兜底结果。
        result.setRefs(refs);
        // 构建兜底质量信息。
        StructuredQuality quality = new StructuredQuality();
        // 写入兜底质量置信度。
        quality.setConfidence(0.0D);
        // 写入兜底质量告警。
        quality.setWarnings(List.of("fallback_extractor_used"));
        // 写入兜底截断标记。
        quality.setTruncated(false);
        // 绑定质量信息到兜底结果。
        result.setQuality(quality);
        // 判断观测记录器是否存在，存在时记录降级指标。
        if (metricsRecorder != null) {
            // 记录结构化降级指标。
            metricsRecorder.recordDegrade(result.getKind(), result.getSchemaVersion());
        }
        // 返回兜底结果。
        return result;
    }

    private StructuredResult<? extends StructuredData> validateStructuredResult(StructuredResult<? extends StructuredData> result,
                                                                                String stepType,
                                                                                String toolName,
                                                                                String rawRef) {
        // 判断校验器是否为空，空时直接返回原结果。
        if (structuredResultValidator == null) {
            // 返回未校验的结构化结果。
            return result;
        }
        // 调用结构化结果校验器执行校验。
        StructuredValidationResult validationResult = structuredResultValidator.validate(result);
        // 判断校验是否通过，失败时执行降级处理。
        if (!validationResult.isValid()) {
            // 记录结构化结果校验失败日志。
            log.warn("结构化结果校验失败, workflowId={}, stepId={}, stepType={}, toolName={}, kind={}, schemaVersion={}, rawRef={}, errors={}",
                    null,
                    null,
                    stepType,
                    toolName,
                    result == null ? null : result.getKind(),
                    result == null ? null : result.getSchemaVersion(),
                    rawRef,
                    validationResult.getErrors());
            // 记录结构化结果校验失败指标。
            recordSchemaInvalidMetric(result == null ? null : result.getKind(),
                    result == null ? null : result.getSchemaVersion());
            // 构建结构化结果降级输出。
            return buildSchemaInvalidFallback(result, validationResult, rawRef);
        }
        // 判断是否存在校验告警，存在时合并到质量信息。
        if (!validationResult.getWarnings().isEmpty()) {
            // 合并校验告警到结构化质量信息。
            appendQualityWarnings(result, validationResult.getWarnings());
        }
        // 返回校验通过的结构化结果。
        return result;
    }

    private void applyQualityScore(StructuredResult<? extends StructuredData> result) {
        // 判断结果或评分器是否为空，空时直接返回。
        if (result == null || structuredQualityScorer == null) {
            return;
        }
        // 调用结构化质量评分器计算评分。
        StructuredQuality scored = structuredQualityScorer.score(result);
        // 判断评分是否为空，空时直接返回。
        if (scored == null) {
            return;
        }
        // 合并评分结果到结构化质量信息。
        mergeQuality(result, scored);
        // 判断观测记录器与缺失字段是否可用，满足条件时记录缺失字段指标。
        if (metricsRecorder != null
                && result.getQuality() != null
                && result.getQuality().getMissingFields() != null
                && !result.getQuality().getMissingFields().isEmpty()) {
            // 记录缺失字段数量指标。
            metricsRecorder.recordMissingFields(result.getKind(),
                    result.getSchemaVersion(),
                    result.getQuality().getMissingFields().size());
        }
    }

    private StructuredResult<DefaultStructuredData> buildSchemaInvalidFallback(
            StructuredResult<? extends StructuredData> source,
            StructuredValidationResult validationResult,
            String rawRef) {
        // 初始化降级数据映射容器。
        Map<String, Object> fallbackData = new LinkedHashMap<>();
        // 写入降级类别标识。
        fallbackData.put("category", "SCHEMA_INVALID");
        // 写入降级提示信息。
        fallbackData.put("message", "structured_result_schema_validation_failed");
        // 初始化结果类型文本。
        String kindText = null;
        // 判断原始结果是否存在且包含类型，存在时写入类型文本。
        if (source != null && source.getKind() != null) {
            // 提取原始结果类型文本。
            kindText = source.getKind().name();
        }
        // 写入原始结果类型文本。
        fallbackData.put("kind", kindText);
        // 写入原始结构版本号。
        fallbackData.put("schemaVersion", source == null ? null : source.getSchemaVersion());
        // 写入结构化校验错误列表。
        fallbackData.put("errors", validationResult == null ? List.of() : validationResult.getErrors());
        // 构建降级结构化结果。
        StructuredResult<DefaultStructuredData> result = StructuredResult.defaultResult(ResultKind.ERROR, 1, fallbackData);
        // 构建降级引用信息。
        StructuredRefs refs = new StructuredRefs();
        // 写入原始引用标识。
        refs.setRawRef(rawRef);
        // 绑定引用信息到降级结果。
        result.setRefs(refs);
        // 构建降级质量信息。
        StructuredQuality quality = new StructuredQuality();
        // 写入降级质量置信度。
        quality.setConfidence(0.0D);
        // 写入降级质量告警。
        quality.setWarnings(List.of("schema_validation_failed"));
        // 写入降级截断标记。
        quality.setTruncated(false);
        // 绑定质量信息到降级结果。
        result.setQuality(quality);
        // 判断观测记录器是否存在，存在时记录降级指标。
        if (metricsRecorder != null) {
            // 记录结构化降级指标。
            metricsRecorder.recordDegrade(result.getKind(), result.getSchemaVersion());
        }
        // 返回降级后的结构化结果。
        return result;
    }

    private void appendQualityWarnings(StructuredResult<? extends StructuredData> result, List<String> warnings) {
        // 判断结果或告警列表是否为空，空时直接返回。
        if (result == null || warnings == null || warnings.isEmpty()) {
            // 直接返回，避免空指针与无效操作。
            return;
        }
        // 获取结构化质量对象。
        StructuredQuality quality = result.getQuality();
        // 判断质量对象是否为空，空时初始化。
        if (quality == null) {
            // 初始化结构化质量对象。
            quality = new StructuredQuality();
            // 绑定结构化质量对象到结果。
            result.setQuality(quality);
        }
        // 初始化合并后的告警集合。
        Set<String> merged = new LinkedHashSet<>();
        // 读取已有告警列表。
        List<String> existing = quality.getWarnings();
        // 判断已有告警是否为空，非空时合并。
        if (existing != null && !existing.isEmpty()) {
            // 合并已有告警列表。
            merged.addAll(existing);
        }
        // 合并本次校验告警列表。
        merged.addAll(warnings);
        // 写入合并后的告警列表。
        quality.setWarnings(List.copyOf(merged));
    }

    private void mergeQuality(StructuredResult<? extends StructuredData> result, StructuredQuality scored) {
        // 判断结果或评分是否为空，空时直接返回。
        if (result == null || scored == null) {
            return;
        }
        // 设计意图：合并多个质量来源，保留更保守的评分结果。
        // 获取已有质量信息。
        StructuredQuality existing = result.getQuality();
        // 判断已有质量是否为空，空时直接绑定评分结果。
        if (existing == null) {
            // 绑定评分结果到结构化结果。
            result.setQuality(scored);
            return;
        }
        // 合并置信度，优先保留更低值。
        if (scored.getConfidence() != null) {
            if (existing.getConfidence() == null) {
                // 写入置信度评分。
                existing.setConfidence(scored.getConfidence());
            } else {
                // 写入更低的置信度评分。
                existing.setConfidence(Math.min(existing.getConfidence(), scored.getConfidence()));
            }
        }
        // 合并完整度，优先保留更低值。
        if (scored.getCompleteness() != null) {
            if (existing.getCompleteness() == null) {
                // 写入完整度评分。
                existing.setCompleteness(scored.getCompleteness());
            } else {
                // 写入更低的完整度评分。
                existing.setCompleteness(Math.min(existing.getCompleteness(), scored.getCompleteness()));
            }
        }
        // 合并缺失字段列表。
        if (scored.getMissingFields() != null && !scored.getMissingFields().isEmpty()) {
            Set<String> mergedMissing = new LinkedHashSet<>();
            if (existing.getMissingFields() != null && !existing.getMissingFields().isEmpty()) {
                // 合并已有缺失字段列表。
                mergedMissing.addAll(existing.getMissingFields());
            }
            // 合并本次缺失字段列表。
            mergedMissing.addAll(scored.getMissingFields());
            // 写入合并后的缺失字段列表。
            existing.setMissingFields(List.copyOf(mergedMissing));
        }
        // 合并告警列表。
        if (scored.getWarnings() != null && !scored.getWarnings().isEmpty()) {
            Set<String> mergedWarnings = new LinkedHashSet<>();
            if (existing.getWarnings() != null && !existing.getWarnings().isEmpty()) {
                // 合并已有告警列表。
                mergedWarnings.addAll(existing.getWarnings());
            }
            // 合并本次告警列表。
            mergedWarnings.addAll(scored.getWarnings());
            // 写入合并后的告警列表。
            existing.setWarnings(List.copyOf(mergedWarnings));
        }
        // 合并截断标记，已有值优先保留。
        if (existing.getTruncated() == null && scored.getTruncated() != null) {
            // 写入截断标记。
            existing.setTruncated(scored.getTruncated());
        }
    }

    private void recordSchemaInvalidMetric(ResultKind kind, Integer schemaVersion) {
        // 判断观测记录器是否存在，不存在时直接返回。
        if (metricsRecorder == null) {
            // 直接返回，避免空指针。
            return;
        }
        // 记录结构化校验失败指标。
        metricsRecorder.recordValidationFailed(kind, schemaVersion);
    }
}
