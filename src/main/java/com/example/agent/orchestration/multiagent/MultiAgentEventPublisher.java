package com.example.agent.orchestration.multiagent;

import com.example.agent.orchestration.multiagent.event.DagEventPublisher;
import com.example.agent.orchestration.multiagent.event.EventPublishSupport;
import com.example.agent.orchestration.multiagent.event.HandoffEventPublisher;
import com.example.agent.orchestration.multiagent.event.TeamEventPublisher;
import com.example.agent.orchestration.multiagent.handoff.HandoffRecord;
import com.example.agent.orchestration.multiagent.handoff.HandoffRequest;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.sse.EventStreamService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 多智能体事件发布门面。
 *
 * <p>用途：作为统一入口委派到团队域、handoff 域与 DAG 域发布器，
 * 避免所有事件模板集中在单一大类。</p>
 */
@Component
public class MultiAgentEventPublisher {

    private final TeamEventPublisher teamEventPublisher;
    private final HandoffEventPublisher handoffEventPublisher;
    private final DagEventPublisher dagEventPublisher;

    /**
     * 构造事件门面。
     */
    public MultiAgentEventPublisher(ApplicationEventPublisher eventPublisher,
                                    EventStreamService eventStreamService) {
        EventPublishSupport publishSupport = new EventPublishSupport(eventPublisher, eventStreamService);
        this.teamEventPublisher = new TeamEventPublisher(publishSupport);
        this.handoffEventPublisher = new HandoffEventPublisher(publishSupport);
        this.dagEventPublisher = new DagEventPublisher(publishSupport);
    }

    /**
     * 发布团队事件。
     */
    public void publishTeamEvents(TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  List<AgentRole> roles) {
        teamEventPublisher.publishTeamEvents(tenantContext, workflowId, seqCounter, roles);
    }

    /**
     * 发布 handoff 请求事件。
     */
    public void publishHandoffRequested(TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        HandoffRequest request,
                                        HandoffRecord record) {
        handoffEventPublisher.publishHandoffRequested(tenantContext,
                workflowId,
                seqCounter,
                request,
                record);
    }

    /**
     * 发布 handoff 完成事件。
     */
    public void publishHandoffCompleted(TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        HandoffRequest request,
                                        HandoffRecord record,
                                        String reason) {
        handoffEventPublisher.publishHandoffCompleted(tenantContext,
                workflowId,
                seqCounter,
                request,
                record,
                reason);
    }

    /**
     * 发布消息发送事件。
     */
    public void publishMessageSent(TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   String fromRoleId,
                                   String toRoleId,
                                   String message,
                                   String topic) {
        handoffEventPublisher.publishMessageSent(tenantContext,
                workflowId,
                seqCounter,
                fromRoleId,
                toRoleId,
                message,
                topic);
    }

    /**
     * 发布消息接收事件。
     */
    public void publishMessageReceived(TenantContext tenantContext,
                                       String workflowId,
                                       AtomicLong seqCounter,
                                       String roleId,
                                       String message,
                                       String topic) {
        handoffEventPublisher.publishMessageReceived(tenantContext,
                workflowId,
                seqCounter,
                roleId,
                message,
                topic);
    }

    /**
     * 发布工作区更新事件。
     */
    public void publishWorkspaceUpdated(TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        String topic,
                                        String source) {
        handoffEventPublisher.publishWorkspaceUpdated(tenantContext,
                workflowId,
                seqCounter,
                topic,
                source);
    }

    /**
     * 发布 DAG 节点启动事件。
     */
    public void publishDagNodeStarted(TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter,
                                      String nodeId,
                                      String roleId) {
        dagEventPublisher.publishDagNodeStarted(tenantContext, workflowId, seqCounter, nodeId, roleId);
    }

    /**
     * 发布 DAG 节点启动事件（携带回放锚点）。
     */
    public void publishDagNodeStarted(TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter,
                                      String nodeId,
                                      String roleId,
                                      String dagRunId,
                                      Integer attempt) {
        dagEventPublisher.publishDagNodeStarted(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                dagRunId,
                attempt);
    }

    /**
     * 发布 DAG 节点完成事件。
     */
    public void publishDagNodeCompleted(TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        String nodeId,
                                        String roleId,
                                        List<String> producedTopics) {
        dagEventPublisher.publishDagNodeCompleted(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                producedTopics);
    }

    /**
     * 发布 DAG 节点完成事件（携带回放锚点）。
     */
    public void publishDagNodeCompleted(TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        String nodeId,
                                        String roleId,
                                        List<String> producedTopics,
                                        String dagRunId,
                                        Integer attempt) {
        dagEventPublisher.publishDagNodeCompleted(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                producedTopics,
                dagRunId,
                attempt);
    }

