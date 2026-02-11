package com.example.agent.orchestration.multiagent.event;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Team 领域事件发布器。
 *
 * <p>用途：统一团队编排相关事件发布，隔离团队域与其它域事件细节。</p>
 */
public class TeamEventPublisher {

    private final EventPublishSupport publishSupport;

    /**
     * 构造团队事件发布器。
     */
    public TeamEventPublisher(EventPublishSupport publishSupport) {
        this.publishSupport = publishSupport;
    }

    /**
     * 发布团队招募与角色分配事件。
     */
    public void publishTeamEvents(TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  List<AgentRole> roles) {
        List<AgentRole> safeRoles = roles == null ? List.of() : roles;
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.TEAM_RECRUITED,
                Map.of("teamSize", safeRoles.size()));
        for (AgentRole role : safeRoles) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("roleId", role.getRoleId());
            payload.put("name", role.getName());
            payload.put("description", role.getDescription());
            if (role.getModelId() != null) {
                payload.put("modelId", role.getModelId());
            }
            publishSupport.publishEvent(tenantContext,
                    workflowId,
                    seqCounter,
                    EventType.ROLE_ASSIGNED,
                    payload);
        }
    }

    /**
     * 发布团队状态事件。
     */
    public void publishTeamStatus(TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  String status,
                                  Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        if (details != null && !details.isEmpty()) {
            payload.putAll(details);
        }
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.TEAM_STATUS,
                payload);
    }
}

