package com.example.agent.reasoning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThoughtTreeServiceTest {

    @Test
    void buildTreeProducesResult() {
        ThoughtTreeService service = new ThoughtTreeService();
        ThoughtTreeResult result = service.buildTree("分析订单异常原因", new ThoughtTreeConfig());
        assertNotNull(result.getRoot());
        assertTrue(result.getTotalThoughts() > 0);
        assertNotNull(result.getBestSolution());
        assertTrue(result.getConfidence() >= 0);
    }

    @Test
    void expandReturnsNodesWithScores() {
        ThoughtTreeService service = new ThoughtTreeService();
        var nodes = service.expand("制定优化方案");
        assertFalse(nodes.isEmpty());
        for (ThoughtNode node : nodes) {
            assertTrue(node.getScore() >= 0 && node.getScore() <= 1);
        }
    }
}
