package com.example.agent.planning.diagnostics;

import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.strategy.PlanningStrategyContext;
import com.example.agent.planning.strategy.PlanningStrategyResult;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 规划诊断快照构建器。
 *
 * <p>用途：将规划关键状态构建为诊断快照，供测试与故障排查使用，不参与主流程决策。
 */
@Component
public class PlanningDiagnosticsSnapshotBuilder {

    /**
     * 构建规划上下文诊断快照。
     *
     * @param context 规划上下文
     * @return 诊断快照
     */
    public Map<String, Object> buildContextSnapshot(PlanningContext context) {
        Map<String, Object> snapshot = new HashMap<>();
        if (context == null) {
            snapshot.put("available", false);
            return snapshot;
        }
        snapshot.put("available", true);
        snapshot.put("keys", context.mutableValues().keySet().size());
        snapshot.put("summary", context.toPromptSummary());
        return snapshot;
    }

    /**
     * 构建策略调度诊断快照。
     *
     * @param context 策略上下文
     * @param result 策略结果
     * @return 诊断快照
     */
    public Map<String, Object> buildStrategySnapshot(PlanningStrategyContext context,
                                                     PlanningStrategyResult result) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("planId", context != null ? context.getPlanId() : null);
        snapshot.put("mode", context != null ? context.getMode() : null);
        snapshot.put("strategy", context != null ? context.getStrategy() : null);
        snapshot.put("stepCount", context != null ? context.getStepCount() : 0);
        snapshot.put("terminal", result != null && result.isTerminal());
        snapshot.put("scene", result != null ? result.getScene() : null);
        snapshot.put("nextStepKey", result != null ? result.getNextStepKey() : null);
        return snapshot;
    }
}

