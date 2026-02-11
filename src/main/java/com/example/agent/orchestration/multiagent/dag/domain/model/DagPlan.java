package com.example.agent.orchestration.multiagent.dag.domain.model;

import java.util.List;
import java.util.Map;

/**
 * DAG 执行计划。
 *
 * <p>用途：封装节点列表与排序结果，供执行器执行时复用。</p>
 */
public class DagPlan {

    private final List<DagNode> nodes;
    private final List<String> topologicalOrder;
    private final Map<String, DagNode> nodeIndex;
    private final Map<String, Integer> inDegree;
    private final Map<String, List<String>> downstream;

    public DagPlan(List<DagNode> nodes,
                   List<String> topologicalOrder,
                   Map<String, DagNode> nodeIndex) {
        this(nodes, topologicalOrder, nodeIndex, Map.of(), Map.of());
    }

    public DagPlan(List<DagNode> nodes,
                   List<String> topologicalOrder,
                   Map<String, DagNode> nodeIndex,
                   Map<String, Integer> inDegree,
                   Map<String, List<String>> downstream) {
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        this.topologicalOrder = topologicalOrder == null ? List.of() : List.copyOf(topologicalOrder);
        this.nodeIndex = nodeIndex == null ? Map.of() : Map.copyOf(nodeIndex);
        this.inDegree = inDegree == null ? Map.of() : Map.copyOf(inDegree);
        this.downstream = downstream == null ? Map.of() : copyDownstream(downstream);
    }

    private Map<String, List<String>> copyDownstream(Map<String, List<String>> downstream) {
        Map<String, List<String>> copied = new java.util.HashMap<>();
        downstream.forEach((nodeId, children) -> copied.put(nodeId,
                children == null ? List.of() : List.copyOf(children)));
        return Map.copyOf(copied);
    }

    public List<DagNode> getNodes() {
        return nodes;
    }

    public List<String> getTopologicalOrder() {
        return topologicalOrder;
    }

    public Map<String, DagNode> getNodeIndex() {
        return nodeIndex;
    }

    public Map<String, Integer> getInDegree() {
        return inDegree;
    }

    public Map<String, List<String>> getDownstream() {
        return downstream;
    }
}
