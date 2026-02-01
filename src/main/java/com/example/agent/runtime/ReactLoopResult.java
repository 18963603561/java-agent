package com.example.agent.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 循环结果。
 */
public class ReactLoopResult {

    /**
     * 是否完成。
     */
    private boolean completed;

    /**
     * 终止原因。
     */
    private String stopReason;

    /**
     * 实际迭代次数。
     */
    private int iterations;

    /**
     * 最终答案。
     */
    private String finalAnswer;

    /**
     * 观察记录。
     */
    private List<ReactObservation> observations = new ArrayList<>();

    /**
     * 决策记录。
     */
    private List<ReactDecision> decisions = new ArrayList<>();

    /**
     * 获取是否完成循环。
     *
     * @return 是否完成
     */
    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public List<ReactObservation> getObservations() {
        return observations;
    }

    public void setObservations(List<ReactObservation> observations) {
        this.observations = observations;
    }

    public List<ReactDecision> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<ReactDecision> decisions) {
        this.decisions = decisions;
    }
}
