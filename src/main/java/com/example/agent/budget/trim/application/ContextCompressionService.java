package com.example.agent.budget.trim.application;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.budget.trim.model.CompressionSummaryApplyResult;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionRequest;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.capabilities.context.compression.contract.CompressionTrimReportView;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.application.CompressionExecutionRouter;
import com.example.agent.capabilities.context.compression.application.CompressionExecutionOrchestrationService;
import com.example.agent.capabilities.context.compression.application.CompressionModelMapper;
import com.example.agent.capabilities.context.compression.application.port.ContextCompressionOrchestrationPort;
import com.example.agent.capabilities.context.compression.application.port.CompressionTelemetryPort;
import com.example.agent.capabilities.context.compression.experiment.application.CompressionDualTrackOrchestrator;
import com.example.agent.capabilities.context.compression.domain.policy.CompressionTriggerPolicy;
import com.example.agent.capabilities.context.compression.domain.policy.HistoryWindowPolicy;
import com.example.agent.capabilities.context.compression.observability.CompressionMetricKeys;
import com.example.agent.capabilities.context.compression.observability.CompressionMetricTags;
import com.example.agent.capabilities.context.compression.observability.CompressionObservation;
import com.example.agent.capabilities.context.compression.observability.CompressionObservationFactory;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 上下文压缩服务，负责压缩流程编排与结果汇总。
 */
