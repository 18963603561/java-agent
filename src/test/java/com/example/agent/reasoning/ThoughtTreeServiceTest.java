package com.example.agent.reasoning;

import com.example.agent.reasoning.common.config.ReasoningConfigValidator;
import com.example.agent.reasoning.thoughttree.ThoughtNode;
import com.example.agent.reasoning.thoughttree.ThoughtTreeConfig;
import com.example.agent.reasoning.thoughttree.ThoughtTreeResult;
import com.example.agent.reasoning.thoughttree.ThoughtTreeScoringPolicy;
import com.example.agent.reasoning.thoughttree.ThoughtTreeService;
import com.example.agent.reasoning.thoughttree.ThoughtTreeTerminalPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThoughtTreeServiceTest {

    private ThoughtTreeService newService() {
        return new ThoughtTreeService(
                new ReasoningConfigValidator(),
                new ThoughtTreeScoringPolicy(),
                new ThoughtTreeTerminalPolicy()
        );
    }

    @Test
    void buildTreeProducesResult() {
        ThoughtTreeService service = newService();
        ThoughtTreeResult result = service.buildTree("分析订单异常原因", new ThoughtTreeConfig());
        assertNotNull(result.getRoot());
        assertTrue(result.getTotalThoughts() > 0);
        assertNotNull(result.getBestSolution());
        assertTrue(result.getConfidence() >= 0);
    }

    @Test
    void expandReturnsNodesWithScores() {
        ThoughtTreeService service = newService();
        var nodes = service.expand("制定优化方案");
        assertFalse(nodes.isEmpty());
        for (ThoughtNode node : nodes) {
            assertTrue(node.getScore() >= 0 && node.getScore() <= 1);
        }
    }

    @Test
    void bestPathStartsFromRootAndMaintainsParentChain() {
        ThoughtTreeService service = newService();
        ThoughtTreeConfig config = new ThoughtTreeConfig();
        config.setMaxDepth(3);
        config.setBranchingFactor(3);
        config.setExplorationBudget(12);

        ThoughtTreeResult result = service.buildTree("设计系统优化路径", config);
        List<ThoughtNode> path = result.getBestPath();
        assertFalse(path.isEmpty());
        assertEquals("root", path.get(0).getNodeId());

        for (int i = 1; i < path.size(); i++) {
            ThoughtNode previous = path.get(i - 1);
            ThoughtNode current = path.get(i);
            assertEquals(previous.getNodeId(), current.getParentId());
        }
    }

    @Test
    void bestSolutionFollowsBestPathOrder() {
        ThoughtTreeService service = newService();
        ThoughtTreeConfig config = new ThoughtTreeConfig();
        config.setMaxDepth(3);
        config.setBranchingFactor(3);
        config.setExplorationBudget(12);

        ThoughtTreeResult result = service.buildTree("制定迭代交付计划", config);
        List<ThoughtNode> path = result.getBestPath();
        String solution = result.getBestSolution();
        assertNotNull(solution);
        assertFalse(solution.isBlank());

        int stepOrder = 1;
        for (ThoughtNode node : path) {
            if ("root".equals(node.getNodeId())) {
                continue;
            }
            assertTrue(solution.contains(stepOrder + ". " + node.getContent()));
            stepOrder++;
        }
    }

    @Test
    void totalThoughtsShouldNotExceedExplorationBudget() {
        ThoughtTreeService service = newService();
        ThoughtTreeConfig config = new ThoughtTreeConfig();
        config.setMaxDepth(4);
        config.setBranchingFactor(4);
        config.setExplorationBudget(5);

        ThoughtTreeResult result = service.buildTree("预算受限时的排期策略", config);
        assertTrue(result.getTotalThoughts() <= 5);
    }
}
