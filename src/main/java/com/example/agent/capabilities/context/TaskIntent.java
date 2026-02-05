package com.example.agent.capabilities.context;

import java.util.List;

/**
 * 任务意图与成功标准。
 */
public class TaskIntent {

    /**
     * 任务标识。
     */
    private String taskId;

    /**
     * 用户输入内容。
     */
    private String inputText;

    /**
     * 成功标准。
     */
    private String successCriteria;

    /**
     * 失败处理策略。
     */
    private String failurePolicy;

    /**
     * 输出要求。
     */
    private String requiredOutput;

    /**
     * 约束条件。
     */
    private List<String> constraints;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public String getSuccessCriteria() {
        return successCriteria;
    }

    public void setSuccessCriteria(String successCriteria) {
        this.successCriteria = successCriteria;
    }

    public String getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(String failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    public String getRequiredOutput() {
        return requiredOutput;
    }

    public void setRequiredOutput(String requiredOutput) {
        this.requiredOutput = requiredOutput;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<String> constraints) {
        this.constraints = constraints;
    }
}