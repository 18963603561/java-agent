package com.example.agent.orchestration.multiagent.dag.actor.runtime;

import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagNode;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagPlan;
import com.example.agent.orchestration.multiagent.dag.actor.DagActorRuntime;
import com.example.agent.orchestration.multiagent.dag.actor.DagNodeRuntimeState;
import com.example.agent.orchestration.multiagent.dag.actor.DagSupervisorPolicy;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import com.example.agent.orchestration.multiagent.model.RunStatus;
import com.example.agent.orchestration.multiagent.supervisor.FailurePropagationPolicy;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DAG 运行收尾服务。
 *
 * <p>用途：统一负责运行状态收敛、运行结束事件发布和节点视图组装。</p>
 */
public class DagRunFinalizeService {

    private final MultiAgentEventPublisher eventPublisher;
    private final DagAuditService dagAuditService;
    private final DagSupervisorPolicy dagSupervisorPolicy;

    /**
     * 构造收尾服务。
     */
    public DagRunFinalizeService(MultiAgentEventPublisher eventPublisher,
                                 DagAuditService dagAuditService,
                                 DagSupervisorPolicy dagSupervisorPolicy) {
        this.eventPublisher = eventPublisher;
        this.dagAuditService = dagAuditService;
        this.dagSupervisorPolicy = dagSupervisorPolicy;
    }

    /**
     * 完成 DAG 运行收尾并返回结果。
     */
    public DagActorRuntime.DagActorRuntimeResult finalizeRun(String dagRunId,
                                                             String workflowId,
                                                             TenantContext tenantContext,
                                                             AtomicLong seqCounter,
                                                             DagPlan dagPlan,
                                                             Map<String, DagNodeRuntimeState> stateMap,
                                                             List<String> completedNodes,
                                                             List<String> failures,
                                                             DagRunAuditRecord runRecord) {
        RunStatus status = resolveRunStatus(failures, completedNodes);
        eventPublisher.publishTeamStatus(tenantContext,
                workflowId,
                seqCounter,
                status.code(),
                Map.of("failedCount", failures.size(),
                        "maxFailures", dagSupervisorPolicy.getMaxFailures(),
                        "failurePolicy", dagSupervisorPolicy.getFailurePropagationPolicy().name()));

        dagAuditService.completeRunRecord(runRecord, status.code(), failures, Instant.now());
        dagAuditService.saveRunRecord(runRecord);

        List<Map<String, Object>> nodeViews = buildNodeViews(dagPlan.getNodes(), stateMap);
        return new DagActorRuntime.DagActorRuntimeResult(
                dagRunId,
                status.code(),
                dagPlan.getTopologicalOrder(),
                nodeViews,
                failures);
    }

    /**
     * 计算 DAG 运行状态。
     */
    private RunStatus resolveRunStatus(List<String> failures, List<String> completedNodes) {
        if (failures == null || failures.isEmpty()) {
            return RunStatus.COMPLETED;
        }
        if (dagSupervisorPolicy.getFailurePropagationPolicy() == FailurePropagationPolicy.PARTIAL_SUCCESS
                && completedNodes != null
                && !completedNodes.isEmpty()) {
            return RunStatus.PARTIAL_SUCCESS;
        }
        return RunStatus.FAILED;
    }

    /**
     * 构建节点视图。
     */
    private List<Map<String, Object>> buildNodeViews(List<DagNode> nodes,
                                                     Map<String, DagNodeRuntimeState> stateMap) {
        List<Map<String, Object>> nodeViews = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return nodeViews;
        }
        for (DagNode node : nodes) {
            DagNodeRuntimeState state = stateMap.get(node.getNodeId());
            Map<String, Object> nodeView = new HashMap<>();
            nodeView.put("nodeId", node.getNodeId());
            nodeView.put("roleId", node.getRole() != null ? node.getRole().getRoleId() : null);
            nodeView.put("dependsOn", node.getDependsOn());
            nodeView.put("consumes", node.getConsumes());
            nodeView.put("produces", node.getProduces());
            nodeView.put("status", state != null ? state.getStatus().name() : null);
            nodeView.put("remainingDeps", state != null ? state.getRemainingDependencies() : null);
            nodeViews.add(nodeView);
        }
        nodeViews.sort(Comparator.comparing(view -> String.valueOf(view.get("nodeId"))));
        return nodeViews;
    }
}
