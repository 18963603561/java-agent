package com.example.agent.orchestration.multiagent.event;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DAG 领域事件发布器。
 *
 * <p>用途：统一 DAG 节点生命周期、等待、背压与重试事件发布。</p>
 */
public class DagEventPublisher {

    private final EventPublishSupport publishSupport;

    /**
     * 构造 DAG 事件发布器。
     */
    public DagEventPublisher(EventPublishSupport publishSupport) {
        this.publishSupport = publishSupport;
    }

    /**
     * 发布 DAG 节点启动事件。
     */
    public void publishDagNodeStarted(TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter,
                                      String nodeId,
                                      String roleId) {
        publishDagNodeStarted(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                null,
                null);
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("roleId", roleId);
        publishSupport.appendDagReplayAnchor(payload, dagRunId, nodeId, attempt, null, null);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.STEP_STARTED,
                payload);
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
        publishDagNodeCompleted(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                producedTopics,
                null,
                null);
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("roleId", roleId);
        payload.put("producedTopics", producedTopics == null ? List.of() : producedTopics);
        publishSupport.appendDagReplayAnchor(payload, dagRunId, nodeId, attempt, null, null);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.STEP_COMPLETED,
                payload);
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
        publishDagNodeFailed(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                reason,
                null,
                null,
                null);
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("roleId", roleId);
        payload.put("reason", reason);
        publishSupport.appendDagReplayAnchor(payload, dagRunId, nodeId, attempt, reasonCode, null);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.STEP_FAILED,
                payload);
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
        publishDagNodeWaiting(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                reason,
                delayMs,
                delayMs,
                1);
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
        publishDagNodeWaiting(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                reason,
                delayMs,
                waitedMs,
                attempt,
                null,
                null);
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("roleId", roleId);
        payload.put("reason", reason);
        payload.put("delayMs", delayMs);
        payload.put("waitedMs", waitedMs);
        payload.put("attempt", attempt);
        publishSupport.appendDagReplayAnchor(payload, dagRunId, nodeId, attempt, reasonCode, null);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.WAITING,
                payload);
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
        publishDagBackpressureApplied(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                reason,
                queueSize,
                capacity,
                0L);
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
        publishDagBackpressureApplied(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                reason,
                queueSize,
                capacity,
                delayMs,
                null,
                null,
                null);
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("reason", reason);
        payload.put("queueSize", queueSize);
        payload.put("capacity", capacity);
        payload.put("delayMs", delayMs);
        publishSupport.appendDagReplayAnchor(payload, dagRunId, nodeId, attempt, reasonCode, null);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.BACKPRESSURE_APPLIED,
                payload);
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
        publishDagNodeRetrying(tenantContext,
                workflowId,
                seqCounter,
                nodeId,
                roleId,
                attempt,
                delayMs,
                reason,
                null,
                null);
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("roleId", roleId);
        payload.put("attempt", attempt);
        payload.put("delayMs", delayMs);
        payload.put("reason", reason);
        publishSupport.appendDagReplayAnchor(payload, dagRunId, nodeId, attempt, reasonCode, null);
        publishSupport.publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.WAITING,
                payload);
    }
}

