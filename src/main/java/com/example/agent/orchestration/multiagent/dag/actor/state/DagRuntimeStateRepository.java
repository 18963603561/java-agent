package com.example.agent.orchestration.multiagent.dag.actor.state;

import java.util.List;
import java.util.Optional;

/**
 * DAG 运行状态仓储。
 *
 * <p>用途：持久化节点运行态快照，支持恢复与一致性校验。</p>
 */
public interface DagRuntimeStateRepository {

    /**
     * 保存节点快照。
     */
    void save(DagNodeRuntimeSnapshot snapshot);

    /**
     * 查询单节点快照。
     */
    Optional<DagNodeRuntimeSnapshot> findByNode(String dagRunId, String nodeId);

    /**
     * 查询运行下所有节点快照。
     */
    List<DagNodeRuntimeSnapshot> findByDagRun(String dagRunId);
}

