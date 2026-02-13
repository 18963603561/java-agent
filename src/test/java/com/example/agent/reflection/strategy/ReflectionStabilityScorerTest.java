package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionStabilityProperties;
import com.example.agent.reflection.model.ReflectionContext;
import com.example.agent.reflection.model.ReflectionOutputDigest;
import com.example.agent.reflection.model.ReflectionOutputSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反思稳定性评分器测试。
 */
class ReflectionStabilityScorerTest {

    @Test
    void shouldBeUnstableWhenSummaryEmpty() {
        // 构建稳定性配置。
        ReflectionStabilityProperties properties = new ReflectionStabilityProperties();
        // 设置最低评分阈值。
        properties.setMinScore(0.6D);
        // 设置摘要最小字符数。
        properties.setMinSummaryChars(10);
        // 设置结果最小键数量。
        properties.setMinResultKeys(1);
        // 构建稳定性评分器。
        ReflectionStabilityScorer scorer = new ReflectionStabilityScorer(properties);

        // 构建摘要对象。
        ReflectionOutputSummary summary = new ReflectionOutputSummary("");
        // 构建指纹对象。
        ReflectionOutputDigest digest = new ReflectionOutputDigest(0, List.of(), 0, false);
        // 初始化反思上下文构建器。
        ReflectionContext.Builder contextBuilder = ReflectionContext.builder();
        // 写入摘要对象。
        contextBuilder.outputSummary(summary);
        // 写入指纹对象。
        contextBuilder.outputDigest(digest);
        // 写入结果映射。
        contextBuilder.result(Map.of());
        // 构建反思上下文。
        ReflectionContext context = contextBuilder.build();

        // 初始化执行上下文构建器。
        ReflectionExecutionContext.Builder execBuilder = ReflectionExecutionContext.builder();
        // 写入反思上下文。
        execBuilder.reflectionContext(context);
        // 构建执行上下文。
        ReflectionExecutionContext exec = execBuilder.build();

        // 执行稳定性评估。
        ReflectionStabilityEvaluation evaluation = scorer.evaluate(exec);
        // 断言评估结果不为空。
        assertNotNull(evaluation);
        // 判断稳定性是否为不稳定。
        boolean unstable = !evaluation.isStable();
        // 断言稳定性为不稳定。
        assertTrue(unstable);
    }
}
