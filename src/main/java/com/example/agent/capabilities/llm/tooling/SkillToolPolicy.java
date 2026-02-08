package com.example.agent.capabilities.llm.tooling;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import java.util.List;

/**
 * 技能工具策略。
 *
 * <p>用途：承载技能约束中的工具白名单与工具选择策略。
 * <p>输入：由 {@link ToolingContextMapper} 从技能约束映射得到。
 * <p>输出：供工具解析流程按强类型读取策略。
 * <p>边界：允许空策略，调用方应自行决定默认行为。
 */
public class SkillToolPolicy {

    /**
     * 允许的工具列表。
     */
    private final List<String> allowTools;

    /**
     * 技能声明的工具选择策略。
     */
    private final ModelToolChoice toolChoice;

    public SkillToolPolicy(List<String> allowTools, ModelToolChoice toolChoice) {
        this.allowTools = allowTools == null ? List.of() : List.copyOf(allowTools);
        this.toolChoice = toolChoice;
    }

    public static SkillToolPolicy empty() {
        return new SkillToolPolicy(List.of(), null);
    }

    public List<String> getAllowTools() {
        return allowTools;
    }

    public ModelToolChoice getToolChoice() {
        return toolChoice;
    }

    public boolean hasAllowTools() {
        return !allowTools.isEmpty();
    }
}

