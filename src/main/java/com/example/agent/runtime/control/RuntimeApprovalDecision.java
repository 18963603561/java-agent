package com.example.agent.runtime.control;

/**
 * 运行时审批决策对象。
 *
 * <p>用途：描述审批是否显式声明、是否需要审批以及审批来源。
 * <p>输入：审批解析阶段产出的原始标记。
 * <p>输出：供审批门禁统一判定的结构化对象。
 * <p>边界：当审批未声明时使用 {@link #none()}。
 */
public class RuntimeApprovalDecision {

    /**
     * 是否为显式声明。
     */
    private final boolean explicit;

    /**
     * 是否要求审批。
     */
    private final boolean required;

    /**
     * 审批来源。
     */
    private final String source;

    public RuntimeApprovalDecision(boolean explicit, boolean required, String source) {
        this.explicit = explicit;
        this.required = required;
        this.source = source;
    }

    /**
     * 构建空审批决策。
     */
    public static RuntimeApprovalDecision none() {
        return new RuntimeApprovalDecision(false, false, null);
    }

    public boolean isExplicit() {
        return explicit;
    }

    public boolean isRequired() {
        return required;
    }

    public String getSource() {
        return source;
    }
}
