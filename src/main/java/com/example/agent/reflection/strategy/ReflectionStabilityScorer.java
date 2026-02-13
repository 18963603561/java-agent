package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionStabilityProperties;
import com.example.agent.reflection.model.ReflectionContext;
import com.example.agent.reflection.model.ReflectionOutputDigest;
import com.example.agent.reflection.model.ReflectionOutputSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 反思稳定性评分器。
 */
@Component
public class ReflectionStabilityScorer {

    /**
     * 稳定性配置。
     */
    private final ReflectionStabilityProperties properties;

    public ReflectionStabilityScorer(ReflectionStabilityProperties properties) {
        this.properties = properties;
    }

    /**
     * 计算稳定性评分。
     *
     * @param context 反思执行上下文
     * @return 稳定性评估结果
     */
    public ReflectionStabilityEvaluation evaluate(ReflectionExecutionContext context) {
        // 判断配置是否可用，未启用时返回满分稳定结果。
        if (properties == null || !properties.isEnable()) {
            // 返回满分稳定评估结果。
            return new ReflectionStabilityEvaluation(1.0D, true, List.of());
        }

        // 设计意图：在进入策略链前评估输入稳定性，避免低质上下文误判。
        // 读取反思上下文，空时使用空上下文。
        ReflectionContext reflectionContext = context != null ? context.getReflectionContext() : null;
        // 读取摘要对象。
        ReflectionOutputSummary summary = reflectionContext != null ? reflectionContext.getOutputSummary() : null;
        // 读取摘要文本。
        String summaryText = summary != null ? summary.getSummary() : null;
        // 读取结果映射。
        Map<String, Object> result = reflectionContext != null ? reflectionContext.getResult() : null;
        // 读取输出指纹。
        ReflectionOutputDigest digest = reflectionContext != null ? reflectionContext.getOutputDigest() : null;

        // 初始化告警列表。
        List<String> warnings = new ArrayList<>();
        double score = 1.0D;

        // 判断摘要文本是否为空，空时扣减评分并记录告警。
        if (!StringUtils.hasText(summaryText)) {
            // 扣减摘要缺失惩罚。
            score -= properties.getSummaryPenalty();
            // 记录摘要为空告警。
            warnings.add("summary_empty");
        } else {
            // 判断摘要长度是否低于阈值，过短时扣减评分并记录告警。
            if (summaryText.trim().length() < properties.getMinSummaryChars()) {
                // 扣减摘要过短惩罚。
                score -= properties.getSummaryPenalty() * 0.5D;
                // 记录摘要过短告警。
                warnings.add("summary_short");
            }
        }

        // 判断结果映射是否为空或键数量不足，命中时扣减评分并记录告警。
        if (result == null || result.isEmpty() || result.size() < properties.getMinResultKeys()) {
            // 扣减结果缺失惩罚。
            score -= properties.getResultPenalty();
            // 记录结果为空告警。
            warnings.add("result_empty");
        }

        // 判断指纹是否缺失，缺失时扣减评分并记录告警。
        if (digest == null || digest.getKeyCount() == null) {
            // 扣减指纹缺失惩罚。
            score -= properties.getDigestPenalty();
            // 记录指纹缺失告警。
            warnings.add("digest_missing");
        }

        // 对评分进行范围裁剪。
        double clampedScore = clampScore(score);
        // 判断是否达到稳定阈值。
        boolean stable = clampedScore >= properties.getMinScore();
        // 返回稳定性评估结果。
        return new ReflectionStabilityEvaluation(clampedScore, stable, warnings);
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
}
