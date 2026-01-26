package com.example.agent.multiagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DAG 执行器，用于多智能体依赖执行。
 */
@Service
public class AgentGraphExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentGraphExecutor.class);

    public void execute(AgentGraph graph) {
        int nodes = graph.getNodes() == null ? 0 : graph.getNodes().size();
        int edges = graph.getEdges() == null ? 0 : graph.getEdges().size();
        log.info("DAG 执行, nodes={}, edges={}", nodes, edges);
    }
}
