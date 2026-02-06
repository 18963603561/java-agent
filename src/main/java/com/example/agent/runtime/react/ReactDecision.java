package com.example.agent.runtime.react;

import java.util.Map;

/**
 * ReAct 思考阶段的决策结果。
 */
public class ReactDecision {

    /**
     * 动作类型：tool/stop/none 等。
     */
    private String action;

    /**
     * 工具名称。
     */
    private String tool;

    /**
     * 工具参数。
     */
    private Map<String, Object> arguments;

    /**
     * 是否建议终止循环。
     */
    private Boolean shouldStop;

    /**
     * 终止原因。
     */
    private String stopReason;

    /**
     * 建议的最终答案。
     */
    private String finalAnswer;

    /**
     * 本次决策模型原始输出引用。
     */
    private String rawRef;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    public Boolean getShouldStop() {
        return shouldStop;
    }

    public void setShouldStop(Boolean shouldStop) {
        this.shouldStop = shouldStop;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }

    /**
     * 判断是否希望终止。
     *
     * @return 是否终止
     */
    public boolean wantsStop() {
        if (Boolean.TRUE.equals(shouldStop)) {
            return true;
        }
        if (finalAnswer != null && !finalAnswer.isBlank()) {
            return true;
        }
        return action != null && "stop".equalsIgnoreCase(action);
    }
}
