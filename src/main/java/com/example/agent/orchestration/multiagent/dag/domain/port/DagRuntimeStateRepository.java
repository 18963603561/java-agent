package com.example.agent.orchestration.multiagent.dag.domain.port;

import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeRuntimeSnapshot;
import java.util.List;
import java.util.Optional;

/**
 * DAG 运行态仓储端口。
 * <p>用途：持久化节点运行快照并支持按运行实例查询。</p>
 */
public interface DagRuntimeStateRepository {

    void save(DagNodeRuntimeSnapshot snapshot);

    Optional<DagNodeRuntimeSnapshot> findByNode(String dagRunId, String nodeId);

    List<DagNodeRuntimeSnapshot> findByDagRun(String dagRunId);
}
