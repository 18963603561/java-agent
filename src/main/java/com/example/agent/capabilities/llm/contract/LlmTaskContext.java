package com.example.agent.capabilities.llm.contract;

import java.util.Map;

/**
 * LLM 任务上下文。
 *
 * <p>用途：承载 LLM 能力层所需的最小任务上下文，隔离对外部入口 DTO 的直接依赖。
 * <p>输入：由入口层适配器在调用前构造。
 * <p>输出：供提示词装配、工具注入等能力组件统一消费。
 * <p>边界：字段允许为空，消费方按默认策略处理。
 */
public class LlmTaskContext {

    /**
     * 租户标识。
     */
    private final String tenantId;

    /**
     * 技能名称。
     */
    private final String skillName;

    /**
     * 调用上下文。
     */
    private final Map<String, Object> context;

    /**
     * 显式工具选择策略。
     */
    private final ModelToolChoice toolChoice;

    public LlmTaskContext(String tenantId,
                          String skillName,
                          Map<String, Object> context,
                          ModelToolChoice toolChoice) {
        this.tenantId = tenantId;
        this.skillName = skillName;
        this.context = context == null ? Map.of() : Map.copyOf(context);
        this.toolChoice = toolChoice;
    }

    /**
     * 创建空上下文。
     *
     * @return 空上下文
     */
    public static LlmTaskContext empty() {
        return new LlmTaskContext(null, null, Map.of(), null);
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSkillName() {
        return skillName;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public ModelToolChoice getToolChoice() {
        return toolChoice;
    }
}

