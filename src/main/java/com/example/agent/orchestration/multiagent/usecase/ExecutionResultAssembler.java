package com.example.agent.orchestration.multiagent.usecase;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentExecutionMode;
import com.example.agent.orchestration.multiagent.model.DagExecutionResult;
import com.example.agent.orchestration.multiagent.model.MultiAgentExecutionResult;
import com.example.agent.orchestration.multiagent.model.SupervisorExecutionResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 执行结果组装器。
 *
 * <p>用途：统一组装多智能体返回结果，保持不同执行路径输出口径一致。</p>
 */
@Component
public class ExecutionResultAssembler {

    /**
     * 组装最终返回结果。
     */
    public MultiAgentExecutionResult assemble(DagExecutionResult dagResult,
                                              SupervisorExecutionResult supervisorResult,
                                              List<AgentRole> roles,
                                              MultiAgentExecutionMode mode,
                                              String rawRef) {
        MultiAgentExecutionResult result = new MultiAgentExecutionResult();
        result.setMode(mode);
        List<AgentRole> safeRoles = roles == null ? List.of() : roles;
        result.setTeam(safeRoles);
        result.setSummary("team_size=" + safeRoles.size() + ",mode=" + mode.name());
        result.setDagResult(dagResult);
        result.setSupervisorResult(supervisorResult);
        if (dagResult != null) {
            result.setStatus(dagResult.getStatus());
        }
        if (supervisorResult != null) {
            result.setStatus(supervisorResult.getStatus());
        }
        if (StringUtils.hasText(rawRef)) {
            result.setRawRef(rawRef);
        }
        return result;
    }

    /**
     * 组装最终返回结果并输出 Map。
     */
    public Map<String, Object> assembleAsMap(DagExecutionResult dagResult,
                                             SupervisorExecutionResult supervisorResult,
                                             List<AgentRole> roles,
                                             MultiAgentExecutionMode mode,
                                             String rawRef) {
        return assemble(dagResult, supervisorResult, roles, mode, rawRef).toMap();
    }
}
