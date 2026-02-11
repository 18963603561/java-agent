package com.example.agent.orchestration.multiagent.dag.actor.state;

/**
 * DAG 运行态仓储兼容门面。
 * <p>用途：向下兼容历史包路径，真实端口定义位于 domain.port。</p>
 */
public interface DagRuntimeStateRepository extends com.example.agent.orchestration.multiagent.dag.domain.port.DagRuntimeStateRepository {
}

