package com.example.agent.capabilities.context.compression.experiment.infrastructure.evaluator;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionQualityScore;
import com.example.agent.capabilities.context.compression.experiment.domain.policy.CompressionQualityEvaluator;
import org.springframework.stereotype.Component;

/**
 * 基于规则的压缩质量评估器。
 */
@Component
public class RuleBasedCompressionQualityEvaluator implements CompressionQualityEvaluator {

    @Override
    public CompressionQualityScore evaluate(CompressionExecutionResult primaryResult,
                                            CompressionExecutionResult shadowResult) {
        CompressionQualityScore score = new CompressionQualityScore();
        double primaryRate = resolveCompressionRate(primaryResult);
        double shadowRate = resolveCompressionRate(shadowResult);
        score.setPrimaryCompressionRate(primaryRate);
        score.setShadowCompressionRate(shadowRate);

        // 基线判定：影子轨缺失时按主轨得分并关闭替换建议。
        if (shadowResult == null) {
            score.setScore(primaryResult != null && primaryResult.isSuccess() ? 100D : 0D);
            score.setShadowPreferred(false);
            score.setReason("SHADOW_RESULT_MISSING");
            score.setDegradationRate(0D);
            return score;
        }

        // 成功判定：主轨失败且影子轨成功时建议影子轨胜出。
        if ((primaryResult == null || !primaryResult.isSuccess()) && shadowResult.isSuccess()) {
            score.setScore(100D);
            score.setShadowPreferred(true);
            score.setReason("PRIMARY_FAILED_SHADOW_SUCCESS");
            score.setDegradationRate(1D);
            return score;
        }

        // 成功判定：主轨成功且影子轨失败时维持主轨。
        if (primaryResult != null && primaryResult.isSuccess() && !shadowResult.isSuccess()) {
            score.setScore(100D);
            score.setShadowPreferred(false);
            score.setReason("SHADOW_FAILED_PRIMARY_SUCCESS");
            score.setDegradationRate(0D);
            return score;
        }

        // 退化计算：主轨压缩率低于影子轨时视为退化。
        double degradation = Math.max(0D, shadowRate - primaryRate);
        score.setDegradationRate(degradation);
        double boundedScore = Math.max(0D, 100D - (degradation * 100D));
        score.setScore(boundedScore);

        // 评分判定：主轨压缩率显著低于影子轨时建议影子轨胜出。
        if (degradation >= 0.15D) {
            score.setShadowPreferred(true);
            score.setReason("PRIMARY_DEGRADATION_HIGH");
            return score;
        }
        score.setShadowPreferred(false);
        score.setReason("PRIMARY_ACCEPTABLE");
        return score;
    }

    /**
     * 计算压缩率。
     */
    private double resolveCompressionRate(CompressionExecutionResult result) {
        // 空值处理：缺少结果或令牌信息时按零压缩率处理。
        if (result == null || result.getOriginalTokens() == null || result.getCompressedTokens() == null) {
            return 0D;
        }
        // 边界处理：原始令牌小于等于零时按零压缩率处理。
        if (result.getOriginalTokens() <= 0) {
            return 0D;
        }
        // 压缩率计算：输出保留比例越低，压缩率越高。
        double rate = 1D - (result.getCompressedTokens() / (double) result.getOriginalTokens());
        if (rate < 0D) {
            return 0D;
        }
        if (rate > 1D) {
            return 1D;
        }
        return rate;
    }
}

