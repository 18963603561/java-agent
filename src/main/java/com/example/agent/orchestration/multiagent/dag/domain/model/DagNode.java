package com.example.agent.orchestration.multiagent.dag.domain.model;

import com.example.agent.orchestration.multiagent.AgentRole;
import java.util.List;

/**
 * DAG 节点定义。
 *
 * <p>用途：描述多智能体图执行中的单个角色节点，包含依赖与 topic 协作元数据。</p>
 */
public class DagNode {

    private final String nodeId;
    private final AgentRole role;
    private final List<String> dependsOn;
    private final List<String> consumes;
    private final List<String> produces;

    public DagNode(String nodeId,
                   AgentRole role,
                   List<String> dependsOn,
                   List<String> consumes,
                   List<String> produces) {
        this.nodeId = nodeId;
        this.role = role;
        this.dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        this.consumes = consumes == null ? List.of() : List.copyOf(consumes);
        this.produces = produces == null ? List.of() : List.copyOf(produces);
    }

    public String getNodeId() {
        return nodeId;
    }

    public AgentRole getRole() {
        return role;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public List<String> getConsumes() {
        return consumes;
    }

    public List<String> getProduces() {
        return produces;
    }
}
