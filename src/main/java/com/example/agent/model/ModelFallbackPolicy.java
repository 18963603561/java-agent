package com.example.agent.model;

import com.example.agent.auth.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 模型降级策略，根据预算与策略判断是否降级。
 */
@Component
public class ModelFallbackPolicy {

    /**
     * 模型配置集合。
     */
    private final ModelConfigProperties modelConfigProperties;

    /**
     * 构造回退策略。
     *
     * @param modelConfigProperties 模型配置集合
     */
    public ModelFallbackPolicy(ModelConfigProperties modelConfigProperties) {
        this.modelConfigProperties = modelConfigProperties;
    }

    /**
     * 评估是否需要模型降级。
     *
     * @param tenantContext 租户上下文
     * @param taskId 任务标识
     * @param currentModel 当前模型
     * @param reason 降级原因
     * @return 决策结果，若无需降级则返回 null
     */
    public ModelFallbackDecision evaluate(TenantContext tenantContext, String taskId,
                                          String currentModel, String reason) {
        if (!modelConfigProperties.isFallbackEnabled()) {
            return null;
        }
        String fallbackModel = modelConfigProperties.getFallbackModelId();
        if (fallbackModel == null || fallbackModel.equals(currentModel)) {
            return null;
        }
        ModelFallbackDecision decision = new ModelFallbackDecision();
        decision.setDecisionId(UUID.randomUUID().toString());
        decision.setTenantId(tenantContext.getTenantId());
        decision.setTaskId(taskId);
        decision.setFromModel(currentModel);
        decision.setToModel(fallbackModel);
        decision.setReason(reason);
        decision.setDecidedAt(Instant.now());
        return decision;
    }
}
