package com.example.agent.orchestration.multiagent.role;

/**
 * 角色解析上下文。
 * <p>用途：在角色解析链路中统一传递 workflow 与步骤上下文，保证日志字段一致。</p>
 */
public record RoleResolveContext(String workflowId, String stepType) {
}

