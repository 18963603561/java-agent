package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.model.SemanticSummary;
import com.example.agent.runtime.model.SummarySourceRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义摘要质量评分器测试。
 */
class SemanticSummaryQualityScorerTest {

    @Test
    void shouldMarkMissingWhenSummaryEmpty() {
        // 构建质量配置。
        SemanticSummaryQualityProperties properties = new SemanticSummaryQualityProperties();
        // 设置最小摘要长度。
        properties.setMinChars(50);
        // 设置最低评分阈值。
        properties.setMinScore(0.6D);
        // 构建评分器。
        SemanticSummaryQualityScorer scorer = new SemanticSummaryQualityScorer(properties);

        // 构建输出映射。
        Map<String, Object> output = Map.of(RuntimeOutputKeys.RAW_REF, "raw-1");
        // 初始化摘要输入构建器。
        StepSummaryBuildInput.Builder inputBuilder = StepSummaryBuildInput.builder();
        // 写入输出映射。
        inputBuilder.output(output);
        // 构建摘要输入。
        StepSummaryBuildInput input = inputBuilder.build();

        // 构建空列表。
        List<String> emptyList = List.of();
        // 构建空来源引用列表。
        List<SummarySourceRef> emptyRefs = List.of();
        // 构建语义摘要。
        SemanticSummary summary = new SemanticSummary("no_summary", emptyList, emptyList, emptyList, emptyRefs, false);
        // 执行质量评分。
        SemanticSummaryQuality quality = scorer.score(input, summary);

        // 断言评分结果不为空。
        assertNotNull(quality);
        // 读取告警列表。
        List<String> warnings = quality.getWarnings();
        // 判断告警是否包含摘要为空。
        boolean hasEmptyWarning = warnings.contains("summary_empty");
        // 断言告警包含摘要为空。
        assertTrue(hasEmptyWarning);
        // 读取缺失字段列表。
        List<String> missing = quality.getMissingFields();
        // 判断缺失字段是否包含摘要文本。
        boolean hasTextMissing = missing.contains(RuntimeOutputKeys.SUMMARY_TEXT);
        // 断言缺失字段包含摘要文本。
        assertTrue(hasTextMissing);
    }
}
