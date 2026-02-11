package com.example.agent.orchestration.multiagent.dag.infrastructure.state;

import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeRuntimeSnapshot;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagRuntimeStateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * DAG 运行状态内存仓储。
 *
 * <p>用途：提供无外部依赖的默认状态持久化实现。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agent.multiagent.storage", name = "mode", havingValue = "inmemory")
public class InMemoryDagRuntimeStateRepository implements DagRuntimeStateRepository {

    /**
     * 运行时快照存储。
     */
    private final Map<String, DagNodeRuntimeSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public void save(DagNodeRuntimeSnapshot snapshot) {
        // 关键逻辑：关键键缺失时不落盘，避免污染数据集。
        if (snapshot == null || !StringUtils.hasText(snapshot.getDagRunId()) || !StringUtils.hasText(snapshot.getNodeId())) {
            return;
        }
        String key = buildKey(snapshot.getDagRunId(), snapshot.getNodeId());
        DagNodeRuntimeSnapshot copy = copySnapshot(snapshot);
        copy.setUpdatedAt(Instant.now());
        store.put(key, copy);
    }

    @Override
    public Optional<DagNodeRuntimeSnapshot> findByNode(String dagRunId, String nodeId) {
        String key = buildKey(dagRunId, nodeId);
        DagNodeRuntimeSnapshot snapshot = store.get(key);
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.of(copySnapshot(snapshot));
    }

    @Override
    public List<DagNodeRuntimeSnapshot> findByDagRun(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return List.of();
        }
        List<DagNodeRuntimeSnapshot> result = new ArrayList<>();
        for (DagNodeRuntimeSnapshot snapshot : store.values()) {
            if (!dagRunId.equals(snapshot.getDagRunId())) {
                continue;
            }
            result.add(copySnapshot(snapshot));
        }
        return result;
    }

    /**
     * 构建主键。
     */
    private String buildKey(String dagRunId, String nodeId) {
        return String.valueOf(dagRunId) + ":" + String.valueOf(nodeId);
    }

    /**
     * 复制快照。
     */
    private DagNodeRuntimeSnapshot copySnapshot(DagNodeRuntimeSnapshot source) {
        DagNodeRuntimeSnapshot target = new DagNodeRuntimeSnapshot();
        target.setDagRunId(source.getDagRunId());
        target.setWorkflowId(source.getWorkflowId());
        target.setNodeId(source.getNodeId());
        target.setStatus(source.getStatus());
        target.setRemainingDependencies(source.getRemainingDependencies());
        target.setAttempt(source.getAttempt());
        target.setVersion(source.getVersion());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
