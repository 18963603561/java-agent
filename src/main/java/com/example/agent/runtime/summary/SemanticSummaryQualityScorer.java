package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.model.SemanticSummary;
import com.example.agent.runtime.model.SummarySourceRef;
import com.example.agent.runtime.model.SummarySourceRefType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 语义摘要质量评分器。
 */
@Component
public class SemanticSummaryQualityScorer {

    /**
     * 质量评分配置。
     */
    private final SemanticSummaryQualityProperties properties;

    public SemanticSummaryQualityScorer(SemanticSummaryQualityProperties properties) {
        this.properties = properties;
    }

    /**
     * 计算语义摘要质量。
     *
     * @param input 摘要输入
     * @param summary 语义摘要
     * @return 质量结果
     */
    public SemanticSummaryQuality score(StepSummaryBuildInput input, SemanticSummary summary) {
        // 判断配置是否可用，未启用时直接返回空。
        if (properties == null || !properties.isEnable()) {
            return null;
        }

        // 设计意图：用轻量规则衡量摘要质量，避免引入模型依赖。
        // 解析输出映射，供引用字段检查使用。
        Map<String, Object> outputMap = resolveOutputMap(input == null ? null : input.getOutput());
        // 读取摘要文本，作为质量评分输入。
        String text = summary != null ? summary.getText() : null;
        // 读取截断标记，影响评分惩罚。
        boolean truncated = summary != null && summary.isTruncated();
        // 读取来源引用列表，判断是否缺失。
        List<SummarySourceRef> sourceRefs = summary != null ? summary.getSourceRefs() : List.of();

        // 初始化缺失字段列表。
        List<String> missingFields = new ArrayList<>();
        // 初始化告警列表。
        List<String> warnings = new ArrayList<>();

        // 判断摘要文本是否为空或占位，空时记录缺失与告警。
        if (!StringUtils.hasText(text) || isPlaceholder(text)) {
            // 记录文本缺失字段。
            missingFields.add(RuntimeOutputKeys.SUMMARY_TEXT);
            // 记录摘要为空告警。
            warnings.add("summary_empty");
        }
        // 判断是否截断，截断时记录告警。
        if (truncated) {
            // 记录摘要截断告警。
            warnings.add("summary_truncated");
        }

        // 解析原始引用，判断来源引用是否缺失。
        String rawRef = resolveRawRef(outputMap);
        // 判断原始引用存在但来源引用缺失，记录缺失与告警。
        if (StringUtils.hasText(rawRef) && !hasRawRef(sourceRefs)) {
            // 记录来源引用缺失字段。
            missingFields.add(RuntimeOutputKeys.SUMMARY_SOURCE_REFS);
            // 记录来源引用缺失告警。
            warnings.add("source_ref_missing");
        }

        // 计算覆盖度评分。
        double coverage = calculateCoverage(text);
        // 计算一致性评分。
        double coherence = calculateCoherence(text);
        // 计算基础评分。
        double score = 0.6D * coherence + 0.4D * coverage;
        // 根据缺失字段与截断状态扣减评分。
        score = applyPenalties(score, missingFields.size(), truncated);
        // 对评分进行范围裁剪。
        double clampedScore = clampScore(score);

        // 判断覆盖度是否过低，过低时记录告警。
        if (coverage < properties.getLowCoverageThreshold()) {
            // 记录覆盖度过低告警。
            warnings.add("coverage_low");
        }
        // 判断评分是否低于阈值，低于阈值时记录告警。
        if (clampedScore < properties.getMinScore()) {
            // 记录评分过低告警。
            warnings.add("score_below_threshold");
        }

        // 构建质量结果对象。
        SemanticSummaryQuality quality = new SemanticSummaryQuality();
        // 写入评分字段。
        quality.setScore(clampedScore);
        // 写入覆盖度字段。
        quality.setCoverage(coverage);
        // 写入一致性字段。
        quality.setCoherence(coherence);
        // 写入缺失字段列表。
        quality.setMissingFields(missingFields);
        // 写入告警列表。
        quality.setWarnings(warnings);
        // 返回质量结果对象。
        return quality;
    }

