package com.example.agent.orchestration.multiagent.model;

import com.example.agent.orchestration.multiagent.AgentRole;
import java.util.List;

/**
 * Supervisor 执行结果。
 *
 * <p>用途：显式表达监督模式输出契约，提升编译期约束能力。</p>
 */
public class SupervisorExecutionResult {

    /**
     * 执行状态。
     */
    private String status;

    /**
     * 角色团队。
     */
    private List<AgentRole> team = List.of();

    /**
     * 各次交接状态。
     */
    private List<String> handoffStatuses = List.of();

    /**
     * 失败数量。
     */
    private int failedCount;

    /**
     * 最大失败阈值。
     */
    private int maxFailures;

    /**
     * 失败传播策略。
     */
    private String failurePolicy;

    /**
     * 缺失主题。
     */
    private List<String> missingTopics = List.of();

    /**
     * 交接生命周期标识。
     */
    private String handoffLifecycleId;

    /**
     * 交接生命周期状态。
     */
    private String handoffLifecycleStatus;

    /**
     * 交接生命周期版本。
     */
    private long handoffLifecycleVersion;

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

    public List<String> getHandoffStatuses() {
        return handoffStatuses;
    }

    public void setHandoffStatuses(List<String> handoffStatuses) {
        this.handoffStatuses = handoffStatuses == null ? List.of() : List.copyOf(handoffStatuses);
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public String getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(String failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    public List<String> getMissingTopics() {
        return missingTopics;
    }

    public void setMissingTopics(List<String> missingTopics) {
        this.missingTopics = missingTopics == null ? List.of() : List.copyOf(missingTopics);
    }

    public String getHandoffLifecycleId() {
        return handoffLifecycleId;
    }

    public void setHandoffLifecycleId(String handoffLifecycleId) {
        this.handoffLifecycleId = handoffLifecycleId;
    }

    public String getHandoffLifecycleStatus() {
        return handoffLifecycleStatus;
    }

    public void setHandoffLifecycleStatus(String handoffLifecycleStatus) {
        this.handoffLifecycleStatus = handoffLifecycleStatus;
    }

    public long getHandoffLifecycleVersion() {
        return handoffLifecycleVersion;
    }

    public void setHandoffLifecycleVersion(long handoffLifecycleVersion) {
        this.handoffLifecycleVersion = handoffLifecycleVersion;
    }
}

