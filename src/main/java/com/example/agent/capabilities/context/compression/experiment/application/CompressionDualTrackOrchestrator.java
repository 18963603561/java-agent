package com.example.agent.capabilities.context.compression.experiment.application;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.application.CompressionExecutionRouter;
import com.example.agent.capabilities.context.compression.application.port.CompressionExecutor;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionComparisonRecord;
import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionQualityScore;
import com.example.agent.capabilities.context.compression.experiment.domain.model.DualTrackExecutionResult;
import com.example.agent.capabilities.context.compression.experiment.domain.model.RollbackDecision;
import com.example.agent.capabilities.context.compression.experiment.domain.model.RolloutDecision;
import com.example.agent.capabilities.context.compression.experiment.domain.policy.CompressionRolloutPolicy;
import com.example.agent.capabilities.context.compression.experiment.domain.policy.CompressionQualityEvaluator;
import com.example.agent.capabilities.context.compression.experiment.domain.port.CompressionComparisonRepository;
import com.example.agent.capabilities.context.compression.experiment.application.guard.CompressionRollbackGuard;
import com.example.agent.capabilities.context.compression.infrastructure.llm.LlmCompressionExecutorAdapter;
import com.example.agent.capabilities.context.compression.infrastructure.rule.RuleCompressionExecutorAdapter;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 压缩双轨执行编排器。
 */
