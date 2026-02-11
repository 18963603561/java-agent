package com.example.agent.orchestration.multiagent.dag.infrastructure.recovery;

import com.example.agent.orchestration.multiagent.dag.actor.recovery.DagDeadLetterMessage;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagDeadLetterRepository;
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
 * DAG 死信内存仓储。
 *
 * <p>用途：提供默认死信存储实现，支持运行时排障与重放。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agent.multiagent.storage", name = "mode", havingValue = "inmemory")
public class InMemoryDagDeadLetterRepository implements DagDeadLetterRepository {

    /**
     * 死信存储。
     */
    private final Map<String, DagDeadLetterMessage> store = new ConcurrentHashMap<>();

    @Override
    public void save(DagDeadLetterMessage message) {
        // 关键逻辑：死信主键缺失时自动生成，避免保存失败。
        if (message == null) {
            return;
        }
        if (!StringUtils.hasText(message.getDeadLetterId())) {
            message.setDeadLetterId(buildDeadLetterId(message));
        }
        if (message.getFirstFailedAt() == null) {
            message.setFirstFailedAt(Instant.now());
        }
        message.setLastFailedAt(Instant.now());
        store.put(message.getDeadLetterId(), copyMessage(message));
    }

    @Override
    public List<DagDeadLetterMessage> findByDagRun(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return List.of();
        }
        List<DagDeadLetterMessage> result = new ArrayList<>();
        for (DagDeadLetterMessage message : store.values()) {
            // 关键逻辑：按运行标识过滤死信集合。
            if (!dagRunId.equals(message.getDagRunId())) {
                continue;
            }
            result.add(copyMessage(message));
        }
        return result;
    }

    @Override
    public Optional<DagDeadLetterMessage> findById(String deadLetterId) {
        if (!StringUtils.hasText(deadLetterId)) {
            return Optional.empty();
        }
        DagDeadLetterMessage message = store.get(deadLetterId);
        if (message == null) {
            return Optional.empty();
        }
        return Optional.of(copyMessage(message));
    }

    @Override
    public void deleteById(String deadLetterId) {
        if (!StringUtils.hasText(deadLetterId)) {
            return;
        }
        store.remove(deadLetterId);
    }

    /**
     * 构建死信标识。
     */
    private String buildDeadLetterId(DagDeadLetterMessage message) {
        String dagRunId = StringUtils.hasText(message.getDagRunId()) ? message.getDagRunId() : "dag-run";
        String nodeId = StringUtils.hasText(message.getNodeId()) ? message.getNodeId() : "node";
        return dagRunId + ":" + nodeId + ":" + System.nanoTime();
    }

    /**
     * 复制死信对象。
     */
    private DagDeadLetterMessage copyMessage(DagDeadLetterMessage source) {
        DagDeadLetterMessage target = new DagDeadLetterMessage();
        target.setDeadLetterId(source.getDeadLetterId());
        target.setDagRunId(source.getDagRunId());
        target.setWorkflowId(source.getWorkflowId());
        target.setNodeId(source.getNodeId());
        target.setReason(source.getReason());
        target.setDeliveryAttempt(source.getDeliveryAttempt());
        target.setFirstFailedAt(source.getFirstFailedAt());
        target.setLastFailedAt(source.getLastFailedAt());
        target.setEnvelope(source.getEnvelope());
        return target;
    }
}
