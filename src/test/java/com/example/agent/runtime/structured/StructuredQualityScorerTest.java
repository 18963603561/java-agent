package com.example.agent.runtime.structured;

import com.example.agent.runtime.structured.result.StructuredQuality;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.DefaultStructuredData;
import com.example.agent.runtime.structured.structured.SqlData;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构化质量评分器测试。
 */
class StructuredQualityScorerTest {

    @Test
    void shouldWarnWhenSqlMissing() {
        // 构建质量配置。
        StructuredQualityProperties properties = new StructuredQualityProperties();
        // 构建质量评分器。
        StructuredQualityScorer scorer = new StructuredQualityScorer(properties);

        // 构建 SQL 数据对象。
        SqlData data = new SqlData();
        // 构建结构化结果。
        StructuredResult<SqlData> result = StructuredResult.of(ResultKind.SQL, 1, data);
        // 执行质量评分。
        StructuredQuality quality = scorer.score(result);

        // 断言质量结果不为空。
        assertNotNull(quality);
        // 读取告警列表。
        List<String> warnings = quality.getWarnings();
        // 判断告警是否包含 sql 缺失。
        boolean hasMissingSql = warnings.contains("missing_sql");
        // 断言告警包含 sql 缺失。
        assertTrue(hasMissingSql);
        // 读取缺失字段列表。
        List<String> missingFields = quality.getMissingFields();
        // 判断缺失字段是否包含 sql。
        boolean hasSqlField = missingFields.contains("sql");
        // 断言缺失字段包含 sql。
        assertTrue(hasSqlField);
        // 断言默认截断标记为 false，避免出现 null 语义。
        assertFalse(quality.isTruncated());
    }

    @Test
    void shouldMarkTruncatedWhenDataContainsTruncatedFlag() {
        // 构建质量配置。
        StructuredQualityProperties properties = new StructuredQualityProperties();
        // 构建质量评分器。
        StructuredQualityScorer scorer = new StructuredQualityScorer(properties);

        // 构建包含截断标记的结构化结果。
        StructuredResult<DefaultStructuredData> result = StructuredResult.defaultResult(
                ResultKind.DECISION,
                1,
                java.util.Map.of("truncated", true, "keys", List.of("a"), "size", 1)
        );

        // 执行质量评分。
        StructuredQuality quality = scorer.score(result);
        // 断言截断标记可被识别。
        assertNotNull(quality);
        assertTrue(quality.isTruncated());
    }
}
