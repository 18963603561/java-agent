package com.example.agent.runtime;

import java.util.Map;

/**
 * 步骤执行请求，用于描述单个步骤的类型与输入。
 *
 * <p>使用场景：规划服务生成步骤后，运行时按步骤类型分派执行。</p>
 * <p>注意事项：步骤输入允许携带上下文与工具参数，需在运行时合并与校验。</p>
 */
public class StepRequest {

    /**
     * 步骤类型。
     *
     * <p>使用场景：用于路由不同执行分支，例如 LLM、TOOL、THOUGHT_TREE、CHAIN_OF_THOUGHT、REACT。</p>
     * <p>边界条件：为空或未知类型时，运行时可能回退到默认流程或触发错误处理。</p>
     */
    private String stepType;
    /**
     * 步骤输入。
     *
     * <p>使用场景：</p>
     * <p>1）LLM 步骤：可包含 `question`、`topic`、`context`、`disableTools` 等。</p>
     * <p>2）TOOL 步骤：可包含 `tool`、`toolName`、工具参数与调用上下文。</p>
     * <p>3）推理/研究步骤：可包含 `prompt`、`query`、`constraints` 等。</p>
     * <p>边界条件：为 null 时，运行时将仅使用任务请求或全局上下文。</p>
     */
    private Map<String, Object> input;
    /**
     * 是否需要审批。
     *
     * <p>使用场景：高风险步骤需要人工确认时设置为 true。</p>
     * <p>边界条件：为 null 表示未显式指定，由运行时按策略推断。</p>
     */
    private Boolean requiresApproval;
    /**
     * 审批来源。
     *
     * <p>使用场景：标识审批需求来源，常见取值：user、step、evaluation。</p>
     * <p>边界条件：为空时由运行时设置默认来源。</p>
     */
    private String approvalSource;

    public StepRequest() {
    }

    public StepRequest(String stepType, Map<String, Object> input) {
        this.stepType = stepType;
        this.input = input;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getApprovalSource() {
        return approvalSource;
    }

    public void setApprovalSource(String approvalSource) {
        this.approvalSource = approvalSource;
    }
}