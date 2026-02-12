package com.example.agent.capabilities.context.compression.application;

import com.example.agent.capabilities.context.compression.application.port.CompressionTelemetryPort;
import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionRequest;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.context.compression.domain.model.CompressionOutcome;
import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeCommand;
import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeResult;
import com.example.agent.capabilities.context.compression.domain.policy.HistoryWindowPolicy;
import com.example.agent.capabilities.context.compression.experiment.application.CompressionDualTrackOrchestrator;
import com.example.agent.capabilities.context.compression.experiment.domain.model.DualTrackExecutionResult;
import com.example.agent.capabilities.context.compression.observability.CompressionMetricKeys;
import com.example.agent.capabilities.context.compression.observability.CompressionMetricTags;
import com.example.agent.capabilities.context.compression.observability.CompressionObservation;
import com.example.agent.capabilities.context.compression.observability.CompressionObservationFactory;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.security.auth.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 压缩执行编排服务，负责窗口整形与压缩执行阶段编排。
 */
@Service
public class CompressionExecutionOrchestrationService {

    /**
     * 压缩配置，用于读取窗口参数与比例阈值。
     */
    private final ContextCompressionProperties properties;
    /**
     * 压缩遥测端口，用于输出指标与观测事件。
     */
    private final CompressionTelemetryPort telemetryPort;
    /**
     * 执行路由器，用于分发 rule/llm/hybrid 压缩路径。
     */
    private final CompressionExecutionRouter executionRouter;
    /**
     * 双轨编排器，用于灰度对比与主影子轨执行。
     */
    private final CompressionDualTrackOrchestrator dualTrackOrchestrator;
    /**
     * 模型映射器，用于请求与执行结果转换。
     */
    private final CompressionModelMapper compressionModelMapper;
    /**
     * 历史窗口策略，用于执行三段式窗口整形。
     */
    private final HistoryWindowPolicy historyWindowPolicy;
    /**
     * 观测工厂，用于构造统一压缩观测对象。
     */
    private final CompressionObservationFactory observationFactory;

    public CompressionExecutionOrchestrationService(ContextCompressionProperties properties,
                                                    CompressionTelemetryPort telemetryPort,
                                                    CompressionExecutionRouter executionRouter,
                                                    CompressionDualTrackOrchestrator dualTrackOrchestrator,
                                                    CompressionModelMapper compressionModelMapper,
                                                    HistoryWindowPolicy historyWindowPolicy,
                                                    CompressionObservationFactory observationFactory) {
        this.properties = properties;
        this.telemetryPort = telemetryPort;
        this.executionRouter = executionRouter;
        this.dualTrackOrchestrator = dualTrackOrchestrator;
        this.compressionModelMapper = compressionModelMapper;
        this.historyWindowPolicy = historyWindowPolicy;
        this.observationFactory = observationFactory;
    }

