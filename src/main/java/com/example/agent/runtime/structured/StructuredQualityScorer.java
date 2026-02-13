package com.example.agent.runtime.structured;

import com.example.agent.runtime.structured.result.StructuredQuality;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.StructuredData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 结构化质量评分器。
 */
@Component
public class StructuredQualityScorer {

    /**
     * 质量评分配置。
     */
    private final StructuredQualityProperties properties;

    public StructuredQualityScorer(StructuredQualityProperties properties) {
        this.properties = properties;
    }

    /**
     * 计算结构化质量。
     *
     * @param result 结构化结果
     * @return 质量结果
     */
    public StructuredQuality score(StructuredResult<? extends StructuredData> result) {
        // 判断配置或结果是否可用，任一为空时直接返回。
        if (properties == null || !properties.isEnable() || result == null) {
            return null;
        }

        // 设计意图：基于 kind/schema/data 做轻量质量评分，避免引入重依赖。
        // 初始化告警列表。
        List<String> warnings = new ArrayList<>();
        // 初始化缺失字段列表。
        List<String> missingFields = new ArrayList<>();

        // 读取结果类型，作为字段规则选择依据。
        ResultKind kind = result.getKind();
        // 判断结果类型是否为空，空时记录告警。
        if (kind == null) {
            // 写入类型缺失告警。
            warnings.add("kind_missing");
        }
        // 读取结构化数据映射。
        Map<String, Object> data = result.dataAsMap();
        // 判断数据是否为空，空时记录缺失与告警。
        if (data == null || data.isEmpty()) {
            // 写入数据缺失字段。
            missingFields.add("data");
            // 写入数据为空告警。
            warnings.add("data_empty");
        }

        // 解析必填字段列表。
        List<String> requiredFields = resolveRequiredFields(kind);
        // 循环校验必填字段完整性。
        for (String field : requiredFields) {
            // 判断字段是否缺失或为空，缺失时记录。
            if (!hasValue(data.get(field))) {
                // 写入缺失字段。
                missingFields.add(field);
                // 写入字段缺失告警。
                warnings.add("missing_" + field);
            }
        }

        // 计算完整度。
        double completeness = calculateCompleteness(requiredFields.size(), missingFields.size());
        // 计算置信度。
        double confidence = calculateConfidence(kind, data, completeness, missingFields.size());
        // 判断完整度是否过低，过低时记录告警。
        if (completeness < properties.getLowCompletenessThreshold()) {
            // 写入完整度过低告警。
            warnings.add("completeness_low");
        }

        // 构建质量结果对象。
        StructuredQuality quality = new StructuredQuality();
        // 写入置信度。
        quality.setConfidence(confidence);
        // 写入完整度。
        quality.setCompleteness(completeness);
        // 写入告警列表。
        quality.setWarnings(warnings);
        // 写入缺失字段列表。
        quality.setMissingFields(missingFields);
        // 返回质量结果对象。
        return quality;
    }

    private List<String> resolveRequiredFields(ResultKind kind) {
        // 判断结果类型是否为空，空时返回空列表。
        if (kind == null) {
            return List.of();
        }
        // 根据结果类型返回必填字段列表。
        return switch (kind) {
            case SQL -> List.of("sql");
            case RECORDSET -> List.of("columns", "rows");
            case ERROR -> List.of("category", "message");
            default -> List.of("keys", "size");
        };
    }

    private double calculateCompleteness(int requiredCount, int missingCount) {
        // 判断必填字段数量是否有效，空时返回满分。
        if (requiredCount <= 0) {
            return 1.0D;
        }
        // 计算完整度并裁剪到 0-1。
        double value = 1.0D - (double) missingCount / (double) requiredCount;
        // 判断完整度是否小于 0，小于 0 时返回 0。
        if (value < 0.0D) {
            return 0.0D;
        }
        // 返回完整度评分。
        return value;
    }

    private double calculateConfidence(ResultKind kind,
                                       Map<String, Object> data,
                                       double completeness,
                                       int missingCount) {
        // 初始化基础置信度。
        double confidence = properties.getBaseConfidence() * completeness;
        // 判断是否为错误类结果，错误类使用低置信度基线。
        if (ResultKind.ERROR.equals(kind)) {
            confidence = properties.getErrorConfidence() * completeness;
        }
        // 判断数据是否为空，空时下调到空数据置信度上限。
        if (data == null || data.isEmpty()) {
            // 写入空数据置信度上限。
            confidence = Math.min(confidence, properties.getEmptyDataConfidence());
        }
        // 判断缺失字段数量是否大于 0，存在时扣减置信度。
        if (missingCount > 0) {
            // 扣减缺失字段惩罚。
            confidence = confidence - missingCount * properties.getMissingPenalty();
        }
        // 判断置信度是否小于 0，小于 0 时返回 0。
        if (confidence < 0.0D) {
            return 0.0D;
        }
        // 返回置信度评分。
        return confidence;
    }

    private boolean hasValue(Object value) {
        // 判断值是否为空，空时返回否。
        if (value == null) {
            return false;
        }
        // 判断字符串是否为空白，空白时返回否。
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        // 判断集合是否为空，空集合时返回否。
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        // 判断映射是否为空，空映射时返回否。
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        // 其他类型默认视为有值。
        return true;
    }
}
