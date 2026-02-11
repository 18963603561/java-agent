package com.example.agent.orchestration.multiagent.dag.actor.state;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * DAG 消息去重内存仓储。
 *
 * <p>用途：提供默认去重实现，支持 TTL 自动过期。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agent.multiagent.storage", name = "mode", havingValue = "inmemory")
public class InMemoryDagMessageDedupRepository implements DagMessageDedupRepository {

    /**
     * 去重键到过期时间映射。
     */
    private final Map<String, Long> dedupIndex = new ConcurrentHashMap<>();

    @Override
    public boolean register(String dagRunId, String nodeId, String messageId, long ttlMs) {
        // 关键逻辑：关键字段缺失时返回已存在，避免无效消息进入执行路径。
        if (!StringUtils.hasText(dagRunId) || !StringUtils.hasText(nodeId) || !StringUtils.hasText(messageId)) {
            return false;
        }
        long now = System.currentTimeMillis();
        evictExpired(now);
        String key = buildKey(dagRunId, nodeId, messageId);
        long expiredAt = now + Math.max(1L, ttlMs);
        Long previous = dedupIndex.putIfAbsent(key, expiredAt);
        return previous == null;
    }

    @Override
    public void cleanupByDagRun(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return;
        }
        Iterator<Map.Entry<String, Long>> iterator = dedupIndex.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (!entry.getKey().startsWith(dagRunId + ":")) {
                continue;
            }
            iterator.remove();
        }
    }

    /**
     * 清理过期去重键。
     */
    private void evictExpired(long now) {
        Iterator<Map.Entry<String, Long>> iterator = dedupIndex.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() >= now) {
                continue;
            }
            iterator.remove();
        }
    }

    /**
     * 构建去重主键。
     */
    private String buildKey(String dagRunId, String nodeId, String messageId) {
        return dagRunId + ":" + nodeId + ":" + messageId;
    }
}
