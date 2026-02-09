package com.example.agent.budget.trim.application;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.budget.trim.model.CompressionExecutionResult;
import com.example.agent.budget.trim.model.CompressionSummaryApplyResult;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.budget.trim.model.ContextCompressionResult;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.budget.trim.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.Map;
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
    private final MetricsPublisher metricsPublisher;
    private final ContextCompressionProperties properties;
    private final CompressionTriggerPolicy triggerPolicy;
    private final CompressionCooldownService cooldownService;
    private final CompressionExecutionService executionService;
    private final CompressionSummaryApplier summaryApplier;

    public ContextCompressionService(TokenEstimator tokenEstimator,
                                     MetricsPublisher metricsPublisher,
                                     ContextCompressionProperties properties,
                                     CompressionTriggerPolicy triggerPolicy,
                                     CompressionCooldownService cooldownService,
                                     CompressionExecutionService executionService,
                                     CompressionSummaryApplier summaryApplier) {
        this.contextTokenEstimator = new ContextTokenEstimator(tokenEstimator);
        this.metricsPublisher = metricsPublisher;
        this.properties = properties;
        this.triggerPolicy = triggerPolicy;
        this.cooldownService = cooldownService;
        this.executionService = executionService;
        this.summaryApplier = summaryApplier;
    }

    /**
     * 在预算超限时触发压缩并回填摘要。
     *
     * @param request 压缩请求
     * @return 压缩结果
     */
    public ContextCompressionResult compressIfNeeded(ContextCompressionRequest request) {
        ContextCompressionResult result = new ContextCompressionResult();
        if (request == null || request.getSnapshot() == null) {
            return result;
        }

        ContextSnapshot snapshot = request.getSnapshot();
        result.setSnapshot(snapshot);

        if (properties != null && !properties.isEnabled()) {
            return result;
        }

        ContextBudgetAllocation allocation = request.getAllocation();
        if (!allocation.isAllocationEnabled()) {
            return result;
        }

        TokenView tokenView = resolveTokenView(request);
        result.setBeforeTokens(tokenView.beforeTokens());
        result.setAfterTrimTokens(tokenView.afterTrimTokens());

        String reason = triggerPolicy.resolveTriggerReason(allocation,
                tokenView.sectionTokens(),
                tokenView.afterTrimTokens());
        if (!StringUtils.hasText(reason)) {
            return result;
        }

        String workflowId = request.getWorkflowId();
        String sessionId = request.getSessionId();
        String cooldownKey = resolveCooldownKey(workflowId, sessionId);
        if (StringUtils.hasText(cooldownKey) && cooldownService.isInCooldown(cooldownKey)) {
            metricsPublisher.incrementWithTags("context_compression_skipped_total", "cooldown", "true");
            result.setSkippedCooldown(true);
            return result;
        }

        if (!StringUtils.hasText(sessionId)) {
            log.warn("上下文压缩跳过，缺少会话标识, tenantId={}, workflowId={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId);
            return result;
        }

        CompressionExecutionResult executionResult = executionService.execute(request, request.getTenantContext());
        result.setDurationMs(executionResult.getDurationMs());
        if (!executionResult.isSuccess()) {
            return result;
        }

        if (StringUtils.hasText(cooldownKey)) {
            cooldownService.markCompressed(cooldownKey);
        }

        CompressionSummaryApplyResult applyResult = summaryApplier.apply(snapshot, executionResult.getCompressed());
        result.setSummaryVersion(applyResult.getSummaryVersion());

        Map<ContextSection, Integer> sectionAfterCompress = contextTokenEstimator.estimateSectionTokens(snapshot);
        int afterCompressTokens = contextTokenEstimator.sumTokens(sectionAfterCompress);
        result.setAfterCompressTokens(afterCompressTokens);

        boolean stillOverBudget = triggerPolicy.isOverBudget(allocation, sectionAfterCompress, afterCompressTokens);
        if (stillOverBudget) {
            metricsPublisher.increment("context_compression_still_over_budget_total");
            log.warn("上下文压缩后仍超预算, tenantId={}, workflowId={}, afterCompressTokens={}, totalBudgetTokens={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId, afterCompressTokens,
                    allocation.getTotalTokens());
        }

        result.setTriggered(true);
        result.setTriggerReason(reason);
        result.setStillOverBudget(stillOverBudget);
        recordTriggerMetrics(reason);

        log.info("上下文压缩完成, tenantId={}, workflowId={}, beforeTokens={}, afterTrimTokens={}, afterCompressTokens={}, "
                        + "triggerReason={}, compressionDurationMs={}, summaryVersion={}",
                resolveTenantId(request.getTenantContext(), snapshot),
                workflowId,
                tokenView.beforeTokens(),
                tokenView.afterTrimTokens(),
                afterCompressTokens,
                reason,
                executionResult.getDurationMs(),
                result.getSummaryVersion());
        return result;
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
            metricsPublisher.incrementWithTags("context_compression_trigger_total", "reason", "OVER_TOTAL");
        }
        if (reason.contains("OVER_SECTION")) {
            metricsPublisher.incrementWithTags("context_compression_trigger_total", "reason", "OVER_SECTION");
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
     * 压缩前后令牌视图。
     */
    private record TokenView(Map<ContextSection, Integer> sectionTokens,
                             Integer beforeTokens,
                             Integer afterTrimTokens) {
    }
}
