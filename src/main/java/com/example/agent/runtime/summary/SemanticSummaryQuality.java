package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语义摘要质量结果。
 */
public class SemanticSummaryQuality {

    /**
     * 质量评分。
     */
    private Double score;

    /**
     * 覆盖度评分。
     */
    private Double coverage;

    /**
     * 一致性评分。
     */
    private Double coherence;

    /**
     * 缺失字段列表。
     */
    private List<String> missingFields;

    /**
     * 告警列表。
     */
    private List<String> warnings;

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getCoverage() {
        return coverage;
    }

    public void setCoverage(Double coverage) {
        this.coverage = coverage;
    }

    public Double getCoherence() {
        return coherence;
    }

    public void setCoherence(Double coherence) {
        this.coherence = coherence;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    /**
     * 转换为可序列化映射。
     *
     * @return 质量映射
     */
    public Map<String, Object> toMap() {
        // 初始化质量映射容器。
        Map<String, Object> map = new LinkedHashMap<>();
        // 判断评分是否为空，非空时写入质量评分字段。
        if (score != null) {
            // 写入质量评分字段。
            map.put(RuntimeOutputKeys.SUMMARY_QUALITY_SCORE, score);
        }
        // 判断覆盖度是否为空，非空时写入覆盖度字段。
        if (coverage != null) {
            // 写入覆盖度字段。
            map.put(RuntimeOutputKeys.SUMMARY_QUALITY_COVERAGE, coverage);
        }
        // 判断一致性是否为空，非空时写入一致性字段。
        if (coherence != null) {
            // 写入一致性字段。
            map.put(RuntimeOutputKeys.SUMMARY_QUALITY_COHERENCE, coherence);
        }
        // 判断缺失字段是否为空，非空时写入缺失字段列表。
        if (missingFields != null && !missingFields.isEmpty()) {
            // 写入缺失字段列表。
            map.put(RuntimeOutputKeys.SUMMARY_QUALITY_MISSING_FIELDS, missingFields);
        }
        // 返回质量映射。
        return map;
    }
}
