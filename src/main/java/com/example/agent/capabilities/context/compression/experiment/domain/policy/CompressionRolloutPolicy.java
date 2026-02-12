package com.example.agent.capabilities.context.compression.experiment.domain.policy;

import com.example.agent.capabilities.context.compression.experiment.domain.model.RolloutDecision;

/**
 * 压缩双轨灰度策略端口。
 *
 * <p>用途：按租户、场景、会话稳定分桶等维度决定是否开启双轨实验。</p>
 */
public interface CompressionRolloutPolicy {

    /**
     * 解析双轨灰度决策。
     *
     * @param tenantId 租户标识
     * @param scene 场景标识
     * @param sessionId 会话标识
     * @param workflowId 工作流标识
     * @return 灰度决策
     */
    RolloutDecision decide(String tenantId, String scene, String sessionId, String workflowId);
}
