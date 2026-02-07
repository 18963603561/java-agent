package com.example.agent.runtime.model.input;

import com.example.agent.runtime.model.StepPolicy;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.RuntimeContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 步骤输入类型化视图。
 *
 * <p>用途：在核心链路中提供稳定的步骤输入读取入口，减少直接依赖动态映射键。
 * <p>输入：步骤定义与运行时上下文。
 * <p>输出：类型化字段访问与可落到执行边界的动态映射。
 */
public class StepInputView {

    /**
     * 步骤参数。
     */
    private final Map<String, Object> arguments;

    /**
     * 步骤上下文。
     */
    private final Map<String, Object> context;

    /**
     * 步骤依赖。
     */
    private final List<String> dependsOn;

    /**
     * 审批输入。
     */
    private final ApprovalInput approvalInput;

    private StepInputView(Map<String, Object> arguments,
                          Map<String, Object> context,
                          List<String> dependsOn,
                          ApprovalInput approvalInput) {
        this.arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        this.context = context == null ? Map.of() : Map.copyOf(context);
        this.dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        this.approvalInput = approvalInput == null ? ApprovalInput.empty() : approvalInput;
    }

    /**
     * 基于步骤与运行时上下文构建输入视图。
     *
     * @param step 步骤定义
     * @param runtimeContext 运行时上下文
     * @return 输入视图
     */
    public static StepInputView from(StepSpec step, RuntimeContext runtimeContext) {
        if (step == null) {
            return new StepInputView(Map.of(), Map.of(), List.of(),
                    runtimeContext != null ? runtimeContext.getApprovalInput() : ApprovalInput.empty());
        }
        Map<String, Object> args = copyMap(step.getArguments());
        Map<String, Object> ctx = copyMap(step.getContext());
        List<String> dependencies = copyDependsOn(step.getDependsOn());
        ApprovalInput fromPolicy = resolveFromStepPolicy(step.getPolicy());
        ApprovalInput fromContext = ApprovalInput.fromMap(ctx);
        ApprovalInput fromRuntime = runtimeContext != null ? runtimeContext.getApprovalInput() : ApprovalInput.empty();
        ApprovalInput approvalInput = pickApproval(fromPolicy, fromContext, fromRuntime);
        return new StepInputView(args, ctx, dependencies, approvalInput);
    }

    /**
     * 输出执行边界映射。
     *
     * @return 执行边界映射
     */
    public Map<String, Object> toExecutionMap() {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (!arguments.isEmpty()) {
            merged.putAll(arguments);
        }
        if (!context.isEmpty()) {
            merged.put("context", new LinkedHashMap<>(context));
        }
        if (!dependsOn.isEmpty()) {
            merged.put("dependsOn", new ArrayList<>(dependsOn));
        }
        if (approvalInput.getRequiresApproval() != null) {
            merged.put("requiresApproval", approvalInput.getRequiresApproval());
        }
        if (approvalInput.getApprovalSource() != null) {
            merged.put("approvalSource", approvalInput.getApprovalSource());
        }
        return merged;
    }

    public Map<String, Object> arguments() {
        return arguments;
    }

    public Map<String, Object> context() {
        return context;
    }

    public List<String> dependsOn() {
        return dependsOn;
    }

    public ApprovalInput approvalInput() {
        return approvalInput;
    }

    public boolean requiresApproval() {
        return approvalInput.isRequired();
    }

    public String approvalSource() {
        return approvalInput.getApprovalSource();
    }

    private static ApprovalInput pickApproval(ApprovalInput fromPolicy,
                                              ApprovalInput fromContext,
                                              ApprovalInput fromRuntime) {
        if (fromPolicy != null && fromPolicy.isExplicit()) {
            return fromPolicy;
        }
        if (fromContext != null && fromContext.isExplicit()) {
            return fromContext;
        }
        if (fromRuntime != null && fromRuntime.isExplicit()) {
            return fromRuntime;
        }
        return ApprovalInput.empty();
    }

    private static ApprovalInput resolveFromStepPolicy(StepPolicy policy) {
        if (policy == null) {
            return ApprovalInput.empty();
        }
        return ApprovalInput.of(policy.getRequiresApproval(), policy.getApprovalSource());
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(source);
    }

    private static List<String> copyDependsOn(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(source);
    }
}