@Component
public class CompressionDualTrackOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CompressionDualTrackOrchestrator.class);

    /**
     * 执行路由器。
     */
    private final CompressionExecutionRouter executionRouter;

    /**
     * 灰度策略。
     */
    private final CompressionRolloutPolicy rolloutPolicy;

    /**
     * 对比记录仓储。
     */
    private final CompressionComparisonRepository comparisonRepository;

    /**
     * 质量评估器。
     */
    private final CompressionQualityEvaluator qualityEvaluator;

    /**
     * 自动回滚守卫。
     */
    private final CompressionRollbackGuard rollbackGuard;

    public CompressionDualTrackOrchestrator(CompressionExecutionRouter executionRouter,
                                            CompressionRolloutPolicy rolloutPolicy,
                                            CompressionComparisonRepository comparisonRepository,
                                            CompressionQualityEvaluator qualityEvaluator,
                                            CompressionRollbackGuard rollbackGuard) {
        this.executionRouter = executionRouter;
        this.rolloutPolicy = rolloutPolicy;
        this.comparisonRepository = comparisonRepository;
        this.qualityEvaluator = qualityEvaluator;
        this.rollbackGuard = rollbackGuard;
    }

    /**
     * 执行压缩编排并按需触发双轨实验。
     *
     * @param command 压缩命令
     * @param tenantId 租户标识
     * @param scene 场景标识
     * @param sessionId 会话标识
     * @param workflowId 工作流标识
     * @return 双轨执行结果
     */
    public DualTrackExecutionResult execute(CompressionCommand command,
                                            String tenantId,
                                            String scene,
                                            String sessionId,
                                            String workflowId) {
        DualTrackExecutionResult result = new DualTrackExecutionResult();
        RolloutDecision decision = rolloutPolicy != null
                ? rolloutPolicy.decide(tenantId, scene, sessionId, workflowId)
                : null;
        result.setRolloutDecision(decision);
        boolean dualTrackEnabled = decision != null && decision.isDualTrackEnabled();
        result.setDualTrackEnabled(dualTrackEnabled);

        String primaryMode = resolvePrimaryMode();
        String shadowMode = resolveShadowMode(primaryMode);

        // 主轨执行：优先执行主轨并确保返回值可用。
        CompressionExecutionResult primaryResult = executeByMode(command, primaryMode);
        result.setPrimaryResult(primaryResult);

        // 双轨判定：未开启双轨时直接返回主轨结果。
        if (!dualTrackEnabled || !StringUtils.hasText(shadowMode)) {
            return result;
        }

        CompressionExecutionResult shadowResult;
        try {
            // 影子执行：仅做对比观测，不影响主轨输出。
            shadowResult = executeByMode(command, shadowMode);
        } catch (RuntimeException exception) {
            // 异常处理：影子轨异常降级为失败记录，不影响主轨。
            shadowResult = new CompressionExecutionResult();
            shadowResult.setSource(shadowMode);
            shadowResult.setSuccess(false);
            shadowResult.setFailureReason("SHADOW_EXECUTION_EXCEPTION");
            log.warn("压缩影子轨执行异常，降级为失败记录, tenantId={}, workflowId={}, shadowMode={}",
                    tenantId,
                    workflowId,
                    shadowMode,
                    exception);
        }
        result.setShadowResult(shadowResult);

        CompressionQualityScore qualityScore = evaluateQuality(primaryResult, shadowResult);
        RollbackDecision rollbackDecision = evaluateRollback(primaryResult, shadowResult, qualityScore);
        // 回滚判定：命中回滚时将影子轨结果提升为主结果。
        if (rollbackDecision.isRollback() && shadowResult != null) {
            result.setPrimaryResult(shadowResult);
            primaryResult = shadowResult;
        }

        CompressionComparisonRecord record = buildComparisonRecord(primaryResult,
                shadowResult,
                decision,
                qualityScore,
                rollbackDecision,
                tenantId,
                workflowId,
                sessionId,
                primaryMode,
                shadowMode);
        result.setComparisonRecord(record);
        // 仓储写入：写入失败仅告警，不影响主流程。
        persistComparisonRecord(record, tenantId, workflowId);
        return result;
    }

    /**
     * 按模式执行压缩。
     */
    private CompressionExecutionResult executeByMode(CompressionCommand command, String mode) {
        // 路由调用：优先按明确模式执行，避免策略漂移。
        if (executionRouter != null && StringUtils.hasText(mode)) {
            return executionRouter.execute(command, mode);
        }
        // 降级调用：模式缺失时走默认路由模式。
        if (executionRouter != null) {
            return executionRouter.execute(command);
        }
        CompressionExecutionResult failed = new CompressionExecutionResult();
        failed.setSuccess(false);
        failed.setSource(mode);
        failed.setFailureReason("EXECUTION_ROUTER_MISSING");
        return failed;
    }

    /**
     * 解析主轨模式。
     */
    private String resolvePrimaryMode() {
        if (executionRouter == null) {
            return RuleCompressionExecutorAdapter.MODE;
        }
        String mode = executionRouter.resolveConfiguredMode();
        if (!StringUtils.hasText(mode)) {
            return RuleCompressionExecutorAdapter.MODE;
        }
        return mode;
    }

    /**
     * 解析影子轨模式。
     */
    private String resolveShadowMode(String primaryMode) {
        // 模式判定：主轨为 llm 时影子轨切 rule。
        if (LlmCompressionExecutorAdapter.MODE.equals(primaryMode)) {
            return RuleCompressionExecutorAdapter.MODE;
        }
        // 模式判定：主轨为 rule 时影子轨切 llm。
        if (RuleCompressionExecutorAdapter.MODE.equals(primaryMode)) {
            return LlmCompressionExecutorAdapter.MODE;
        }
        return null;
    }

    /**
     * 构造对比记录。
     */
    private CompressionComparisonRecord buildComparisonRecord(CompressionExecutionResult primaryResult,
                                                              CompressionExecutionResult shadowResult,
                                                              RolloutDecision decision,
                                                              CompressionQualityScore qualityScore,
                                                              RollbackDecision rollbackDecision,
                                                              String tenantId,
                                                              String workflowId,
                                                              String sessionId,
                                                              String primaryMode,
                                                              String shadowMode) {
        CompressionComparisonRecord record = new CompressionComparisonRecord();
        record.setRecordId(UUID.randomUUID().toString());
        record.setTimestamp(Instant.now());
        record.setTenantId(tenantId);
        record.setWorkflowId(workflowId);
        record.setSessionId(sessionId);
        record.setRolloutVersion(decision != null ? decision.getRolloutVersion() : null);
        record.setPrimarySource(primaryResult != null && StringUtils.hasText(primaryResult.getSource())
                ? primaryResult.getSource()
                : primaryMode);
        record.setShadowSource(shadowResult != null && StringUtils.hasText(shadowResult.getSource())
                ? shadowResult.getSource()
                : shadowMode);
        record.setPrimarySuccess(primaryResult != null && primaryResult.isSuccess());
        record.setShadowSuccess(shadowResult != null && shadowResult.isSuccess());
        record.setPrimaryFailureReason(primaryResult != null ? primaryResult.getFailureReason() : null);
        record.setShadowFailureReason(shadowResult != null ? shadowResult.getFailureReason() : null);
        record.setPrimaryOriginalTokens(primaryResult != null ? primaryResult.getOriginalTokens() : null);
        record.setPrimaryCompressedTokens(primaryResult != null ? primaryResult.getCompressedTokens() : null);
        record.setShadowOriginalTokens(shadowResult != null ? shadowResult.getOriginalTokens() : null);
        record.setShadowCompressedTokens(shadowResult != null ? shadowResult.getCompressedTokens() : null);
        record.setQualityScore(qualityScore != null ? qualityScore.getScore() : null);
        record.setRollbackApplied(rollbackDecision != null && rollbackDecision.isRollback());
        record.setRollbackReason(rollbackDecision != null ? rollbackDecision.getReason() : null);
        record.setWinnerSource(rollbackDecision != null ? rollbackDecision.getWinnerSource() : null);
        return record;
    }

    /**
     * 评估双轨质量。
     */
    private CompressionQualityScore evaluateQuality(CompressionExecutionResult primaryResult,
                                                    CompressionExecutionResult shadowResult) {
        // 依赖守卫：评估器缺失时返回默认质量结果。
        if (qualityEvaluator == null) {
            CompressionQualityScore score = new CompressionQualityScore();
            score.setScore(100D);
            score.setReason("QUALITY_EVALUATOR_MISSING");
            score.setShadowPreferred(false);
            return score;
        }
        // 质量评估：评估主轨与影子轨收益差异，支撑回滚决策。
        return qualityEvaluator.evaluate(primaryResult, shadowResult);
    }

    /**
     * 评估是否触发回滚。
     */
    private RollbackDecision evaluateRollback(CompressionExecutionResult primaryResult,
                                              CompressionExecutionResult shadowResult,
                                              CompressionQualityScore qualityScore) {
        // 依赖守卫：回滚守卫缺失时返回默认不回滚决策。
        if (rollbackGuard == null) {
            RollbackDecision decision = new RollbackDecision();
            decision.setRollback(false);
            decision.setReason("ROLLBACK_GUARD_MISSING");
            decision.setWinnerSource(primaryResult != null ? primaryResult.getSource() : RuleCompressionExecutorAdapter.MODE);
            return decision;
        }
        // 回滚评估：根据失败、质量与时延综合决策是否切换胜出来源。
        return rollbackGuard.evaluate(primaryResult, shadowResult, qualityScore);
    }

    /**
     * 持久化对比记录。
     */
    private void persistComparisonRecord(CompressionComparisonRecord record, String tenantId, String workflowId) {
        // 依赖守卫：仓储缺失时直接返回，避免影响主链路。
        if (comparisonRepository == null || record == null) {
            return;
        }
        try {
            // 仓储调用：写入双轨对比记录供后续评估。
            comparisonRepository.save(record);
        } catch (RuntimeException exception) {
            // 异常处理：记录写入异常只告警不中断主流程。
            log.warn("压缩对比记录写入失败，忽略并继续主流程, tenantId={}, workflowId={}, recordId={}",
                    tenantId,
                    workflowId,
                    record.getRecordId(),
                    exception);
        }
    }
}