    /**
     * 执行窗口整形与压缩执行阶段。
     *
     * @param request 压缩请求
     * @param result 压缩结果承载对象
     * @return 执行结果，缺失时返回 null
     */
    public CompressionExecutionResult execute(ContextCompressionRequest request, ContextCompressionResult result) {
        // 入参守卫：请求或结果容器缺失时终止执行阶段，避免空指针扩散。
        if (request == null || result == null) {
            return null;
        }
        ContextSnapshot snapshot = request.getSnapshot();
        String workflowId = request.getWorkflowId();
        String sessionId = request.getSessionId();

        // 历史整形：先按三段式窗口策略整形历史引用，统一压缩输入窗口。
        HistoryWindowShapeResult windowShapeResult = shapeHistoryWindow(snapshot);
        // 快照回写：窗口整形返回新快照时同步回写请求与结果对象。
        if (windowShapeResult.getSnapshot() != null) {
            snapshot = windowShapeResult.getSnapshot();
            request.setSnapshot(snapshot);
            result.setSnapshot(snapshot);
        }
        // 结果回填：写入窗口整形元数据供事件流与审计使用。
        result.setWindowShaped(windowShapeResult.isWindowShaped());
        result.setShapeReason(windowShapeResult.getShapeReason());
        result.setPrimersRetained(windowShapeResult.getPrimersRetained());
        result.setRecentsRetained(windowShapeResult.getRecentsRetained());
        result.setMiddleWindowSize(windowShapeResult.getMiddleWindowSize());
        // 指标打点：记录窗口整形是否命中与命中原因。
        telemetryPort.incrementWithTags(CompressionMetricKeys.CONTEXT_COMPRESSION_SHAPE_TOTAL,
                CompressionMetricTags.SHAPED, String.valueOf(windowShapeResult.isWindowShaped()),
                CompressionMetricTags.REASON, sanitizeTag(windowShapeResult.getShapeReason()));
        // 观测记录：窗口整形阶段观测用于审计三段策略命中情况。
        recordObservationFromContext("shape", workflowId, sessionId, result);

        // 模型映射：将请求转换为压缩命令，并透传窗口整形结果。
        CompressionCommand command = compressionModelMapper.toCommand(request, request.getTenantContext(), windowShapeResult);
        // 执行编排：优先走双轨编排器，缺失时回退单轨路由。
        DualTrackExecutionResult dualTrackResult = executeCompression(command, request, snapshot);
        // 结果映射：统一将主轨执行结果映射为压缩领域结果。
        CompressionOutcome outcome = compressionModelMapper.toOutcome(
                dualTrackResult != null ? dualTrackResult.getPrimaryResult() : executionRouter.execute(command));
        // 缺失判定：执行结果缺失时直接返回空结果并打执行缺失观测。
        if (outcome == null || outcome.getExecutionResult() == null) {
            recordObservationFromContext("execute_missing", workflowId, sessionId, result);
            return null;
        }

        CompressionExecutionResult executionResult = outcome.getExecutionResult();
        // 双轨结果回填：写入灰度与对比元数据，供事件与观测统一消费。
        applyDualTrackResult(result, dualTrackResult);
        // 字段回填：将执行关键字段写回压缩结果对象。
        result.setExecutionSource(executionResult.getSource());
        result.setFailureReason(executionResult.getFailureReason());
        result.setFallbackApplied(executionResult.isFallbackApplied());
        result.setDurationMs(executionResult.getDurationMs());
        // 观测记录：执行阶段观测反映路由来源、失败原因与降级情况。
        recordObservationFromExecution("execute", workflowId, sessionId, executionResult);

        Double triggerRatio = properties != null && properties.getTrigger() != null
                ? properties.getTrigger().getCompressionTriggerRatio()
                : null;
        Double targetRatio = properties != null && properties.getTrigger() != null
                ? properties.getTrigger().getCompressionTargetRatio()
                : null;
        // 比例回填：为执行结果补齐触发阈值与目标阈值。
        executionResult.setTriggerRatio(triggerRatio);
        executionResult.setTargetRatio(targetRatio);
        // 失败判定：执行失败时记录失败指标并输出失败阶段观测。
        if (!executionResult.isSuccess()) {
            // 失败打点：记录压缩失败原因与来源，支撑故障分析与降级评估。
            telemetryPort.incrementWithTags(CompressionMetricKeys.CONTEXT_COMPRESSION_FAILED_TOTAL,
                    CompressionMetricTags.SOURCE, sanitizeTag(executionResult.getSource()),
                    CompressionMetricTags.REASON, sanitizeTag(executionResult.getFailureReason()));
            // 观测记录：执行失败阶段观测用于区分失败分布与来源。
            recordObservationFromContext("execute_failed", workflowId, sessionId, result);
        }
        // 结果返回：返回执行阶段产物供预算触发层继续处理。
        return executionResult;
    }

    /**
     * 执行压缩编排。
     */
    private DualTrackExecutionResult executeCompression(CompressionCommand command,
                                                        ContextCompressionRequest request,
                                                        ContextSnapshot snapshot) {
        // 依赖守卫：双轨编排器缺失时返回空结果并沿用单轨执行。
        if (dualTrackOrchestrator == null) {
            return null;
        }
        String tenantId = resolveTenantId(request.getTenantContext(), snapshot);
        String scene = properties != null && properties.getLlm() != null
                ? properties.getLlm().getScene()
                : null;
        // 编排调用：由双轨编排器决定主轨生效与影子轨对比。
        return dualTrackOrchestrator.execute(command,
                tenantId,
                scene,
                request.getSessionId(),
                request.getWorkflowId());
    }

