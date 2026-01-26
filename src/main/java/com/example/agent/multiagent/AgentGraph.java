package com.example.agent.multiagent;

import java.util.List;

/**
 * 智能体 DAG 定义。
 */
public class AgentGraph {

    private List<String> nodes;
    private List<String> edges;

    public AgentGraph() {
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    public List<String> getEdges() {
        return edges;
    }

    public void setEdges(List<String> edges) {
        this.edges = edges;
    }
}
