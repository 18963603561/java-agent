package com.example.agent.capabilities.context.compression.experiment.application.guard;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionQualityScore;
import com.example.agent.capabilities.context.compression.experiment.domain.model.RollbackDecision;
import org.springframework.stereotype.Component;

/**
 * 压缩自动回滚守卫。
 */
@Component
public class CompressionRollbackGuard {

    /**
     * 压缩配置。
     */
    private final ContextCompressionProperties properties;

    public CompressionRollbackGuard(ContextCompressionProperties properties) {
        this.properties = properties;
    }

    /**
     * 评估是否触发回滚。
     *
     * @param primaryResult 主轨结果
     * @param shadowResult 影子轨结果
     * @param qualityScore 质量评分
     * @return 回滚决策
     */
    public RollbackDecision evaluate(CompressionExecutionResult primaryResult,
                                     CompressionExecutionResult shadowResult,
                                     CompressionQualityScore qualityScore) {
        RollbackDecision decision = new RollbackDecision();
        decision.setRollback(false);
        decision.setWinnerSource(primaryResult != null ? primaryResult.getSource() : "rule");
        decision.setReason("PRIMARY_KEPT");

        // 空值判定：影子轨缺失时不触发回滚。
        if (shadowResult == null) {
            return decision;
        }

        // 失败判定：主轨失败且影子轨成功时触发回滚到影子轨。
        if ((primaryResult == null || !primaryResult.isSuccess()) && shadowResult.isSuccess()) {
            decision.setRollback(true);
            decision.setWinnerSource(shadowResult.getSource());
            decision.setReason("PRIMARY_FAILED");
            return decision;
        }

        // 分数判定：质量分低于阈值且影子轨建议胜出时触发回滚。
        if (qualityScore != null && qualityScore.isShadowPreferred()) {
            double threshold = resolveQualityThreshold();
            if (qualityScore.getScore() < threshold) {
                decision.setRollback(true);
                decision.setWinnerSource(shadowResult.getSource());
                decision.setReason("QUALITY_SCORE_BELOW_THRESHOLD");
                return decision;
            }
        }

        // 时延判定：主轨超时且影子轨成功时触发回滚。
        if (isPrimaryTimeout(primaryResult) && shadowResult.isSuccess()) {
            decision.setRollback(true);
            decision.setWinnerSource(shadowResult.getSource());
            decision.setReason("PRIMARY_TIMEOUT");
            return decision;
        }
        return decision;
    }

    /**
     * 解析质量阈值。
     */
    private double resolveQualityThreshold() {
        Double ratio = properties != null && properties.getTrigger() != null
                ? properties.getTrigger().getCompressionTargetRatio()
                : null;
        if (ratio == null) {
            return 85D;
        }
        return Math.max(60D, Math.min(95D, ratio * 100D));
    }

    /**
     * 判断主轨是否超时。
     */
    private boolean isPrimaryTimeout(CompressionExecutionResult primaryResult) {
        // 空值判定：缺少主轨结果时不认定超时。
        if (primaryResult == null) {
            return false;
        }
        long timeoutMs = properties != null && properties.getLlm() != null ? properties.getLlm().getTimeoutMs() : 0L;
        if (timeoutMs <= 0) {
            return false;
        }
        // 超时判定：主轨耗时超过超时阈值时触发超时语义。
        return primaryResult.getDurationMs() > timeoutMs;
    }
}


