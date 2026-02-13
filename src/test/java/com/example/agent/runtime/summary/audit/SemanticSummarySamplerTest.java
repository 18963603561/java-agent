package com.example.agent.runtime.summary.audit;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.summary.SemanticSummaryQuality;
import com.example.agent.runtime.summary.StepSummaryBuildInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 语义摘要抽检器测试。
 */
class SemanticSummarySamplerTest {

    @Test
    void shouldSampleWhenLowScoreForced() {
        // 构建抽检配置。
        SemanticSummaryAuditProperties properties = new SemanticSummaryAuditProperties();
        // 启用抽检。
        properties.setEnable(true);
        // 设置抽检比例为 0。
        properties.setSampleRate(0.0D);
        // 启用低分强制抽检。
        properties.setForceLowScore(true);
        // 设置低分阈值。
        properties.setLowScoreThreshold(0.5D);

        // 构建内存抽检落库实现。
        MemorySink sink = new MemorySink();
        // 构建抽检器。
        SemanticSummarySampler sampler = new SemanticSummarySampler(properties, sink);

        // 构建质量结果。
        SemanticSummaryQuality quality = new SemanticSummaryQuality();
        // 写入低分评分。
        quality.setScore(0.1D);

        // 构建摘要映射。
        Map<String, Object> summary = Map.of(RuntimeOutputKeys.SUMMARY_TEXT, "ok");
        // 构建输出映射。
        Map<String, Object> output = Map.of(RuntimeOutputKeys.RAW_REF, "raw-1");
        // 初始化摘要输入构建器。
        StepSummaryBuildInput.Builder inputBuilder = StepSummaryBuildInput.builder();
        // 写入输出映射。
        inputBuilder.output(output);
        // 构建摘要输入。
        StepSummaryBuildInput input = inputBuilder.build();

        // 执行抽检。
        sampler.sample(input, summary, quality);

        // 读取记录数量。
        int size = sink.getRecords().size();
        // 断言抽检记录数量为 1。
        assertEquals(1, size);
    }

    /**
     * 内存抽检落库实现。
     */
    private static final class MemorySink implements SemanticSummaryAuditSink {

        private final List<SemanticSummaryAuditRecord> records = new ArrayList<>();

        @Override
        public void record(SemanticSummaryAuditRecord record) {
            // 写入抽检记录到内存列表。
            records.add(record);
        }

        private List<SemanticSummaryAuditRecord> getRecords() {
            // 返回记录列表。
            return records;
        }
    }
}
