package com.example.agent.budget.trim.application;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.budget.trim.model.CompressionSummaryApplyResult;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.budget.trim.model.ContextCompressionResult;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.budget.trim.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.application.CompressionExecutionRouter;
import com.example.agent.capabilities.context.compression.application.CompressionModelMapper;
import com.example.agent.capabilities.context.compression.application.port.CompressionTelemetryPort;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.context.compression.domain.model.CompressionOutcome;
import com.example.agent.capabilities.context.compression.domain.policy.CompressionTriggerPolicy;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 上下文压缩服务，负责压缩流程编排与结果汇总。
 */
@Service
public class ContextCompressionService {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionService.class);

    private final ContextTokenEstimator contextTokenEstimator;
    private final CompressionTelemetryPort telemetryPort;
    private final ContextCompressionProperties properties;
    private final CompressionTriggerPolicy triggerPolicy;
    private final CompressionCooldownService cooldownService;
    private final CompressionExecutionRouter executionRouter;
    private final CompressionModelMapper compressionModelMapper;
    private final CompressionSummaryApplier summaryApplier;

    /**
     * 压缩质量窗口索引，按 workflow 维度维护窗口样本。
     */
    private final Map<String, CompressionQualityWindow> qualityWindowByWorkflow = new ConcurrentHashMap<>();

    public ContextCompressionService(TokenEstimator tokenEstimator,
                                     CompressionTelemetryPort telemetryPort,
                                     ContextCompressionProperties properties,
                                     CompressionTriggerPolicy triggerPolicy,
                                     CompressionCooldownService cooldownService,
                                     CompressionExecutionRouter executionRouter,
                                     CompressionModelMapper compressionModelMapper,
                                     CompressionSummaryApplier summaryApplier) {
        this.contextTokenEstimator = new ContextTokenEstimator(tokenEstimator);
        this.telemetryPort = telemetryPort;
        this.properties = properties;
        this.triggerPolicy = triggerPolicy;
        this.cooldownService = cooldownService;
        this.executionRouter = executionRouter;
        this.compressionModelMapper = compressionModelMapper;
        this.summaryApplier = summaryApplier;
    }

    /**
     * 在预算超限时触发压缩并回填摘要。
     *
     * @param request 压缩请求
     * @return 压缩结果
     */
    public ContextCompressionResult compressIfNeeded(ContextCompressionRequest request) {
        // 创建默认结果：即使未触发压缩也返回结构化结果对象。
        ContextCompressionResult result = new ContextCompressionResult();
        // 入参判定：请求或快照缺失时直接返回，避免后续链路空指针。
        if (request == null || request.getSnapshot() == null) {
            return result;
        }

        ContextSnapshot snapshot = request.getSnapshot();
        result.setSnapshot(snapshot);
        // 解析日志上下文：优先工作流与租户标识，便于统一观测。
        String workflowId = request.getWorkflowId();
        String tenantId = resolveTenantId(request.getTenantContext(), snapshot);

        log.info("上下文压缩开始, tenantId={}, workflowId={}", tenantId, workflowId);

        // 紧急策略判定：禁用压缩开关开启时直接短路返回。
        if (properties != null
                && properties.getEmergency() != null
                && properties.getEmergency().isDisableCompression()) {
            log.warn("上下文压缩被紧急开关禁用, tenantId={}, workflowId={}", tenantId, workflowId);
            telemetryPort.incrementWithTags("context_compression_skipped_total", "reason", "emergency_disabled");
            return result;
        }

        // 配置判定：关闭压缩开关时直接返回，避免进入后续重路径。
        if (properties != null && properties.getTrigger() != null && !properties.getTrigger().isEnabled()) {
            log.info("上下文压缩关闭，跳过执行, tenantId={}, workflowId={}", tenantId, workflowId);
            return result;
        }

        ContextBudgetAllocation allocation = request.getAllocation();
        // 预算判定：预算分配未启用时跳过压缩，维持主流程稳定。
        if (!allocation.isAllocationEnabled()) {
            log.info("预算分配未启用，跳过压缩, tenantId={}, workflowId={}, state={}",
                    tenantId,
                    workflowId,
                    allocation.getAllocationState());
            return result;
        }

        TokenView tokenView = resolveTokenView(request);
        result.setBeforeTokens(tokenView.beforeTokens());
        result.setAfterTrimTokens(tokenView.afterTrimTokens());

        // 触发判定：根据预算与比例规则决定是否执行压缩。
        String reason = triggerPolicy.resolveTriggerReason(allocation,
                tokenView.sectionTokens(),
                tokenView.afterTrimTokens());
        // 无触发原因时直接返回，避免不必要的压缩执行。
        if (!StringUtils.hasText(reason)) {
            log.debug("上下文压缩未触发, tenantId={}, workflowId={}", tenantId, workflowId);
            return result;
        }

        String sessionId = request.getSessionId();
        String cooldownKey = resolveCooldownKey(workflowId, sessionId);
        // 冷却判定：命中冷却窗口则跳过本次压缩并打点。
        if (StringUtils.hasText(cooldownKey) && cooldownService.isInCooldown(cooldownKey)) {
            telemetryPort.incrementWithTags("context_compression_skipped_total", "cooldown", "true");
            result.setSkippedCooldown(true);
            log.info("上下文压缩命中冷却窗口, tenantId={}, workflowId={}, sessionId={}", tenantId, workflowId, sessionId);
            return result;
        }

        // 会话校验：缺少会话标识时跳过压缩，避免写入链路无法关联。
        if (!StringUtils.hasText(sessionId)) {
            log.warn("上下文压缩跳过，缺少会话标识, tenantId={}, workflowId={}",
                    tenantId, workflowId);
            return result;
        }

        // 模型映射：将请求转换为压缩命令，隔离调用方与执行域对象。
        CompressionCommand command = compressionModelMapper.toCommand(request, request.getTenantContext());
        // 执行路由：根据模式解析路由到 rule/llm 执行器。
        CompressionOutcome outcome = compressionModelMapper.toOutcome(executionRouter.execute(command));
        // 结果判定：路由结果为空时直接返回，避免下游读取空执行对象。
        if (outcome == null || outcome.getExecutionResult() == null) {
            log.warn("上下文压缩执行返回空结果, tenantId={}, workflowId={}", tenantId, workflowId);
            return result;
        }
        result.setExecutionSource(outcome.getExecutionResult().getSource());
        result.setFailureReason(outcome.getExecutionResult().getFailureReason());
        result.setFallbackApplied(outcome.getExecutionResult().isFallbackApplied());
        result.setDurationMs(outcome.getExecutionResult().getDurationMs());
        result.setRolloutVersion(resolveVersion(properties != null ? properties.getRollout().getVersion() : null));
        result.setQualityGateVersion(resolveVersion(properties != null ? properties.getQualityGate().getVersion() : null));
        result.setRollbackPolicyVersion(resolveVersion(properties != null ? properties.getRollback().getVersion() : null));
        if (!outcome.getExecutionResult().isSuccess()) {
            // 失败打点：记录压缩失败原因与来源，支撑故障分析与降级评估。
            telemetryPort.incrementWithTags("context_compression_failed_total",
                    "source", sanitizeTag(outcome.getExecutionResult().getSource()),
                    "reason", sanitizeTag(outcome.getExecutionResult().getFailureReason()));
            log.warn("上下文压缩执行失败, tenantId={}, workflowId={}, source={}, reason={}",
                    tenantId,
                    workflowId,
                    outcome.getExecutionResult().getSource(),
                    outcome.getExecutionResult().getFailureReason());
            return result;
        }

        // 冷却更新：执行成功后刷新冷却窗口，防止高频重复压缩。
        if (StringUtils.hasText(cooldownKey)) {
            cooldownService.markCompressed(cooldownKey);
        }

        // 摘要回填：将压缩结果写回快照工作记忆与长期记忆引用。
        CompressionSummaryApplyResult applyResult = summaryApplier.apply(snapshot, outcome.getExecutionResult().getCompressed());
        result.setSummaryVersion(applyResult.getSummaryVersion());

        Map<ContextSection, Integer> sectionAfterCompress = contextTokenEstimator.estimateSectionTokens(snapshot);
        int afterCompressTokens = contextTokenEstimator.sumTokens(sectionAfterCompress);
        result.setAfterCompressTokens(afterCompressTokens);

        boolean stillOverBudget = triggerPolicy.isOverBudget(allocation, sectionAfterCompress, afterCompressTokens);
        // 超预算判定：压缩后仍超预算时告警并记录指标。
        if (stillOverBudget) {
            telemetryPort.increment("context_compression_still_over_budget_total");
            log.warn("上下文压缩后仍超预算, tenantId={}, workflowId={}, afterCompressTokens={}, totalBudgetTokens={}",
                    tenantId, workflowId, afterCompressTokens,
                    allocation.getTotalTokens());
        }

        result.setTriggered(true);
        result.setTriggerReason(reason);
        result.setStillOverBudget(stillOverBudget);
        result.setWinnerSource(outcome.getExecutionResult().getSource());
        // 质量评分：根据压缩比例估算质量得分，用于门禁与回滚治理。
        double qualityScore = estimateQualityScore(tokenView.afterTrimTokens(), afterCompressTokens);
        result.setQualityScore(qualityScore);
        // 回滚治理：按窗口统计失败/低分/超时后决定是否强制回滚到 rule。
        applyRollbackIfNeeded(result, workflowId, qualityScore, outcome.getExecutionResult().getDurationMs());
        recordTriggerMetrics(reason);
        telemetryPort.incrementWithTags("context_compression_success_total",
                "source", sanitizeTag(outcome.getExecutionResult().getSource()));

        log.info("上下文压缩完成, tenantId={}, workflowId={}, beforeTokens={}, afterTrimTokens={}, afterCompressTokens={}, "
                        + "triggerReason={}, compressionDurationMs={}, summaryVersion={}",
                tenantId,
                workflowId,
                tokenView.beforeTokens(),
                tokenView.afterTrimTokens(),
                afterCompressTokens,
                reason,
                outcome.getExecutionResult().getDurationMs(),
                result.getSummaryVersion());
        return result;
    }

    /**
     * 执行回滚判定并更新结果对象。
     */
    private void applyRollbackIfNeeded(ContextCompressionResult result,
                                       String workflowId,
                                       double qualityScore,
                                       Long durationMs) {
        // 回滚开关判定：未启用自动回滚时仅记录质量，不进行窗口判定。
        if (properties == null || properties.getRollback() == null || !properties.getRollback().isAutoEnabled()) {
            return;
        }
        // 工作流判定：缺少工作流标识时不做窗口统计，避免跨会话污染。
        if (!StringUtils.hasText(workflowId)) {
            return;
        }
        // 构造样本：收集当前请求的失败、低分、超时信号。
        CompressionQualitySample sample = new CompressionQualitySample();
        // 失败标记：执行来源为空或触发失败原因时记为失败样本。
        sample.failure = StringUtils.hasText(result.getFailureReason());
        // 低分标记：质量分低于门禁阈值时记为低分样本。
        sample.lowScore = isLowScore(qualityScore);
        // 超时标记：执行耗时超过 llm 超时配置时记为超时样本。
        sample.timeout = isTimeout(durationMs);
        sample.timestamp = Instant.now();

        // 窗口获取：按 workflow 维度复用质量窗口。
        CompressionQualityWindow window = qualityWindowByWorkflow.computeIfAbsent(
                workflowId,
                key -> new CompressionQualityWindow());
        // 写入样本并清理过期窗口，保持统计窗口稳定。
        window.addSample(sample, properties.getRollback().getTriggerWindowMinutes());

        // 样本量判定：样本不足时不触发回滚，避免早期误判。
        if (window.totalCount < properties.getRollback().getTriggerWindowMinSamples()) {
            return;
        }

        // 回滚判定：若任一风险率超过阈值则触发回滚。
        String rollbackReason = resolveRollbackReason(window, properties.getRollback());
        if (!StringUtils.hasText(rollbackReason)) {
            return;
        }

        // 应用回滚：将最终来源置为 rule，并补齐回滚审计信息。
        result.setRollbackApplied(true);
        result.setRollbackReason(rollbackReason);
        result.setWinnerSource("rule");
        log.warn("上下文压缩触发自动回滚, workflowId={}, reason={}, failureRate={}, lowScoreRate={}, timeoutRate={}",
                workflowId,
                rollbackReason,
                window.failureRate(),
                window.lowScoreRate(),
                window.timeoutRate());
    }

    /**
     * 解析回滚原因。
     */
    private String resolveRollbackReason(CompressionQualityWindow window, ContextCompressionProperties.Rollback rollback) {
        // 失败率判定：超过阈值时返回失败率回滚原因。
        if (window.failureRate() > rollback.getMaxFailureRate()) {
            return "failure_rate_exceeded";
        }
        // 低分率判定：超过阈值时返回低分率回滚原因。
        if (window.lowScoreRate() > rollback.getMaxLowScoreRate()) {
            return "low_score_rate_exceeded";
        }
        // 超时率判定：超过阈值时返回超时率回滚原因。
        if (window.timeoutRate() > rollback.getMaxTimeoutRate()) {
            return "timeout_rate_exceeded";
        }
        return null;
    }

    /**
     * 判定质量分是否低于阈值。
     */
    private boolean isLowScore(double qualityScore) {
        // 门禁旁路判定：紧急绕过开启时不计算低分率。
        if (properties != null
                && properties.getEmergency() != null
                && properties.getEmergency().isBypassQualityGate()) {
            return false;
        }
        // 缺失门禁配置时直接返回 false，避免误判低分。
        if (properties == null || properties.getQualityGate() == null) {
            return false;
        }
        // 阈值比较：低于最低质量分时计为低分。
        return qualityScore < properties.getQualityGate().getMinScore();
    }

    /**
     * 判定是否超时。
     */
    private boolean isTimeout(Long durationMs) {
        // 参数判定：缺失耗时时间或配置时视为未超时。
        if (durationMs == null || properties == null || properties.getLlm() == null) {
            return false;
        }
        // 阈值比较：超过配置超时阈值时计为超时。
        return durationMs > properties.getLlm().getTimeoutMs();
    }

    /**
     * 估算压缩质量得分。
     */
    private double estimateQualityScore(Integer beforeTokens, Integer afterTokens) {
        // 参数判定：任一 token 为空或 before 非正时返回默认低分。
        if (beforeTokens == null || afterTokens == null || beforeTokens <= 0) {
            return 0D;
        }
        // 比例计算：压缩后剩余比例越高，质量分越高。
        double ratio = (double) afterTokens / (double) beforeTokens;
        // 边界归一：将分值约束到 [0,1] 区间。
        if (ratio < 0D) {
            return 0D;
        }
        if (ratio > 1D) {
            return 1D;
        }
        return ratio;
    }

    /**
     * 解析版本字符串。
     */
    private String resolveVersion(String configuredVersion) {
        // 空值判定：缺失版本时统一回填 unknown。
        if (!StringUtils.hasText(configuredVersion)) {
            return "unknown";
        }
        return configuredVersion.trim();
    }

    /**
     * 解析压缩链路令牌视图，优先使用裁剪报告，否则回退统一估算器。
     */
    private TokenView resolveTokenView(ContextCompressionRequest request) {
        ContextTrimReport trimReport = request.getTrimReport();
        Map<ContextSection, Integer> sectionTokens = trimReport != null ? trimReport.getSectionTokensAfter() : null;
        Integer afterTrimTokens = trimReport != null ? trimReport.getTotalAfterTokens() : null;
        Integer beforeTokens = trimReport != null ? trimReport.getTotalBeforeTokens() : null;
        if (sectionTokens == null || afterTrimTokens == null) {
            sectionTokens = contextTokenEstimator.estimateSectionTokens(request.getSnapshot());
            afterTrimTokens = contextTokenEstimator.sumTokens(sectionTokens);
        }
        return new TokenView(sectionTokens, beforeTokens, afterTrimTokens);
    }

    /**
     * 解析冷却键，优先工作流，其次会话。
     */
    private String resolveCooldownKey(String workflowId, String sessionId) {
        if (StringUtils.hasText(workflowId)) {
            return workflowId;
        }
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }
        return null;
    }

    /**
     * 记录触发原因指标。
     */
    private void recordTriggerMetrics(String reason) {
        if (!StringUtils.hasText(reason)) {
            return;
        }
        if (reason.contains("OVER_TOTAL")) {
            telemetryPort.incrementWithTags("context_compression_trigger_total", "reason", "OVER_TOTAL");
        }
        if (reason.contains("OVER_SECTION")) {
            telemetryPort.incrementWithTags("context_compression_trigger_total", "reason", "OVER_SECTION");
        }
        if (reason.contains("OVER_RATIO")) {
            telemetryPort.incrementWithTags("context_compression_trigger_total", "reason", "OVER_RATIO");
        }
    }

    /**
     * 解析租户标识，用于日志上下文。
     */
    private String resolveTenantId(TenantContext tenantContext, ContextSnapshot snapshot) {
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTenantId())) {
            return tenantContext.getTenantId();
        }
        if (snapshot != null && snapshot.getRuntimeMeta() != null) {
            return snapshot.getRuntimeMeta().getTenantId();
        }
        return null;
    }

    /**
     * 清洗标签值。
     */
    private String sanitizeTag(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value;
    }

    /**
     * 压缩前后令牌视图。
     */
    private record TokenView(Map<ContextSection, Integer> sectionTokens,
                             Integer beforeTokens,
                             Integer afterTrimTokens) {
    }

    /**
     * 压缩质量样本。
     */
    private static class CompressionQualitySample {

        /**
         * 失败标记。
         */
        private boolean failure;

        /**
         * 低分标记。
         */
        private boolean lowScore;

        /**
         * 超时标记。
         */
        private boolean timeout;

        /**
         * 样本时间戳。
         */
        private Instant timestamp;
    }

    /**
     * 压缩质量窗口。
     */
    private static class CompressionQualityWindow {

        /**
         * 窗口样本队列。
         */
        private final Deque<CompressionQualitySample> samples = new ArrayDeque<>();

        /**
         * 总样本数。
         */
        private int totalCount;

        /**
         * 失败样本数。
         */
        private int failureCount;

        /**
         * 低分样本数。
         */
        private int lowScoreCount;

        /**
         * 超时样本数。
         */
        private int timeoutCount;

        /**
         * 写入样本并清理过期数据。
         */
        private void addSample(CompressionQualitySample sample, int triggerWindowMinutes) {
            // 入参判定：样本为空时直接返回，避免窗口计数污染。
            if (sample == null || sample.timestamp == null) {
                return;
            }
            // 入队新样本：统计总量与各风险维度计数。
            samples.addLast(sample);
            totalCount++;
            // 失败计数：失败样本增加失败计数。
            if (sample.failure) {
                failureCount++;
            }
            // 低分计数：低分样本增加低分计数。
            if (sample.lowScore) {
                lowScoreCount++;
            }
            // 超时计数：超时样本增加超时计数。
            if (sample.timeout) {
                timeoutCount++;
            }
            // 清理过期样本：维持窗口大小在配置时间范围内。
            evictExpired(triggerWindowMinutes);
        }

        /**
         * 清理窗口过期样本。
         */
        private void evictExpired(int triggerWindowMinutes) {
            // 边界判定：窗口分钟数非正时不做清理。
            if (triggerWindowMinutes <= 0) {
                return;
            }
            // 计算截止时间：早于该时间的样本将被淘汰。
            Instant threshold = Instant.now().minus(triggerWindowMinutes, ChronoUnit.MINUTES);
            // 循环淘汰：持续移除队首过期样本直到窗口合法。
            while (!samples.isEmpty()) {
                CompressionQualitySample first = samples.peekFirst();
                // 退出条件：队首样本未过期时结束清理。
                if (first == null || first.timestamp == null || !first.timestamp.isBefore(threshold)) {
                    break;
                }
                CompressionQualitySample removed = samples.removeFirst();
                totalCount = Math.max(0, totalCount - 1);
                // 失败回退：移除失败样本时同步回退失败计数。
                if (removed.failure) {
                    failureCount = Math.max(0, failureCount - 1);
                }
                // 低分回退：移除低分样本时同步回退低分计数。
                if (removed.lowScore) {
                    lowScoreCount = Math.max(0, lowScoreCount - 1);
                }
                // 超时回退：移除超时样本时同步回退超时计数。
                if (removed.timeout) {
                    timeoutCount = Math.max(0, timeoutCount - 1);
                }
            }
        }

        /**
         * 失败率。
         */
        private double failureRate() {
            // 分母判定：无样本时返回 0，避免除零错误。
            if (totalCount <= 0) {
                return 0D;
            }
            return (double) failureCount / (double) totalCount;
        }

        /**
         * 低分率。
         */
        private double lowScoreRate() {
            // 分母判定：无样本时返回 0，避免除零错误。
            if (totalCount <= 0) {
                return 0D;
            }
            return (double) lowScoreCount / (double) totalCount;
        }

        /**
         * 超时率。
         */
        private double timeoutRate() {
            // 分母判定：无样本时返回 0，避免除零错误。
            if (totalCount <= 0) {
                return 0D;
            }
            return (double) timeoutCount / (double) totalCount;
        }
    }
}
