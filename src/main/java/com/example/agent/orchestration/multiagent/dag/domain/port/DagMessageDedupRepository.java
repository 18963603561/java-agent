package com.example.agent.orchestration.multiagent.dag.domain.port;

/**
 * DAG 消息去重仓储端口。
 * <p>用途：保证消息在运行窗口内只被处理一次，避免重复消费。</p>
 */
public interface DagMessageDedupRepository {

    boolean register(String dagRunId, String nodeId, String messageId, long ttlMs);

    void cleanupByDagRun(String dagRunId);
}

