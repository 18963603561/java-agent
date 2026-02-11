package com.example.agent.orchestration.multiagent.dag.actor.state;

/**
 * DAG 消息去重仓储。
 *
 * <p>用途：在跨实例场景下保证消息只生效一次。</p>
 */
public interface DagMessageDedupRepository {

    /**
     * 尝试注册消息。
     *
     * @return true 表示首次注册成功，false 表示已存在
     */
    boolean register(String dagRunId, String nodeId, String messageId, long ttlMs);

    /**
     * 清理指定运行下的去重键。
     */
    void cleanupByDagRun(String dagRunId);
}

