package com.example.agent.model;

import com.example.agent.context.ContextSnapshot;

/**
 * 提示词渲染最小上下文，只承载白名单决策字段。
 */
public class PromptRenderContext {

    /**
     * 风险等级，可为空。
     */
    private String riskLevel;

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    /**
     * 从快照映射最小渲染上下文，仅保留白名单字段。
     *
     * @param snapshot 上下文快照
     * @return 渲染上下文
     */
    public static PromptRenderContext fromSnapshot(ContextSnapshot snapshot) {
        PromptRenderContext context = new PromptRenderContext();
        if (snapshot == null || snapshot.getRoleBoundary() == null) {
            return context;
        }
        String riskLevel = snapshot.getRoleBoundary().getRiskLevel();
        if (riskLevel != null && !riskLevel.isBlank()) {
            context.setRiskLevel(riskLevel);
        }
        return context;
    }
}
