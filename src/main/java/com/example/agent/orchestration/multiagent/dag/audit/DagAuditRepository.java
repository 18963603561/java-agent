package com.example.agent.orchestration.multiagent.dag.audit;

/**
 * DAG 审计仓储兼容门面。
 * <p>用途：向下兼容历史包路径，真实端口定义位于 domain.port。</p>
 */
public interface DagAuditRepository extends com.example.agent.orchestration.multiagent.dag.domain.port.DagAuditRepository {
}

