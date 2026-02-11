package com.example.agent.orchestration.multiagent.dag.actor.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * DAG 节点租约服务。
 *
 * <p>用途：保证同一节点同一时刻仅一个实例执行。</p>
 */
@Service
public class DagNodeLeaseService {

    /**
     * 租约索引。
     */
    private final Map<String, LeaseRecord> leaseIndex = new ConcurrentHashMap<>();

    /**
     * 尝试获取租约。
     */
    public boolean acquire(String dagRunId, String nodeId, String instanceId, long ttlMs) {
        if (!StringUtils.hasText(dagRunId)
                || !StringUtils.hasText(nodeId)
                || !StringUtils.hasText(instanceId)
                || ttlMs <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = buildKey(dagRunId, nodeId);
        LeaseRecord current = leaseIndex.get(key);
        // 关键逻辑：无租约或已过期时可直接占有。
        if (current == null || current.expiredAt < now) {
            leaseIndex.put(key, new LeaseRecord(instanceId, now + ttlMs));
            return true;
        }
        // 关键逻辑：租约归属当前实例时允许续租。
        if (instanceId.equals(current.instanceId)) {
            leaseIndex.put(key, new LeaseRecord(instanceId, now + ttlMs));
            return true;
        }
        return false;
    }

    /**
     * 释放租约。
     */
    public void release(String dagRunId, String nodeId, String instanceId) {
        String key = buildKey(dagRunId, nodeId);
        LeaseRecord current = leaseIndex.get(key);
        if (current == null) {
            return;
        }
        // 关键逻辑：仅租约持有者可释放，避免误释放。
        if (!StringUtils.hasText(instanceId) || !instanceId.equals(current.instanceId)) {
            return;
        }
        leaseIndex.remove(key);
    }

    /**
     * 校验当前实例是否持有租约。
     */
    public boolean isOwner(String dagRunId, String nodeId, String instanceId) {
        String key = buildKey(dagRunId, nodeId);
        LeaseRecord current = leaseIndex.get(key);
        if (current == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (current.expiredAt < now) {
            leaseIndex.remove(key);
            return false;
        }
        return instanceId.equals(current.instanceId);
    }

    /**
     * 清理指定运行的租约。
     */
    public void cleanupByDagRun(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return;
        }
        leaseIndex.keySet().removeIf(key -> key.startsWith(dagRunId + ":"));
    }

    /**
     * 构建租约键。
     */
    private String buildKey(String dagRunId, String nodeId) {
        return String.valueOf(dagRunId) + ":" + String.valueOf(nodeId);
    }

    /**
     * 租约记录。
     */
    private record LeaseRecord(String instanceId, long expiredAt) {
    }
}

