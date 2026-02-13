package com.example.agent.runtime.summary.audit;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.summary.SemanticSummaryQuality;
import com.example.agent.runtime.summary.StepSummaryBuildInput;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 语义摘要抽检器。
 */
@Component
public class SemanticSummarySampler {

    private static final Logger log = LoggerFactory.getLogger(SemanticSummarySampler.class);

    /**
     * 抽检配置。
     */
    private final SemanticSummaryAuditProperties properties;

    /**
     * 抽检落库接口。
     */
    private final SemanticSummaryAuditSink auditSink;

    public SemanticSummarySampler(SemanticSummaryAuditProperties properties,
                                  SemanticSummaryAuditSink auditSink) {
        this.properties = properties;
        this.auditSink = auditSink;
    }

    /**
     * 执行抽检。
     *
     * @param input 摘要输入
     * @param summary 摘要映射
     * @param quality 质量结果
     * @return 抽检原因，未命中时返回 null
     */
    public String sample(StepSummaryBuildInput input,
                         Map<String, Object> summary,
                         SemanticSummaryQuality quality) {
        // 设计意图：用开关、阈值与随机比例控制抽检，避免影响主流程性能。
        // 判断抽检是否启用，未启用时直接返回。
        if (properties == null || !properties.isEnable()) {
            return null;
        }
        // 判断摘要映射是否为空，空时直接返回。
        if (summary == null || summary.isEmpty()) {
            return null;
        }
        // 判断质量对象是否为空，空时直接返回。
        if (quality == null) {
            return null;
        }
        // 计算抽检原因，未命中时返回空。
        String reason = resolveSampleReason(quality);
        // 判断是否命中抽检条件，未命中时直接返回。
        if (!StringUtils.hasText(reason)) {
            return null;
        }

        // 构建抽检记录对象。
        SemanticSummaryAuditRecord record = buildRecord(input, summary, quality);
        // 判断记录对象是否为空，空时返回抽检原因。
        if (record == null) {
            return reason;
        }

        // 调用抽检落库接口并捕获异常，避免影响主流程。
        try {
            auditSink.record(record);
            // 记录抽检成功日志，便于观测抽检链路。
            log.debug("语义摘要抽检记录完成, stepId={}, stepType={}, rawRef={} ",
                    record.getStepId(),
                    record.getStepType(),
                    record.getRawRef());
        // 捕获抽检落库异常并记录错误日志。
        } catch (Exception ex) {
            // 记录抽检失败日志并输出异常堆栈。
            log.error("语义摘要抽检记录失败, stepId={}, stepType={}",
                    record.getStepId(),
                    record.getStepType(),
                    ex);
        }
        // 返回抽检原因。
        return reason;
    }

    private String resolveSampleReason(SemanticSummaryQuality quality) {
        // 判断质量评分是否为空，空时仅依赖比例抽检。
        if (quality == null || quality.getScore() == null) {
            // 读取抽检比例，用于随机命中判断。
            double sampleRate = properties.getSampleRate();
            // 生成随机值判断是否命中抽检比例。
            double random = ThreadLocalRandom.current().nextDouble();
            // 返回抽检原因或空。
            return random < sampleRate ? "sample_rate" : null;
        }
        // 判断是否触发低质量强制抽检。
        if (properties.isForceLowScore() && quality.getScore() < properties.getLowScoreThreshold()) {
            // 返回低质量抽检原因。
            return "low_score";
        }
        // 读取抽检比例，用于随机命中判断。
        double sampleRate = properties.getSampleRate();
        // 生成随机值判断是否命中抽检比例。
        double random = ThreadLocalRandom.current().nextDouble();
        // 返回抽检原因或空。
        return random < sampleRate ? "sample_rate" : null;
    }

    private SemanticSummaryAuditRecord buildRecord(StepSummaryBuildInput input,
                                                   Map<String, Object> summary,
                                                   SemanticSummaryQuality quality) {
        // 判断摘要映射是否为空，空时返回空记录。
        if (summary == null || summary.isEmpty()) {
            return null;
        }
        // 构建抽检记录对象。
        SemanticSummaryAuditRecord record = new SemanticSummaryAuditRecord();
        // 写入步骤标识。
        record.setStepId(input == null ? null : input.getStepId());
        // 写入步骤类型。
        record.setStepType(input == null ? null : input.getStepType());
        // 写入工具名称。
        record.setToolName(input == null ? null : input.getToolName());
        // 解析原始引用并写入。
        record.setRawRef(resolveRawRef(input));
        // 写入结果引用映射。
        record.setResult(resolveResultRef(input));
        // 写入摘要映射副本。
        record.setSummary(copySummary(summary));
        // 写入质量映射副本。
        record.setQuality(copyQuality(quality));
        // 写入记录时间。
        record.setCreatedAt(Instant.now().toString());
        // 返回抽检记录对象。
        return record;
    }

