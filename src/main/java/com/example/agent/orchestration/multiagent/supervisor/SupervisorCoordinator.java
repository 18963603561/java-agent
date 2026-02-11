package com.example.agent.orchestration.multiagent.supervisor;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.model.SupervisorExecutionResult;
import com.example.agent.orchestration.multiagent.model.ReasonCode;
import com.example.agent.orchestration.multiagent.model.RunStatus;
import com.example.agent.orchestration.multiagent.handoff.HandoffRequest;
import com.example.agent.orchestration.multiagent.handoff.HandoffRecord;
import com.example.agent.orchestration.multiagent.handoff.HandoffResult;
import com.example.agent.orchestration.multiagent.handoff.HandoffService;
import com.example.agent.orchestration.multiagent.handoff.HandoffStatus;
import com.example.agent.orchestration.multiagent.handoff.TopicDependencyCoordinator;
import com.example.agent.security.auth.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Supervisor 协调器。
 *
 * <p>用途：处理多角色顺序协调、依赖等待、handoff 与失败传播。</p>
 */
@Service
public class SupervisorCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SupervisorCoordinator.class);

    private final SupervisorPolicy supervisorPolicy;
    private final HandoffService handoffService;
    private final TopicDependencyCoordinator dependencyCoordinator;
    private final MultiAgentEventPublisher eventPublisher;

    public SupervisorCoordinator(SupervisorPolicy supervisorPolicy,
                                 HandoffService handoffService,
                                 TopicDependencyCoordinator dependencyCoordinator,
                                 MultiAgentEventPublisher eventPublisher) {
        this.supervisorPolicy = supervisorPolicy;
        this.handoffService = handoffService;
        this.dependencyCoordinator = dependencyCoordinator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 执行 Supervisor 协调路径。
     *
     * @param workflowId 工作流标识
     * @param tenantContext 租户上下文
     * @param seqCounter 事件序列
     * @param roles 角色列表
     * @param consumes 依赖 topic
     * @param produces 产出 topic
     * @return 协调结果
     */
    public SupervisorExecutionResult coordinate(String workflowId,
                                                TenantContext tenantContext,
                                                AtomicLong seqCounter,
                                                List<AgentRole> roles,
                                                List<String> consumes,
                                                List<String> produces) {
        List<AgentRole> safeRoles = roles == null ? List.of() : roles;
        int maxFailures = supervisorPolicy.getMaxFailures();
        FailurePropagationPolicy policy = supervisorPolicy.getFailurePropagationPolicy();
        int failed = 0;
        boolean terminatedByFailurePolicy = false;

        HandoffRecord lifecycleRecord = createLifecycleRecord(workflowId);
        lifecycleRecord = handoffService.transitionRecord(lifecycleRecord,
                HandoffStatus.WAITING,
                null,
                null);

        List<String> missingTopics = handoffService.awaitConsumes(workflowId, consumes, dependencyCoordinator);
        if (!missingTopics.isEmpty()) {
            lifecycleRecord = handoffService.transitionRecord(lifecycleRecord,
                    HandoffStatus.FAILED,
                    "handoff_dependency_timeout",
                    ReasonCode.HANDOFF_DEPENDENCY_TIMEOUT.code());
            eventPublisher.publishTeamStatus(tenantContext,
                    workflowId,
                    seqCounter,
                    RunStatus.WAITING_DEPENDENCIES.code(),
                    Map.of(
                            "missingTopics", missingTopics,
                            "handoffId", lifecycleRecord.getHandoffId(),
                            "handoffStatus", lifecycleRecord.getStatus().name(),
                            "handoffVersion", lifecycleRecord.getVersion()));
            failed++;
            if (policy == FailurePropagationPolicy.FAIL_FAST || failed >= maxFailures) {
                terminatedByFailurePolicy = true;
                log.warn("Supervisor依赖未满足触发终止, workflowId={}, failed={}, maxFailures={}, policy={}, missingTopics={}",
                        workflowId,
                        failed,
                        maxFailures,
                        policy,
                        missingTopics);
            }
        } else {
            lifecycleRecord = handoffService.transitionRecord(lifecycleRecord,
                    HandoffStatus.RUNNING,
                    null,
                    null);
        }

        List<String> handoffStatuses = new ArrayList<>();
        for (int index = 0; index < safeRoles.size() - 1 && !terminatedByFailurePolicy; index++) {
            AgentRole from = safeRoles.get(index);
            AgentRole to = safeRoles.get(index + 1);
            HandoffRequest request = new HandoffRequest();
            request.setFromAgent(from.getRoleId());
            request.setToAgent(to.getRoleId());
            Map<String, Object> context = new HashMap<>();
            context.put("workflowId", workflowId);
            context.put("fromRole", from.getName());
            context.put("toRole", to.getName());
            if (produces != null && !produces.isEmpty()) {
                context.put("topic", produces.get(0));
            }
            request.setContext(context);
            request.setPermissions(Map.of("mode", "inherited"));

            HandoffResult handoffResult = handoffService.handoff(request, tenantContext, workflowId, seqCounter);
            handoffStatuses.add(handoffResult.getStatus() != null ? handoffResult.getStatus().name() : null);
            if (handoffResult.getStatus() != HandoffStatus.SUCCEEDED) {
                failed++;
                if (policy == FailurePropagationPolicy.FAIL_FAST || failed >= maxFailures) {
                    terminatedByFailurePolicy = true;
                    log.warn("Supervisor中止, workflowId={}, failed={}, maxFailures={}, policy={}",
                            workflowId,
                            failed,
                            maxFailures,
                            policy);
                }
            }
        }

        RunStatus status;
        if (terminatedByFailurePolicy) {
            status = RunStatus.FAILED;
            if (lifecycleRecord.getStatus() != HandoffStatus.FAILED) {
                lifecycleRecord = handoffService.transitionRecord(lifecycleRecord,
                        HandoffStatus.FAILED,
                        "handoff_supervisor_failed",
                        ReasonCode.HANDOFF_SUPERVISOR_FAILED.code());
            }
        } else if (failed > 0) {
            status = RunStatus.PARTIAL_SUCCESS;
            if (!lifecycleRecord.getStatus().isTerminal()) {
                lifecycleRecord = handoffService.transitionRecord(lifecycleRecord,
                        HandoffStatus.SUCCEEDED,
                        null,
                        null);
            }
        } else {
            status = RunStatus.COMPLETED;
            if (!lifecycleRecord.getStatus().isTerminal()) {
                lifecycleRecord = handoffService.transitionRecord(lifecycleRecord,
                        HandoffStatus.SUCCEEDED,
                        null,
                        null);
            }
        }

        eventPublisher.publishTeamStatus(tenantContext,
                workflowId,
                seqCounter,
                status.code(),
                Map.of(
                        "failedCount", failed,
                        "maxFailures", maxFailures,
                        "failurePolicy", policy.name(),
                        "handoffId", lifecycleRecord.getHandoffId(),
                        "handoffStatus", lifecycleRecord.getStatus().name(),
                        "handoffVersion", lifecycleRecord.getVersion()));

        SupervisorExecutionResult result = new SupervisorExecutionResult();
        result.setStatus(status.code());
        result.setTeam(safeRoles);
        result.setHandoffStatuses(handoffStatuses);
        result.setFailedCount(failed);
        result.setMaxFailures(maxFailures);
        result.setFailurePolicy(policy.name());
        result.setMissingTopics(missingTopics);
        result.setHandoffLifecycleId(lifecycleRecord.getHandoffId());
        result.setHandoffLifecycleStatus(lifecycleRecord.getStatus().name());
        result.setHandoffLifecycleVersion(lifecycleRecord.getVersion());
        return result;
    }

    private HandoffRecord createLifecycleRecord(String workflowId) {
        HandoffRequest request = new HandoffRequest();
        request.setFromAgent("supervisor");
        request.setToAgent("team");
        request.setContext(Map.of("workflowId", workflowId, "topic", "supervisor-lifecycle"));
        request.setIdempotencyKey("supervisor-lifecycle:" + workflowId + ":" + System.nanoTime());
        return handoffService.createLifecycleRecord(request, workflowId);
    }
}
