package com.example.agent.reasoning.common.result;

import com.example.agent.reasoning.thoughttree.ThoughtNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ThoughtTree 强类型结果载荷。
 *
 * <p>用途：定义思维树策略的固定输出字段，避免动态键访问风险。
 */
public class ThoughtTreePayload implements ReasoningPayload {

    private final int totalThoughts;
    private final int treeDepth;
    private final List<ThoughtNode> bestPath;
    private final String bestSolution;

    /**
     * 构造 ThoughtTree 载荷。
     *
     * @param totalThoughts 总思维节点数
     * @param treeDepth 树深度
     * @param bestPath 最佳路径
     * @param bestSolution 最佳方案
     */
    public ThoughtTreePayload(int totalThoughts,
                              int treeDepth,
                              List<ThoughtNode> bestPath,
                              String bestSolution) {
        this.totalThoughts = totalThoughts;
        this.treeDepth = treeDepth;
        this.bestPath = bestPath == null ? List.of() : List.copyOf(bestPath);
        this.bestSolution = bestSolution;
    }

    public int getTotalThoughts() {
        return totalThoughts;
    }

    public int getTreeDepth() {
        return treeDepth;
    }

    public List<ThoughtNode> getBestPath() {
        return bestPath;
    }

    public String getBestSolution() {
        return bestSolution;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("totalThoughts", totalThoughts);
        map.put("treeDepth", treeDepth);
        map.put("bestPath", bestPath);
        map.put("bestSolution", bestSolution);
        return map;
    }
}

