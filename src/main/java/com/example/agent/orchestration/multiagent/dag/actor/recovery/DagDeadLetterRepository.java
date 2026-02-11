package com.example.agent.orchestration.multiagent.dag.actor.recovery;

/**
 * DAG 死信仓储兼容门面。
 * <p>用途：向下兼容历史包路径，真实端口定义位于 domain.port。</p>
 */
public interface DagDeadLetterRepository extends com.example.agent.orchestration.multiagent.dag.domain.port.DagDeadLetterRepository {
}

