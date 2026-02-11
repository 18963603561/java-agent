package com.example.agent.orchestration.multiagent.role;

import com.example.agent.orchestration.multiagent.AgentProfile;
import com.example.agent.orchestration.multiagent.AgentProfileProperties;
import com.example.agent.orchestration.multiagent.AgentRole;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 角色回退工厂。
 * <p>用途：统一构建默认角色集合，避免回退逻辑散落在多个组件。</p>
 */
@Component
public class RoleFallbackFactory {

    private final AgentProfileProperties profileProperties;

    public RoleFallbackFactory(AgentProfileProperties profileProperties) {
        this.profileProperties = profileProperties;
    }

    /**
     * 构建回退角色。
     */
    public List<AgentRole> buildFallbackRoles() {
        List<AgentRole> roles = new ArrayList<>();
        // 关键逻辑：优先使用配置档案构建回退角色，保证执行策略可配置。
        if (profileProperties.getItems() != null) {
            for (AgentProfile profile : profileProperties.getItems()) {
                AgentRole role = new AgentRole();
                role.setRoleId(profile.getAgentId());
                role.setName(profile.getAgentId());
                role.setModelId(profile.getModelId());
                role.setDescription(profile.getPrompt());
                roles.add(role);
            }
        }
        // 关键逻辑：当无配置时提供单默认角色，避免协调链路中断。
        if (roles.isEmpty()) {
            AgentRole role = new AgentRole();
            role.setRoleId("default");
            role.setName("default");
            role.setDescription("默认单角色执行");
            roles.add(role);
        }
        return roles;
    }
}

