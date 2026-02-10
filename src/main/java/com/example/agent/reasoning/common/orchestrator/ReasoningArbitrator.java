package com.example.agent.reasoning.common.orchestrator;

import com.example.agent.reasoning.common.ReasoningResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 推理结果仲裁器。
 *
 * <p>用途：在多策略候选结果中选择最优结果，确保最终输出稳定且可解释。
 */
@Component
public class ReasoningArbitrator {

    /**
     * 选择最佳结果。
     *
     * @param results 候选结果
     * @return 最优结果
     */
    public ReasoningResult selectBest(List<ReasoningResult> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("reasoning_results_missing");
        }
        List<ReasoningResult> candidates = new ArrayList<>();
        for (ReasoningResult result : results) {
            if (result == null) {
                continue;
            }
            if (!StringUtils.hasText(result.getSummary())) {
                continue;
            }
            candidates.add(result);
        }
        if (candidates.isEmpty()) {
            return results.get(0);
        }
        candidates.sort(Comparator
                .comparingDouble(ReasoningResult::getConfidence).reversed()
                .thenComparing(result -> "COMPLETED".equalsIgnoreCase(result.getStatus()) ? 0 : 1)
                .thenComparing(result -> result.getSummary().length(), Comparator.reverseOrder()));
        return candidates.get(0);
    }
}

