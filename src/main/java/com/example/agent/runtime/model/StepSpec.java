package com.example.agent.runtime.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 步骤规格定义。
 *
 * <p>用途：统一描述规划阶段生成的单个步骤，包含类型、参数、上下文、依赖关系与策略。
 * <p>输入输出：输入由规划服务填充，输出供运行时执行器消费。
 * <p>边界：参数与上下文字段允许动态结构，仅用于与模型/工具边界交互。
 */
public class StepSpec {

    /**
     * 步骤类型。
     */
    private String stepType;

    /**
     * 步骤参数。
     */
    private Map<String, Object> arguments;

    /**
     * 步骤上下文。
     */
    private Map<String, Object> context;

    /**
     * 依赖步骤。
     */
    private List<String> dependsOn;

    /**
     * 执行策略。
     */
    private StepPolicy policy;

    public StepSpec() {
    }

    public StepSpec(String stepType, Map<String, Object> arguments) {
        this.stepType = stepType;
        this.arguments = arguments;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public StepPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(StepPolicy policy) {
        this.policy = policy;
    }

    /**
     * 获取步骤审批标记。
     *
     * @return 审批标记
     */
    public Boolean getRequiresApproval() {
        return policy != null ? policy.getRequiresApproval() : null;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        if (policy == null) {
            policy = new StepPolicy();
        }
        policy.setRequiresApproval(requiresApproval);
    }

    public String getApprovalSource() {
        return policy != null ? policy.getApprovalSource() : null;
    }

    public void setApprovalSource(String approvalSource) {
        if (policy == null) {
            policy = new StepPolicy();
        }
        policy.setApprovalSource(approvalSource);
    }

    /**
     * 将强类型步骤规格转换为执行输入映射。
     *
     * <p>注意：该方法仅用于运行时执行边界，避免业务代码直接依赖动态结构。
     *
     * @return 合并后的执行输入
     */
    public Map<String, Object> toExecutionInput() {
        Map<String, Object> merged = new HashMap<>();
        if (arguments != null && !arguments.isEmpty()) {
            merged.putAll(arguments);
        }
        if (context != null && !context.isEmpty()) {
            merged.put("context", context);
        }
        if (dependsOn != null && !dependsOn.isEmpty()) {
            merged.put("dependsOn", new ArrayList<>(dependsOn));
        }
        if (policy != null) {
            if (policy.getRequiresApproval() != null) {
                merged.put("requiresApproval", policy.getRequiresApproval());
            }
            if (policy.getApprovalSource() != null) {
                merged.put("approvalSource", policy.getApprovalSource());
            }
        }
        return merged;
    }
}
