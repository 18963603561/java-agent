package com.example.agent.reasoning;

import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.orchestrator.ReasoningArbitrator;
import com.example.agent.reasoning.common.result.CotPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReasoningArbitratorTest {

    private final ReasoningArbitrator arbitrator = new ReasoningArbitrator();

    @Test
    void selectBestShouldPreferHigherConfidence() {
        ReasoningResult low = new ReasoningResult(
                "cot", "low", 0.3, "completed", "COMPLETED", null, new CotPayload(1, "low")
        );
        ReasoningResult high = new ReasoningResult(
                "debate", "high", 0.9, "completed", "COMPLETED", null, new CotPayload(1, "high")
        );

        ReasoningResult selected = arbitrator.selectBest(List.of(low, high));
        assertEquals("debate", selected.getStrategyType());
    }

    @Test
    void selectBestShouldIgnoreBlankSummary() {
        ReasoningResult invalid = new ReasoningResult(
                "cot", "  ", 1.0, "completed", "COMPLETED", null, new CotPayload(1, "")
        );
        ReasoningResult valid = new ReasoningResult(
                "thought_tree", "usable", 0.4, "completed", "COMPLETED", null, new CotPayload(1, "usable")
        );

        ReasoningResult selected = arbitrator.selectBest(List.of(invalid, valid));
        assertEquals("thought_tree", selected.getStrategyType());
    }

    @Test
    void selectBestShouldPreferCompletedWhenConfidenceSame() {
        ReasoningResult completed = new ReasoningResult(
                "cot", "done", 0.7, "completed", "COMPLETED", null, new CotPayload(1, "done")
        );
        ReasoningResult stopped = new ReasoningResult(
                "debate", "done", 0.7, "max_steps", "STOPPED", null, new CotPayload(1, "done")
        );

        ReasoningResult selected = arbitrator.selectBest(List.of(stopped, completed));
        assertEquals("cot", selected.getStrategyType());
    }
}

