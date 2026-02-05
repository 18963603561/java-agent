package com.example.agent.runtime.engine;

/**
 * ReAct 终止条件评估器。
 */
public class ReactStopEvaluator {

    /**
     * 评估是否允许终止。
     *
     * @param decision 思考决策
     * @param iteration 当前轮次（从 1 开始）
     * @param properties 运行参数
     * @return 终止决策
     */
    public ReactStopDecision evaluate(ReactDecision decision, int iteration, ReactRuntimeProperties properties) {
        if (decision == null || !decision.wantsStop()) {
            return new ReactStopDecision(false, false, null);
        }
        int minIterations = Math.max(1, properties.getMinIterations());
        if (iteration < minIterations) {
            return new ReactStopDecision(false, false, "min_iterations");
        }
        String reason = decision.getStopReason();
        if (reason == null || reason.isBlank()) {
            reason = "completed";
        }
        return new ReactStopDecision(true, true, reason);
    }
}