    private Map<String, Object> resolveOutputMap(Object output) {
        // 输出为映射时直接转换为可读映射。
        if (output instanceof Map<?, ?> map && !map.isEmpty()) {
            // 初始化映射副本，隔离外部修改影响。
            Map<String, Object> copied = new LinkedHashMap<>();
            // 循环遍历映射条目，逐项写入副本。
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                // 写入条目键值，统一键为字符串。
                copied.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            // 返回复制后的映射结果。
            return copied;
        }
        // 输出为空时返回空映射。
        return Map.of();
    }

    private String resolveRawRef(Map<String, Object> outputMap) {
        // 判断输出映射是否为空，空时返回空引用。
        if (outputMap == null || outputMap.isEmpty()) {
            return null;
        }
        // 读取原始引用字段。
        Object rawRef = outputMap.get(RuntimeOutputKeys.RAW_REF);
        // 返回原始引用字符串。
        return rawRef == null ? null : String.valueOf(rawRef);
    }

    private boolean hasRawRef(List<SummarySourceRef> refs) {
        // 判断引用列表是否为空，空时返回否。
        if (refs == null || refs.isEmpty()) {
            // 返回 false，表示无原始引用。
            return false;
        }
        // 循环遍历来源引用列表，判断是否存在 rawRef。
        for (SummarySourceRef ref : refs) {
            // 判断引用是否为空，空时跳过。
            if (ref == null) {
                // 跳过空引用，继续遍历。
                continue;
            }
            // 判断类型是否为原始引用且值不为空。
            if (ref.getType() == SummarySourceRefType.RAW_REF && StringUtils.hasText(ref.getValue())) {
                // 返回 true，表示存在原始引用。
                return true;
            }
        }
        // 返回 false，表示未找到原始引用。
        return false;
    }

    private double calculateCoverage(String text) {
        // 判断摘要文本是否为空，空时覆盖度为 0。
        if (!StringUtils.hasText(text)) {
            return 0.0D;
        }
        // 读取最小字符数阈值。
        int minChars = properties.getMinChars();
        // 判断最小字符数是否有效，无效时返回满分。
        if (minChars <= 0) {
            return 1.0D;
        }
        // 计算覆盖度并裁剪到 0-1。
        return Math.min(1.0D, (double) text.length() / (double) minChars);
    }

    private double calculateCoherence(String text) {
        // 判断摘要文本是否为空或占位，空时一致性为 0。
        if (!StringUtils.hasText(text) || isPlaceholder(text)) {
            return 0.0D;
        }
        // 返回一致性满分。
        return 1.0D;
    }

    private double applyPenalties(double score, int missingCount, boolean truncated) {
        double penalty = 0.0D;
        // 判断缺失字段数量是否大于 0，存在时叠加惩罚。
        if (missingCount > 0) {
            // 计算缺失字段惩罚。
            penalty += missingCount * properties.getMissingFieldPenalty();
        }
        // 判断是否截断，截断时叠加惩罚。
        if (truncated) {
            // 计算截断惩罚。
            penalty += properties.getTruncatedPenalty();
        }
        // 返回扣减惩罚后的评分。
        return score - penalty;
    }

    private double clampScore(double score) {
        // 判断评分是否小于 0，小于 0 时返回 0。
        if (score < 0.0D) {
            return 0.0D;
        }
        // 判断评分是否大于 1，大于 1 时返回 1。
        if (score > 1.0D) {
            return 1.0D;
        }
        // 返回原始评分。
        return score;
    }

    private boolean isPlaceholder(String text) {
        // 判断文本是否为空，空时直接返回否。
        if (!StringUtils.hasText(text)) {
            return false;
        }
        // 归一化文本用于占位判断。
        String normalized = text.trim().toLowerCase();
        // 判断是否命中占位文本。
        return "no_summary".equals(normalized) || "(summary disabled)".equals(normalized);
    }
}
