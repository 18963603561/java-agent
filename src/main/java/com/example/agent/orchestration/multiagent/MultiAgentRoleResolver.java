package com.example.agent.orchestration.multiagent;

import com.example.agent.orchestration.multiagent.role.RoleFallbackFactory;
import com.example.agent.orchestration.multiagent.role.RolePlanParser;
import com.example.agent.orchestration.multiagent.role.RolePlanRepairService;
import com.example.agent.orchestration.multiagent.role.RoleResolveContext;
import com.example.agent.orchestration.multiagent.role.RoleResolveDiagnostics;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 多智能体角色解析器。
 * <p>用途：封装角色 JSON 解析、修复与回退策略，保持协调器主链路简洁。
 */
@Component
public class MultiAgentRoleResolver {

    private final RolePlanParser rolePlanParser;
    private final RolePlanRepairService rolePlanRepairService;
    private final RoleFallbackFactory roleFallbackFactory;
    private final RoleResolveDiagnostics roleResolveDiagnostics;

    public MultiAgentRoleResolver(RolePlanParser rolePlanParser,
                                  RolePlanRepairService rolePlanRepairService,
                                  RoleFallbackFactory roleFallbackFactory,
                                  RoleResolveDiagnostics roleResolveDiagnostics) {
        this.rolePlanParser = rolePlanParser;
        this.rolePlanRepairService = rolePlanRepairService;
        this.roleFallbackFactory = roleFallbackFactory;
        this.roleResolveDiagnostics = roleResolveDiagnostics;
    }

    /**
     * 解析并修复角色结果。
     */
    public RoleResolveResult resolve(String rawContent,
                                     Map<String, Object> inputSummary,
                                     String workflowId,
                                     String stepType) {
        RoleResolveContext resolveContext = new RoleResolveContext(workflowId, stepType);

        // 关键逻辑：优先解析原始输出，成功则直接返回，避免额外修复调用。
        RolePlanParser.ParseResult parseResult = rolePlanParser.parse(rawContent, resolveContext, false);
        if (parseResult.success()) {
            return new RoleResolveResult(parseResult.roles(), null, false, false);
        }

        String parseErrorType = roleResolveDiagnostics.resolveParseErrorType(rawContent, parseResult.errorType());
        // 关键逻辑：原始解析失败后仅尝试一次修复，避免修复链路放大延迟。
        String repairedContent = rolePlanRepairService.repair(rawContent, inputSummary, resolveContext);
        if (repairedContent == null) {
            return new RoleResolveResult(List.of(), parseErrorType, true, false);
        }
        RolePlanParser.ParseResult repairedResult = rolePlanParser.parse(repairedContent, resolveContext, true);
        if (repairedResult.success()) {
            return new RoleResolveResult(repairedResult.roles(), parseErrorType, true, true);
        }
        return new RoleResolveResult(List.of(), parseErrorType, true, false);
    }

    /**
     * 构建回退角色。
     */
    public List<AgentRole> buildFallbackRoles() {
        return roleFallbackFactory.buildFallbackRoles();
    }

    /**
     * 角色解析结果。
     */
    public record RoleResolveResult(List<AgentRole> roles,
                                    String parseErrorType,
                                    boolean repairAttempted,
                                    boolean repairSuccess) {
    }
}