    /**
     * 回填双轨执行结果。
     */
    private void applyDualTrackResult(ContextCompressionResult result, DualTrackExecutionResult dualTrackResult) {
        // 结果守卫：双轨结果缺失时保持默认字段。
        if (result == null || dualTrackResult == null) {
            return;
        }
        result.setDualTrackEnabled(dualTrackResult.isDualTrackEnabled());
        // 决策回填：存在发布决策时补齐版本与命中原因。
        if (dualTrackResult.getRolloutDecision() != null) {
            result.setRolloutVersion(dualTrackResult.getRolloutDecision().getRolloutVersion());
            result.setRolloutReason(dualTrackResult.getRolloutDecision().getReason());
        }
        // 主轨回填：主轨结果存在时写入主轨来源。
        if (dualTrackResult.getPrimaryResult() != null) {
            result.setPrimarySource(dualTrackResult.getPrimaryResult().getSource());
        }
        // 影子轨回填：影子轨结果存在时写入影子轨来源。
        if (dualTrackResult.getShadowResult() != null) {
            result.setShadowSource(dualTrackResult.getShadowResult().getSource());
        }
        // 对比回填：存在对比记录时补齐回滚与赢家信息。
        if (dualTrackResult.getComparisonRecord() != null) {
            result.setComparisonRecordId(dualTrackResult.getComparisonRecord().getRecordId());
            result.setRollbackReason(dualTrackResult.getComparisonRecord().getRollbackReason());
            result.setWinnerSource(dualTrackResult.getComparisonRecord().getWinnerSource());
        }
    }

    /**
     * 执行历史窗口整形。
     */
    private HistoryWindowShapeResult shapeHistoryWindow(ContextSnapshot snapshot) {
        HistoryWindowShapeResult defaultResult = new HistoryWindowShapeResult();
        defaultResult.setSnapshot(snapshot);
        defaultResult.setWindowShaped(false);
        defaultResult.setShapeReason("WINDOW_POLICY_MISSING");
        // 依赖判定：窗口策略或快照缺失时直接返回默认整形结果。
        if (historyWindowPolicy == null || snapshot == null) {
            return defaultResult;
        }
        HistoryWindowShapeCommand command = new HistoryWindowShapeCommand();
        // 输入装配：写入待整形快照供窗口策略执行。
        command.setSnapshot(snapshot);
        int primersCount = properties != null && properties.getWindow() != null
                ? properties.getWindow().getPrimersCount()
                : 0;
        int recentsCount = properties != null && properties.getWindow() != null
                ? properties.getWindow().getRecentsCount()
                : 0;
        // 参数写入：透传首段与尾段保留条数到窗口策略。
        command.setPrimersCount(primersCount);
        command.setRecentsCount(recentsCount);
        // 策略调用：执行窗口整形并返回窗口重排结果。
        HistoryWindowShapeResult result = historyWindowPolicy.shape(command);
        // 空值判定：窗口策略未返回结果时回退默认结果。
        if (result == null) {
            return defaultResult;
        }
        // 快照回填：策略未返回新快照时沿用原快照。
        if (result.getSnapshot() == null) {
            result.setSnapshot(snapshot);
        }
        // 原因回填：策略未写入原因时按是否整形补齐默认原因。
        if (!StringUtils.hasText(result.getShapeReason())) {
            result.setShapeReason(result.isWindowShaped() ? "WINDOW_SHAPED" : "WINDOW_SKIPPED");
        }
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
     * 记录基于执行结果的观测事件。
     */
    private void recordObservationFromExecution(String stage,
                                                String workflowId,
                                                String sessionId,
                                                CompressionExecutionResult result) {
        // 依赖守卫：观测工厂缺失时跳过上报，避免影响主流程。
        if (observationFactory == null) {
            return;
        }
        // 构建观测：统一封装执行来源与失败语义。
        CompressionObservation observation = observationFactory.fromExecutionResult(stage, workflowId, sessionId, result);
        // 上报观测：由遥测端口写入统一指标通道。
        telemetryPort.recordObservation(observation);
    }

    /**
     * 解析租户标识，用于日志上下文。
     */
    private String resolveTenantId(TenantContext tenantContext, ContextSnapshot snapshot) {
        // 优先级判定：租户上下文存在租户标识时优先使用该租户。
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTenantId())) {
            return tenantContext.getTenantId();
        }
        // 回退判定：租户上下文缺失时从快照运行元信息回退解析。
        if (snapshot != null && snapshot.getRuntimeMeta() != null) {
            return snapshot.getRuntimeMeta().getTenantId();
        }
        return null;
    }

    /**
     * 清洗标签值。
     */
    private String sanitizeTag(String value) {
        // 空值判定：空白标签统一映射为 unknown，避免指标高基数污染。
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value;
    }
}

