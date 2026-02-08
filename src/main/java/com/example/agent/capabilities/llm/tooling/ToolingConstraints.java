package com.example.agent.capabilities.llm.tooling;

/**
 * 工具治理约束。
 *
 * <p>用途：聚合技能约束策略，作为工具解析器内部的单一约束对象。
 * <p>输入：由 {@link ToolingContextMapper} 构造。
 * <p>输出：供工具解析流程按统一约束执行筛选与策略覆盖。
 * <p>边界：策略缺失时返回空策略对象，避免空指针。
 */
public class ToolingConstraints {

    /**
     * 技能工具策略。
     */
    private final SkillToolPolicy skillToolPolicy;

    public ToolingConstraints(SkillToolPolicy skillToolPolicy) {
        this.skillToolPolicy = skillToolPolicy == null ? SkillToolPolicy.empty() : skillToolPolicy;
    }

    public static ToolingConstraints empty() {
        return new ToolingConstraints(SkillToolPolicy.empty());
    }

    public SkillToolPolicy getSkillToolPolicy() {
        return skillToolPolicy;
    }
}
