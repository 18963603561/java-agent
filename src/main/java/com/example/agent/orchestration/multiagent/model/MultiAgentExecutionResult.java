package com.example.agent.orchestration.multiagent.model;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentExecutionMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 多智能体统一执行结果。
 *
 * <p>用途：在业务层提供强类型执行结果，同时支持在边界层转换为 Map 输出。</p>
 */
public class MultiAgentExecutionResult {

    /**
     * 执行模式。
     */
    private MultiAgentExecutionMode mode;

    /**
     * 执行状态。
     */
    private String status;

    /**
     * 团队信息。
     */
    private List<AgentRole> team = List.of();

    /**
     * 摘要信息。
     */
    private String summary;

    /**
     * 模型原始引用。
     */
    private String rawRef;

    /**
     * DAG 结果（仅 DAG 模式有效）。
     */
    private DagExecutionResult dagResult;

    /**
     * Supervisor 结果（仅 SUPERVISOR 模式有效）。
     */
    private SupervisorExecutionResult supervisorResult;

    public MultiAgentExecutionMode getMode() {
        return mode;
    }

    public void setMode(MultiAgentExecutionMode mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<AgentRole> getTeam() {
        return team;
    }

    public void setTeam(List<AgentRole> team) {
        this.team = team == null ? List.of() : List.copyOf(team);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }

    public DagExecutionResult getDagResult() {
        return dagResult;
    }

    public void setDagResult(DagExecutionResult dagResult) {
        this.dagResult = dagResult;
    }

    public SupervisorExecutionResult getSupervisorResult() {
        return supervisorResult;
    }

    public void setSupervisorResult(SupervisorExecutionResult supervisorResult) {
        this.supervisorResult = supervisorResult;
    }

    /**
     * 转换为边界输出 Map。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        if (mode != null) {
            result.put("mode", mode.name());
        }
        result.put("status", status);
        result.put("team", team == null ? List.of() : team);
        result.put("summary", summary);

        if (StringUtils.hasText(rawRef)) {
            result.put("rawRef", rawRef);
            result.put("modelRawRef", rawRef);
            result.put("refs", Map.of("modelRawRef", rawRef));
        }

        if (dagResult != null) {
            result.put("dagRunId", dagResult.getDagRunId());
            result.put("dagOrder", dagResult.getDagOrder());
            result.put("dagNodes", dagResult.getDagNodes());
            if (dagResult.getFailures() != null && !dagResult.getFailures().isEmpty()) {
                result.put("failures", dagResult.getFailures());
            }
        }

        if (supervisorResult != null) {
            result.put("handoffStatuses", supervisorResult.getHandoffStatuses());
            result.put("failedCount", supervisorResult.getFailedCount());
            result.put("maxFailures", supervisorResult.getMaxFailures());
            result.put("failurePolicy", supervisorResult.getFailurePolicy());
            result.put("missingTopics", supervisorResult.getMissingTopics());
            result.put("handoffLifecycleId", supervisorResult.getHandoffLifecycleId());
            result.put("handoffLifecycleStatus", supervisorResult.getHandoffLifecycleStatus());
            result.put("handoffLifecycleVersion", supervisorResult.getHandoffLifecycleVersion());
        }
        return result;
    }
}

