package com.example.agent.orchestration.multiagent.dag.application.planner;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagCycleDetectedException;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagNode;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagPlan;
import com.example.agent.orchestration.multiagent.support.StepArgumentReader;
import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DAG 计划器。
 *
 * <p>用途：根据角色列表与步骤依赖构建 DAG，并执行环检测与拓扑排序。</p>
 */
@Component
public class DagPlanner {

    private static final Logger log = LoggerFactory.getLogger(DagPlanner.class);
    private final StepArgumentReader stepArgumentReader;

    /**
     * 构造 DAG 规划器。
     */
    public DagPlanner(StepArgumentReader stepArgumentReader) {
        this.stepArgumentReader = stepArgumentReader;
    }

    /**
     * 构建 DAG 计划。
     *
     * @param roles 角色列表
     * @param step 步骤定义
     * @return DAG 计划
     */
    public DagPlan build(List<AgentRole> roles, StepSpec step) {
        List<String> stepDependencies = normalizeDependsOn(step != null ? step.getDependsOn() : null);
        Map<String, List<String>> roleDependencies = resolveRoleDependencies(step);
        List<String> consumes = resolveStringList(step, "consumes");
        List<String> produces = resolveStringList(step, "produces");

        List<DagNode> nodes = new ArrayList<>();
        Map<String, DagNode> nodeIndex = new HashMap<>();
        if (roles != null) {
            for (int index = 0; index < roles.size(); index++) {
                AgentRole role = roles.get(index);
                if (role == null || !StringUtils.hasText(role.getRoleId())) {
                    continue;
                }
                String nodeId = role.getRoleId().trim();
                List<String> nodeDependsOn = resolveNodeDependencies(index,
                        nodeId,
                        stepDependencies,
                        roleDependencies);
                DagNode node = new DagNode(nodeId, role, nodeDependsOn, consumes, produces);
                nodes.add(node);
                nodeIndex.put(nodeId, node);
            }
        }
        GraphMetadata graphMetadata = buildGraphMetadata(nodes, nodeIndex);
        List<String> order = topologicalSort(nodes, graphMetadata.inDegree(), graphMetadata.downstream());
        return new DagPlan(nodes,
                order,
                nodeIndex,
                graphMetadata.inDegree(),
                graphMetadata.downstream());
    }

    private GraphMetadata buildGraphMetadata(List<DagNode> nodes, Map<String, DagNode> nodeIndex) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> downstream = new HashMap<>();

        for (DagNode node : nodes) {
            String nodeId = node.getNodeId();
            inDegree.putIfAbsent(nodeId, 0);
            downstream.putIfAbsent(nodeId, new ArrayList<>());
        }
        for (DagNode node : nodes) {
            String current = node.getNodeId();
            for (String dep : node.getDependsOn()) {
                // 关键逻辑：依赖节点不存在时直接拒绝，避免运行时进入不可恢复状态。
                if (!nodeIndex.containsKey(dep)) {
                    String message = "dag_dependency_not_found:" + current + "<-" + dep;
                    log.error("检测到非法依赖, nodeId={}, dependency={}", current, dep);
                    throw new IllegalArgumentException(message);
                }
                // 关键逻辑：自依赖在规划期阻断，避免污染后续拓扑逻辑。
                if (dep.equals(current)) {
                    String message = "dag_self_dependency:" + current;
                    log.error("检测到节点自依赖, nodeId={}", current);
                    throw new DagCycleDetectedException(message);
                }
                downstream.computeIfAbsent(dep, ignore -> new ArrayList<>()).add(current);
                inDegree.put(current, inDegree.getOrDefault(current, 0) + 1);
            }
        }
        return new GraphMetadata(copyInDegree(inDegree), copyDownstream(downstream));
    }

    private List<String> topologicalSort(List<DagNode> nodes,
                                         Map<String, Integer> inDegree,
                                         Map<String, List<String>> downstream) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> mutableInDegree = new HashMap<>(inDegree);

        ArrayDeque<String> queue = new ArrayDeque<>();
        mutableInDegree.forEach((nodeId, degree) -> {
            if (degree == 0) {
                queue.add(nodeId);
            }
        });

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(current);
            for (String next : downstream.getOrDefault(current, List.of())) {
                int degree = mutableInDegree.getOrDefault(next, 0) - 1;
                mutableInDegree.put(next, degree);
                if (degree == 0) {
                    queue.add(next);
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            Set<String> cycleNodes = new HashSet<>();
            mutableInDegree.forEach((nodeId, degree) -> {
                if (degree > 0) {
                    cycleNodes.add(nodeId);
                }
            });
            String message = "dag_cycle_detected:" + String.join("->", cycleNodes);
            log.error("检测到DAG环依赖, nodes={}", cycleNodes);
            throw new DagCycleDetectedException(message);
        }
        return sorted;
    }

    private Map<String, Integer> copyInDegree(Map<String, Integer> inDegree) {
        return Map.copyOf(new HashMap<>(inDegree));
    }

    private Map<String, List<String>> copyDownstream(Map<String, List<String>> downstream) {
        Map<String, List<String>> copied = new HashMap<>();
        downstream.forEach((nodeId, children) -> copied.put(nodeId,
                children == null ? List.of() : List.copyOf(children)));
        return Map.copyOf(copied);
    }

    private List<String> normalizeDependsOn(List<String> dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : dependsOn) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String value = item.trim();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> resolveRoleDependencies(StepSpec step) {
        Map<String, Object> dependencies = stepArgumentReader.readMap(step, "dagDependencies");
        if (dependencies.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : dependencies.entrySet()) {
            String roleId = entry.getKey();
            if (!StringUtils.hasText(roleId)) {
                continue;
            }
            result.put(roleId, stepArgumentReader.normalizeStringList(entry.getValue()));
        }
        return result;
    }

    private List<String> resolveNodeDependencies(int index,
                                                 String nodeId,
                                                 List<String> stepDependencies,
                                                 Map<String, List<String>> roleDependencies) {
        if (roleDependencies != null && roleDependencies.containsKey(nodeId)) {
            return roleDependencies.get(nodeId);
        }
        if (index == 0 || stepDependencies == null || stepDependencies.isEmpty()) {
            return List.of();
        }
        List<String> dependencies = new ArrayList<>();
        for (String dependency : stepDependencies) {
            if (!StringUtils.hasText(dependency)) {
                continue;
            }
            String normalized = dependency.trim();
            // 关键逻辑：步骤级回退依赖自动排除自身，避免无意义自依赖污染执行图。
            if (nodeId.equals(normalized)) {
                continue;
            }
            if (!dependencies.contains(normalized)) {
                dependencies.add(normalized);
            }
        }
        return dependencies;
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveStringList(StepSpec step, String key) {
        return stepArgumentReader.readStringList(step, key);
    }

    private record GraphMetadata(Map<String, Integer> inDegree,
                                 Map<String, List<String>> downstream) {
    }
}