    /**
     * 发布 DAG 节点失败事件。
     */
    public void publishDagNodeFailed(TenantContext tenantContext,
                                     String workflowId,
                                     AtomicLong seqCounter,
                                     String nodeId,
                                     String roleId,
                                     String reason) {
        dagEventPublisher.publishDagNodeFailed(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                reason);
    }

    /**
     * 发布 DAG 节点失败事件（携带回放锚点）。
     */
    public void publishDagNodeFailed(TenantContext tenantContext,
                                     String workflowId,
                                     AtomicLong seqCounter,
                                     String nodeId,
                                     String roleId,
                                     String reason,
                                     String dagRunId,
                                     Integer attempt,
                                     String reasonCode) {
        dagEventPublisher.publishDagNodeFailed(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                reason,
                dagRunId,
                attempt,
                reasonCode);
    }

    /**
     * 发布 DAG 节点等待事件。
     */
    public void publishDagNodeWaiting(TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter,
                                      String nodeId,
                                      String roleId,
                                      String reason,
                                      long delayMs) {
        dagEventPublisher.publishDagNodeWaiting(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                reason,
                delayMs);
    }

    /**
     * 发布 DAG 节点等待事件（扩展字段版本）。
     */
    public void publishDagNodeWaiting(TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter,
                                      String nodeId,
                                      String roleId,
                                      String reason,
                                      long delayMs,
                                      long waitedMs,
                                      int attempt) {
        dagEventPublisher.publishDagNodeWaiting(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                reason,
                delayMs,
                waitedMs,
                attempt);
    }

    /**
     * 发布 DAG 节点等待事件（携带回放锚点）。
     */
    public void publishDagNodeWaiting(TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter,
                                      String nodeId,
                                      String roleId,
                                      String reason,
                                      long delayMs,
                                      long waitedMs,
                                      int attempt,
                                      String dagRunId,
                                      String reasonCode) {
        dagEventPublisher.publishDagNodeWaiting(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                reason,
                delayMs,
                waitedMs,
                attempt,
                dagRunId,
                reasonCode);
    }

    /**
     * 发布 DAG 背压事件。
     */
    public void publishDagBackpressureApplied(TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter,
                                              String nodeId,
                                              String reason,
                                              int queueSize,
                                              int capacity) {
        dagEventPublisher.publishDagBackpressureApplied(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                reason,
                queueSize,
                capacity);
    }

    /**
     * 发布 DAG 背压事件（扩展字段版本）。
     */
    public void publishDagBackpressureApplied(TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter,
                                              String nodeId,
                                              String reason,
                                              int queueSize,
                                              int capacity,
                                              long delayMs) {
        dagEventPublisher.publishDagBackpressureApplied(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                reason,
                queueSize,
                capacity,
                delayMs);
    }

    /**
     * 发布 DAG 背压事件（携带回放锚点）。
     */
    public void publishDagBackpressureApplied(TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter,
                                              String nodeId,
                                              String reason,
                                              int queueSize,
                                              int capacity,
                                              long delayMs,
                                              String dagRunId,
                                              Integer attempt,
                                              String reasonCode) {
        dagEventPublisher.publishDagBackpressureApplied(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                reason,
                queueSize,
                capacity,
                delayMs,
                dagRunId,
                attempt,
                reasonCode);
    }

    /**
     * 发布 DAG 节点重试事件。
     */
    public void publishDagNodeRetrying(TenantContext tenantContext,
                                       String workflowId,
                                       AtomicLong seqCounter,
                                       String nodeId,
                                       String roleId,
                                       int attempt,
                                       long delayMs,
                                       String reason) {
        dagEventPublisher.publishDagNodeRetrying(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                attempt,
                delayMs,
                reason);
    }

    /**
     * 发布 DAG 节点重试事件（携带回放锚点）。
     */
    public void publishDagNodeRetrying(TenantContext tenantContext,
                                       String workflowId,
                                       AtomicLong seqCounter,
                                       String nodeId,
                                       String roleId,
                                       int attempt,
                                       long delayMs,
                                       String reason,
                                       String dagRunId,
                                       String reasonCode) {
        dagEventPublisher.publishDagNodeRetrying(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                attempt,
                delayMs,
                reason,
                dagRunId,
                reasonCode);
    }

    /**
     * 发布团队状态事件。
     */
    public void publishTeamStatus(TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  String status,
                                  Map<String, Object> details) {
        teamEventPublisher.publishTeamStatus(tenantContext, workflowId, seqCounter, status, details);
    }
}

