package com.example.agent.capabilities.llm.tooling;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;

/**
 * 模型工具治理上下文。
 *
 * <p>用途：统一承载工具注入阶段需要的租户、技能、显式工具选择与禁用标识。
 * <p>输入：由 {@link ToolingContextMapper} 从任务请求与步骤输入映射得到。
 * <p>输出：供 {@code ModelToolResolver} 直接消费，避免散落的 Map 键访问。
 * <p>边界：字段允许为空，消费方按自身策略处理降级或校验。
 */
public class ModelToolingContext {

    /**
     * 租户标识，用于日志与按需加载工具结构时透传上下文。
     */
    private final String tenantId;

    /**
     * 技能名称，用于定位技能约束策略。
     */
    private final String skillName;

    /**
     * 显式工具选择策略，来源于调用方显式配置。
     */
    private final ModelToolChoice explicitToolChoice;

    /**
     * 是否禁用工具。
     */
    private final boolean disableTools;

    public ModelToolingContext(String tenantId,
                               String skillName,
                               ModelToolChoice explicitToolChoice,
                               boolean disableTools) {
        this.tenantId = tenantId;
        this.skillName = skillName;
        this.explicitToolChoice = explicitToolChoice;
        this.disableTools = disableTools;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSkillName() {
        return skillName;
    }

    public ModelToolChoice getExplicitToolChoice() {
        return explicitToolChoice;
    }

    public boolean isDisableTools() {
        return disableTools;
    }
}