    private String resolveRawRef(StepSummaryBuildInput input) {
        // 判断输入是否为空，空时返回空引用。
        if (input == null || input.getOutput() == null) {
            return null;
        }
        // 判断输出是否为映射，非映射时返回空引用。
        if (!(input.getOutput() instanceof Map<?, ?> map)) {
            return null;
        }
        // 读取原始引用字段。
        Object rawRef = map.get(RuntimeOutputKeys.RAW_REF);
        // 返回原始引用字符串。
        return rawRef == null ? null : String.valueOf(rawRef);
    }

    private Map<String, Object> resolveResultRef(StepSummaryBuildInput input) {
        // 判断输入是否为空，空时返回空映射。
        if (input == null || input.getOutput() == null) {
            return Map.of();
        }
        // 判断输出是否为映射，非映射时返回空映射。
        if (!(input.getOutput() instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        // 读取结果字段并判断是否为映射。
        Object resultObj = map.get(RuntimeOutputKeys.RESULT);
        if (!(resultObj instanceof Map<?, ?> resultMap) || resultMap.isEmpty()) {
            return Map.of();
        }
        // 构建结果引用映射，避免存放原始数据。
        Map<String, Object> resultRef = new LinkedHashMap<>();
        // 读取结果 kind 字段并写入。
        copyIfPresent(resultRef, resultMap, "kind");
        // 读取结果 schemaVersion 字段并写入。
        copyIfPresent(resultRef, resultMap, "schemaVersion");
        // 解析数据键列表并写入。
        resultRef.put("dataKeys", resolveDataKeys(resultMap.get("data")));
        // 返回结果引用映射。
        return resultRef;
    }

    private List<String> resolveDataKeys(Object data) {
        // 判断数据是否为映射，非映射时返回空列表。
        if (!(data instanceof Map<?, ?> dataMap) || dataMap.isEmpty()) {
            return List.of();
        }
        // 构建键列表并限制数量。
        List<String> keys = new java.util.ArrayList<>();
        int index = 0;
        // 设计意图：限制抽检数据键数量，避免审计记录膨胀。
        // 循环遍历数据键集合，按上限截断。
        for (Object key : dataMap.keySet()) {
            // 判断是否达到最大保留键数上限，达到时跳出循环。
            if (properties.getMaxDataKeys() > 0 && index >= properties.getMaxDataKeys()) {
                break;
            }
            // 写入数据键名到列表。
            keys.add(String.valueOf(key));
            index++;
        }
        // 返回数据键列表。
        return keys;
    }

    private void copyIfPresent(Map<String, Object> target, Map<?, ?> source, String key) {
        // 判断目标或来源是否为空，空时直接返回。
        if (target == null || source == null || !StringUtils.hasText(key)) {
            return;
        }
        // 读取目标字段值。
        Object value = source.get(key);
        // 判断字段是否为空，非空时写入。
        if (value != null) {
            // 写入字段值到目标映射。
            target.put(key, value);
        }
    }

    private Map<String, Object> copySummary(Map<String, Object> summary) {
        // 判断摘要映射是否为空，空时返回空映射。
        if (summary == null || summary.isEmpty()) {
            return Map.of();
        }
        // 复制摘要映射，避免外部修改。
        return new LinkedHashMap<>(summary);
    }

    private Map<String, Object> copyQuality(SemanticSummaryQuality quality) {
        // 判断质量对象是否为空，空时返回空映射。
        if (quality == null) {
            return Map.of();
        }
        // 构建质量映射副本。
        Map<String, Object> map = new LinkedHashMap<>(quality.toMap());
        // 判断告警列表是否为空，非空时写入告警字段。
        if (quality.getWarnings() != null && !quality.getWarnings().isEmpty()) {
            // 写入质量告警字段。
            map.put(RuntimeOutputKeys.SUMMARY_WARNINGS, quality.getWarnings());
        }
        // 返回质量映射副本。
        return map;
    }
}
