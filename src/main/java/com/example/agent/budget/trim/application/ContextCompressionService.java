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
    private final CompressionTelemetryPort telemetryPort;
    private final ContextCompressionProperties properties;
    private final CompressionTriggerPolicy triggerPolicy;
    private final CompressionCooldownService cooldownService;
    private final CompressionExecutionRouter executionRouter;
    private final CompressionModelMapper compressionModelMapper;
    private final CompressionSummaryApplier summaryApplier;

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
        ContextCompressionResult result = new ContextCompressionResult();
        if (request == null || request.getSnapshot() == null) {
            return result;
        }

        ContextSnapshot snapshot = request.getSnapshot();
        result.setSnapshot(snapshot);

        // 配置判定：关闭压缩开关时直接返回，避免进入后续重路径。
        if (properties != null && properties.getTrigger() != null && !properties.getTrigger().isEnabled()) {
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
        // 冷却判定：命中冷却窗口则跳过本次压缩并打点。
        if (StringUtils.hasText(cooldownKey) && cooldownService.isInCooldown(cooldownKey)) {
            telemetryPort.incrementWithTags("context_compression_skipped_total", "cooldown", "true");
            result.setSkippedCooldown(true);
            return result;
        }

        if (!StringUtils.hasText(sessionId)) {
            log.warn("上下文压缩跳过，缺少会话标识, tenantId={}, workflowId={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId);
            return result;
        }

        // 模型映射：将请求转换为压缩命令，隔离调用方与执行域对象。
        CompressionCommand command = compressionModelMapper.toCommand(request, request.getTenantContext());
        // 执行路由：根据模式解析路由到 rule/llm 执行器。
        CompressionOutcome outcome = compressionModelMapper.toOutcome(executionRouter.execute(command));
        if (outcome == null || outcome.getExecutionResult() == null) {
            return result;
        }
        result.setExecutionSource(outcome.getExecutionResult().getSource());
        result.setFailureReason(outcome.getExecutionResult().getFailureReason());
        result.setFallbackApplied(outcome.getExecutionResult().isFallbackApplied());
        result.setDurationMs(outcome.getExecutionResult().getDurationMs());
        if (!outcome.getExecutionResult().isSuccess()) {
            // 失败打点：记录压缩失败原因与来源，支撑故障分析与降级评估。
            telemetryPort.incrementWithTags("context_compression_failed_total",
                    "source", sanitizeTag(outcome.getExecutionResult().getSource()),
                    "reason", sanitizeTag(outcome.getExecutionResult().getFailureReason()));
            return result;
        }

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
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId, afterCompressTokens,
                    allocation.getTotalTokens());
        }

        result.setTriggered(true);
        result.setTriggerReason(reason);
        result.setStillOverBudget(stillOverBudget);
        recordTriggerMetrics(reason);
        telemetryPort.incrementWithTags("context_compression_success_total",
                "source", sanitizeTag(outcome.getExecutionResult().getSource()));

        log.info("上下文压缩完成, tenantId={}, workflowId={}, beforeTokens={}, afterTrimTokens={}, afterCompressTokens={}, "
                        + "triggerReason={}, compressionDurationMs={}, summaryVersion={}",
                resolveTenantId(request.getTenantContext(), snapshot),
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
}