@Service
public class ContextCompressionService implements ContextCompressionOrchestrationPort {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionService.class);

    private final ContextTokenEstimator contextTokenEstimator;
    private final CompressionTelemetryPort telemetryPort;
    private final ContextCompressionProperties properties;
    private final CompressionTriggerPolicy triggerPolicy;
    private final CompressionCooldownService cooldownService;
    private final CompressionExecutionRouter executionRouter;
    private final CompressionDualTrackOrchestrator dualTrackOrchestrator;
    private final CompressionModelMapper compressionModelMapper;
    private final HistoryWindowPolicy historyWindowPolicy;
    private final CompressionSummaryApplier summaryApplier;
    private final CompressionObservationFactory observationFactory;
    private final CompressionExecutionOrchestrationService executionOrchestrationService;

    @Autowired
    public ContextCompressionService(TokenEstimator tokenEstimator,
                                     CompressionTelemetryPort telemetryPort,
                                     ContextCompressionProperties properties,
                                     CompressionTriggerPolicy triggerPolicy,
                                     CompressionCooldownService cooldownService,
                                     CompressionExecutionRouter executionRouter,
                                     CompressionDualTrackOrchestrator dualTrackOrchestrator,
                                     CompressionModelMapper compressionModelMapper,
                                     HistoryWindowPolicy historyWindowPolicy,
                                     CompressionSummaryApplier summaryApplier,
                                     CompressionObservationFactory observationFactory) {
        this(tokenEstimator,
                telemetryPort,
                properties,
                triggerPolicy,
                cooldownService,
                executionRouter,
                dualTrackOrchestrator,
                compressionModelMapper,
                historyWindowPolicy,
                summaryApplier,
                observationFactory,
                null);
    }

    public ContextCompressionService(TokenEstimator tokenEstimator,
                                     CompressionTelemetryPort telemetryPort,
                                     ContextCompressionProperties properties,
                                     CompressionTriggerPolicy triggerPolicy,
                                     CompressionCooldownService cooldownService,
                                     CompressionExecutionRouter executionRouter,
                                     CompressionDualTrackOrchestrator dualTrackOrchestrator,
                                     CompressionModelMapper compressionModelMapper,
                                     HistoryWindowPolicy historyWindowPolicy,
                                     CompressionSummaryApplier summaryApplier,
                                     CompressionObservationFactory observationFactory,
                                     CompressionExecutionOrchestrationService executionOrchestrationService) {
        this.contextTokenEstimator = new ContextTokenEstimator(tokenEstimator);
        this.telemetryPort = telemetryPort;
        this.properties = properties;
        this.triggerPolicy = triggerPolicy;
        this.cooldownService = cooldownService;
        this.executionRouter = executionRouter;
        this.dualTrackOrchestrator = dualTrackOrchestrator;
        this.compressionModelMapper = compressionModelMapper;
        this.historyWindowPolicy = historyWindowPolicy;
        this.summaryApplier = summaryApplier;
        this.observationFactory = observationFactory;
        // 依赖判定：外部未注入执行编排服务时按默认依赖构建能力域编排实现。
        if (executionOrchestrationService == null) {
            this.executionOrchestrationService = new CompressionExecutionOrchestrationService(
                    properties,
                    telemetryPort,
                    executionRouter,
                    dualTrackOrchestrator,
                    compressionModelMapper,
                    historyWindowPolicy,
                    observationFactory);
        } else {
            this.executionOrchestrationService = executionOrchestrationService;
        }
    }

    /**
     * 在预算超限时触发压缩并回填摘要。
     *
     * @param request 压缩请求
     * @return 压缩结果
     */
    public ContextCompressionResult compressIfNeeded(ContextCompressionRequest request) {
        ContextCompressionResult result = new ContextCompressionResult();
        // 入参守卫：请求或快照为空时终止压缩，避免空对象触发后续链路异常。
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
        // 预算判定：预算分配未启用时不执行压缩，保持预算禁用语义一致。
        if (!allocation.isAllocationEnabled()) {
            return result;
        }

        TokenView tokenView = resolveTokenView(request);
        result.setBeforeTokens(tokenView.beforeTokens());
        result.setAfterTrimTokens(tokenView.afterTrimTokens());
        String workflowId = request.getWorkflowId();
        String sessionId = request.getSessionId();

        String reason = triggerPolicy.resolveTriggerReason(allocation,
                tokenView.sectionTokens(),
                tokenView.afterTrimTokens());
        // 触发判定：未命中触发原因时直接返回并记录跳过观测。
        if (!StringUtils.hasText(reason)) {
            // 观测记录：压缩未触发时记录阶段观测，便于区分跳过与失败。
            recordObservationFromContext("trigger_skipped", workflowId, sessionId, result);
            return result;
        }
        String cooldownKey = resolveCooldownKey(workflowId, sessionId);
        // 冷却判定：命中冷却窗口则跳过本次压缩并打点。
        if (StringUtils.hasText(cooldownKey) && cooldownService.isInCooldown(cooldownKey)) {
            telemetryPort.incrementWithTags(CompressionMetricKeys.CONTEXT_COMPRESSION_SKIPPED_TOTAL,
                    CompressionMetricTags.COOLDOWN,
                    "true");
            result.setSkippedCooldown(true);
            // 观测记录：冷却跳过属于可恢复路径，需单独打阶段标记。
            recordObservationFromContext("cooldown_skipped", workflowId, sessionId, result);
            return result;
        }

        // 会话判定：缺少会话标识时跳过压缩，避免写回与冷却键语义不完整。
        if (!StringUtils.hasText(sessionId)) {
            log.warn("上下文压缩跳过，缺少会话标识, tenantId={}, workflowId={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId);
            // 观测记录：关键上下文缺失导致跳过，供上游统计输入质量。
            recordObservationFromContext("session_missing", workflowId, sessionId, result);
            return result;
        }

        // 编排调用：将窗口整形与压缩执行委派到压缩能力域服务统一处理。
        com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult executionResult =
                executionOrchestrationService.execute(request, result);
        // 执行判定：执行结果缺失或失败时直接返回当前结果，不进入摘要回填。
        if (executionResult == null || !executionResult.isSuccess()) {
            return result;
        }

        // 冷却写入：命中有效冷却键时记录本次压缩时间戳。
        if (StringUtils.hasText(cooldownKey)) {
            cooldownService.markCompressed(cooldownKey);
        }

        // 摘要回填：将压缩结果写回快照工作记忆与长期记忆引用。
        CompressionSummaryApplyResult applyResult = summaryApplier.apply(snapshot, executionResult.getCompressed());
        result.setSummaryVersion(applyResult.getSummaryVersion());

        Map<ContextSection, Integer> sectionAfterCompress = contextTokenEstimator.estimateSectionTokens(snapshot);
        int afterCompressTokens = contextTokenEstimator.sumTokens(sectionAfterCompress);
        result.setAfterCompressTokens(afterCompressTokens);
        // 读取目标比例：沿用执行阶段回填的目标阈值用于达标判定。
        Double targetRatio = executionResult.getTargetRatio();
        // 预算占比计算：仅在总预算有效时计算实际压缩占比并评估是否达标。
        if (allocation.getTotalTokens() != null && allocation.getTotalTokens() > 0) {
            double actualRatio = (double) afterCompressTokens / allocation.getTotalTokens();
            executionResult.setActualRatio(actualRatio);
            // 目标判定：配置了目标比例时标记压缩后是否达到目标。
            if (targetRatio != null && targetRatio > 0D) {
                executionResult.setTargetMet(actualRatio <= targetRatio);
            }
        }

        boolean stillOverBudget = triggerPolicy.isOverBudget(allocation, sectionAfterCompress, afterCompressTokens);
        // 超预算判定：压缩后仍超预算时告警并记录指标。
        if (stillOverBudget) {
            telemetryPort.increment(CompressionMetricKeys.CONTEXT_COMPRESSION_STILL_OVER_BUDGET_TOTAL);
            log.warn("上下文压缩后仍超预算, tenantId={}, workflowId={}, afterCompressTokens={}, totalBudgetTokens={}",
                    resolveTenantId(request.getTenantContext(), snapshot), workflowId, afterCompressTokens,
                    allocation.getTotalTokens());
        }

        result.setTriggered(true);
        result.setTriggerReason(reason);
        result.setStillOverBudget(stillOverBudget);
        telemetryPort.incrementWithTags(CompressionMetricKeys.CONTEXT_COMPRESSION_SUMMARY_INJECTION_TOTAL,
                CompressionMetricTags.INJECTED, String.valueOf(result.isSummaryInjected()),
                CompressionMetricTags.REASON, sanitizeTag(result.getSummaryInjectReason()));
        recordTriggerMetrics(reason);
        telemetryPort.incrementWithTags(CompressionMetricKeys.CONTEXT_COMPRESSION_SUCCESS_TOTAL,
                CompressionMetricTags.SOURCE, sanitizeTag(executionResult.getSource()));
        // 观测记录：压缩完成阶段观测用于端到端成功路径统计。
        recordObservationFromContext("completed", workflowId, sessionId, result);

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
     * 记录基于压缩结果的观测事件。
     */
    private void recordObservationFromContext(String stage,
                                              String workflowId,
                                              String sessionId,
                                              ContextCompressionResult result) {
        // 依赖守卫：观测工厂缺失时跳过上报，避免影响主流程。
        if (observationFactory == null) {
            return;
        }
        // 构建观测：统一封装阶段、原因和状态字段。
        CompressionObservation observation = observationFactory.fromContextResult(stage, workflowId, sessionId, result);
        // 上报观测：由遥测端口写入统一指标通道。
        telemetryPort.recordObservation(observation);
    }

    /**
     * 解析压缩链路令牌视图，优先使用裁剪报告，否则回退统一估算器。
     */
    private TokenView resolveTokenView(ContextCompressionRequest request) {
        // 视图转换：仅当请求携带 budget 侧裁剪报告实现时才下沉为具体报告对象。
        CompressionTrimReportView trimReportView = request.getTrimReport();
        ContextTrimReport trimReport = trimReportView instanceof ContextTrimReport report ? report : null;
        Map<ContextSection, Integer> sectionTokens = trimReport != null ? trimReport.getSectionTokensAfter() : null;
        Integer afterTrimTokens = trimReport != null ? trimReport.getTotalAfterTokens() : null;
        Integer beforeTokens = trimReport != null ? trimReport.getTotalBeforeTokens() : null;
        // 回退估算：裁剪报告缺失时调用统一估算器补全分段令牌视图。
        if (sectionTokens == null || afterTrimTokens == null) {
            sectionTokens = contextTokenEstimator.estimateSectionTokens(request.getSnapshot());
            afterTrimTokens = contextTokenEstimator.sumTokens(sectionTokens);
        }
        // 结果返回：返回压缩链路统一令牌视图供后续触发判定使用。
        return new TokenView(sectionTokens, beforeTokens, afterTrimTokens);
    }

    /**
     * 解析冷却键，优先工作流，其次会话。
     */
    private String resolveCooldownKey(String workflowId, String sessionId) {
        // 键优先级：工作流标识存在时优先使用工作流键。
        if (StringUtils.hasText(workflowId)) {
            return workflowId;
        }
        // 回退策略：工作流缺失时回退使用会话标识作为冷却键。
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }
        return null;
    }

    /**
     * 记录触发原因指标。
     */
    private void recordTriggerMetrics(String reason) {
        // 空值判定：触发原因为空时不输出触发细分指标。
        if (!StringUtils.hasText(reason)) {
            return;
        }
        // 触发分类：命中总预算超限原因时记录总预算触发指标。
        if (reason.contains("OVER_TOTAL")) {
            telemetryPort.incrementWithTags(CompressionMetricKeys.CONTEXT_COMPRESSION_TRIGGER_TOTAL,
                    CompressionMetricTags.REASON,
                    "OVER_TOTAL");
        }
        // 触发分类：命中分段预算超限原因时记录分段触发指标。
        if (reason.contains("OVER_SECTION")) {
            telemetryPort.incrementWithTags(CompressionMetricKeys.CONTEXT_COMPRESSION_TRIGGER_TOTAL,
                    CompressionMetricTags.REASON,
                    "OVER_SECTION");
        }
        // 触发分类：命中比例阈值原因时记录比例触发指标。
        if (reason.contains("OVER_RATIO")) {
            telemetryPort.incrementWithTags(CompressionMetricKeys.CONTEXT_COMPRESSION_TRIGGER_TOTAL,
                    CompressionMetricTags.REASON,
                    "OVER_RATIO");
        }
    }


    /**
     * 解析租户标识，用于日志上下文。
     */
    private String resolveTenantId(TenantContext tenantContext, ContextSnapshot snapshot) {
        // 优先策略：租户上下文存在有效租户标识时优先返回该标识。
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTenantId())) {
            return tenantContext.getTenantId();
        }
        // 回退策略：租户上下文缺失时回退读取快照运行元信息。
        if (snapshot != null && snapshot.getRuntimeMeta() != null) {
            return snapshot.getRuntimeMeta().getTenantId();
        }
        return null;
    }

    /**
     * 清洗标签值。
     */
    private String sanitizeTag(String value) {
        // 标签兜底：空白标签统一映射为 unknown，避免指标维度空值污染。
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
