package com.example.agent.capabilities.context.compression.observability;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 压缩观测对象工厂。
 *
 * <p>用途：集中构建压缩观测语义对象，避免多处重复拼装观测字段。</p>
 */
@Component
public class CompressionObservationFactory {

    /**
     * 构建压缩阶段观测对象。
     *
     * @param stage 观测阶段
     * @param workflowId 工作流标识
     * @param sessionId 会话标识
     * @param result 压缩结果
     * @return 压缩观测对象
     */
    public CompressionObservation fromContextResult(String stage,
                                                    String workflowId,
                                                    String sessionId,
                                                    ContextCompressionResult result) {
        CompressionObservation observation = new CompressionObservation();
        observation.setStage(normalizeStage(stage));
        observation.setWorkflowId(workflowId);
        observation.setSessionId(sessionId);
        if (result == null) {
            return observation;
        }
        observation.setSource(result.getExecutionSource());
        observation.setTriggerReason(result.getTriggerReason());
        observation.setFailureReason(result.getFailureReason());
        observation.setShapeReason(result.getShapeReason());
        observation.setSummaryInjectReason(result.getSummaryInjectReason());
        observation.setSuccess(result.isTriggered());
        observation.setFallbackApplied(result.isFallbackApplied());
        observation.setWindowShaped(result.isWindowShaped());
        observation.setSummaryInjected(result.isSummaryInjected());
        observation.setDualTrackEnabled(result.isDualTrackEnabled());
        observation.setRolloutVersion(result.getRolloutVersion());
        observation.setPrimarySource(result.getPrimarySource());
        observation.setShadowSource(result.getShadowSource());
        observation.setWinnerSource(result.getWinnerSource());
        observation.setRollbackReason(result.getRollbackReason());
        return observation;
    }

    /**
     * 构建执行结果观测对象。
     *
     * @param stage 观测阶段
     * @param workflowId 工作流标识
     * @param sessionId 会话标识
     * @param result 执行结果
     * @return 压缩观测对象
     */
    public CompressionObservation fromExecutionResult(String stage,
                                                      String workflowId,
                                                      String sessionId,
                                                      CompressionExecutionResult result) {
        CompressionObservation observation = new CompressionObservation();
        observation.setStage(normalizeStage(stage));
        observation.setWorkflowId(workflowId);
        observation.setSessionId(sessionId);
        if (result == null) {
            return observation;
        }
        observation.setSource(result.getSource());
        observation.setFailureReason(result.getFailureReason());
        observation.setSuccess(result.isSuccess());
        observation.setFallbackApplied(result.isFallbackApplied());
        return observation;
    }

    /**
     * 规范化阶段名称。
     */
    private String normalizeStage(String stage) {
        if (!StringUtils.hasText(stage)) {
            return "unknown";
        }
        return stage.trim().toLowerCase();
    }
}

