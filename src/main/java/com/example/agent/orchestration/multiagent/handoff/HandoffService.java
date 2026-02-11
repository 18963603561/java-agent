package com.example.agent.orchestration.multiagent.handoff;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 交接服务。
 *
 * <p>用途：封装 handoff 请求校验、记录构建、事件发布与工作区写入流程。</p>
 */
@Service
public class HandoffService {

    private static final Logger log = LoggerFactory.getLogger(HandoffService.class);

    private final WorkspaceSyncService workspaceSyncService;
    private final MultiAgentEventPublisher eventPublisher;
    private final HandoffRepository handoffRepository;
    private final HandoffStateMachine handoffStateMachine;

    public HandoffService(WorkspaceSyncService workspaceSyncService,
                          MultiAgentEventPublisher eventPublisher,
                          HandoffRepository handoffRepository,
                          HandoffStateMachine handoffStateMachine) {
        this.workspaceSyncService = workspaceSyncService;
        this.eventPublisher = eventPublisher;
        this.handoffRepository = handoffRepository;
        this.handoffStateMachine = handoffStateMachine;
    }

    /**
     * 执行交接。
     *
     * @param request 交接请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列
     * @return 交接结果
     */
    public HandoffResult handoff(HandoffRequest request,
                                 TenantContext tenantContext,
                                 String workflowId,
                                 AtomicLong seqCounter) {
        String idempotencyKey = resolveIdempotencyKey(request, workflowId);
        // 幂等逻辑：同一幂等键优先返回已存在结果，避免重复写入
        HandoffRecord existed = handoffRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existed != null) {
            log.info("交接幂等命中, workflowId={}, handoffId={}, idempotencyKey={}, status={}, version={}",
                    workflowId,
                    existed.getHandoffId(),
                    idempotencyKey,
                    existed.getStatus(),
                    existed.getVersion());
            return toResult(existed, existed.getFailureReason() == null ? "idempotent_hit" : existed.getFailureReason());
        }

        HandoffRecord record = createInitialRecord(request, idempotencyKey);
        record = handoffRepository.create(record);
        eventPublisher.publishHandoffRequested(tenantContext, workflowId, seqCounter, request, record);

        if (!isValidRequest(request)) {
            HandoffRecord failed = updateRecordStatus(record,
                    HandoffStatus.FAILED,
                    "handoff_invalid_request",
                    "HANDOFF_INVALID_REQUEST");
            eventPublisher.publishHandoffCompleted(tenantContext, workflowId, seqCounter, request, failed,
                    "handoff_invalid_request");
            log.warn("交接失败, workflowId={}, handoffId={}, reason={}, version={}",
                    workflowId,
                    failed.getHandoffId(),
                    failed.getFailureReason(),
                    failed.getVersion());
            return toResult(failed, failed.getFailureReason());
        }

        HandoffRecord running = updateRecordStatus(record, HandoffStatus.RUNNING, null, null);
        String topic = resolveTopic(request);
        if (StringUtils.hasText(topic)) {
            // 工作区写入：将交接产物作为 topic 条目写入共享工作区
            Map<String, Object> payload = new HashMap<>();
            payload.put("handoffId", running.getHandoffId());
            payload.put("fromAgent", request.getFromAgent());
            payload.put("toAgent", request.getToAgent());
            payload.put("context", request.getContext());
            payload.put("version", running.getVersion());
            workspaceSyncService.append(workflowId, topic, payload);
            // 消息协作：发布发送/接收事件，确保交接过程可观测
            String message = "handoff:" + request.getFromAgent() + "->" + request.getToAgent();
            eventPublisher.publishMessageSent(tenantContext,
                    workflowId,
                    seqCounter,
                    request.getFromAgent(),
                    request.getToAgent(),
                    message,
                    topic);
            eventPublisher.publishMessageReceived(tenantContext,
                    workflowId,
                    seqCounter,
                    request.getToAgent(),
                    message,
                    topic);
            eventPublisher.publishWorkspaceUpdated(tenantContext, workflowId, seqCounter, topic, "handoff");
        }

        HandoffRecord succeeded = updateRecordStatus(running, HandoffStatus.SUCCEEDED, null, null);
        eventPublisher.publishHandoffCompleted(tenantContext, workflowId, seqCounter, request, succeeded, "ok");
        log.info("交接完成, workflowId={}, handoffId={}, fromAgent={}, toAgent={}, version={}",
                workflowId,
                succeeded.getHandoffId(),
                request.getFromAgent(),
                request.getToAgent(),
                succeeded.getVersion());
        return toResult(succeeded, "ok");
    }

    /**
     * 创建交接生命周期记录（不发布事件）。
     *
     * @param request 交接请求
     * @param workflowId 工作流标识
     * @return 已创建记录
     */
    public HandoffRecord createLifecycleRecord(HandoffRequest request, String workflowId) {
        String idempotencyKey = resolveIdempotencyKey(request, workflowId);
        HandoffRecord existed = handoffRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existed != null) {
            return existed;
        }
        HandoffRecord created = handoffRepository.create(createInitialRecord(request, idempotencyKey));
        log.info("交接生命周期记录创建, workflowId={}, handoffId={}, idempotencyKey={}, version={}",
                workflowId,
                created.getHandoffId(),
                idempotencyKey,
                created.getVersion());
        return created;
    }

    /**
     * 将交接记录推进到指定状态。
     *
     * @param current 当前记录
     * @param targetStatus 目标状态
     * @param failureReason 失败原因
     * @param errorCode 错误码
     * @return 更新后记录
     */
    public HandoffRecord transitionRecord(HandoffRecord current,
                                          HandoffStatus targetStatus,
                                          String failureReason,
                                          String errorCode) {
        if (current == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "handoff record 不能为空");
        }
        return updateRecordStatus(current, targetStatus, failureReason, errorCode);
    }

    /**
     * 查询交接记录。
     *
     * @param handoffId 交接标识
     * @return 交接记录
     */
    public HandoffRecord getRecord(String handoffId) {
        return handoffRepository.findById(handoffId)
                .orElseThrow(() -> new ErrorCodeException(HttpStatus.NOT_FOUND, "HANDOFF_NOT_FOUND", "交接记录不存在"));
    }

    /**
     * 等待 consumes 依赖，返回缺失 topic。
     */
    public List<String> awaitConsumes(String workflowId,
                                      List<String> consumes,
                                      TopicDependencyCoordinator dependencyCoordinator) {
        if (dependencyCoordinator == null) {
            return List.of();
        }
        return dependencyCoordinator.awaitDependencies(workflowId, consumes);
    }

    private HandoffRecord createInitialRecord(HandoffRequest request, String idempotencyKey) {
        HandoffRecord record = new HandoffRecord();
        record.setHandoffId(UUID.randomUUID().toString());
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        record.setFromAgent(request != null ? request.getFromAgent() : null);
        record.setToAgent(request != null ? request.getToAgent() : null);
        record.setContext(request != null ? request.getContext() : null);
        record.setStatus(HandoffStatus.PENDING);
        record.setIdempotencyKey(idempotencyKey);
        record.setVersion(0L);
        return record;
    }

    private HandoffRecord updateRecordStatus(HandoffRecord current,
                                             HandoffStatus targetStatus,
                                             String failureReason,
                                             String errorCode) {
        HandoffStatus next = handoffStateMachine.transition(current.getStatus(), targetStatus);
        HandoffRecord toSave = copyRecord(current);
        // 状态推进：统一在持久化前更新状态与错误上下文
        toSave.setStatus(next);
        toSave.setFailureReason(failureReason);
        toSave.setErrorCode(errorCode);
        toSave.setUpdatedAt(Instant.now());
        return handoffRepository.compareAndSet(toSave, current.getVersion());
    }

    private HandoffResult toResult(HandoffRecord record, String reason) {
        HandoffResult result = new HandoffResult();
        result.setHandoffId(record.getHandoffId());
        result.setStatus(record.getStatus());
        result.setReason(reason);
        result.setVersion(record.getVersion());
        return result;
    }

    private HandoffRecord copyRecord(HandoffRecord source) {
        HandoffRecord target = new HandoffRecord();
        target.setHandoffId(source.getHandoffId());
        target.setFromAgent(source.getFromAgent());
        target.setToAgent(source.getToAgent());
        target.setStatus(source.getStatus());
        target.setVersion(source.getVersion());
        target.setIdempotencyKey(source.getIdempotencyKey());
        target.setErrorCode(source.getErrorCode());
        target.setFailureReason(source.getFailureReason());
        target.setContext(source.getContext() == null ? null : new HashMap<>(source.getContext()));
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private String resolveIdempotencyKey(HandoffRequest request, String workflowId) {
        if (request != null && StringUtils.hasText(request.getIdempotencyKey())) {
            return request.getIdempotencyKey().trim();
        }
        if (request != null && StringUtils.hasText(request.getFromAgent()) && StringUtils.hasText(request.getToAgent())) {
            return (workflowId == null ? "workflow-unknown" : workflowId)
                    + ":"
                    + request.getFromAgent().trim()
                    + "->"
                    + request.getToAgent().trim();
        }
        return (workflowId == null ? "workflow-unknown" : workflowId) + ":invalid-request:" + UUID.randomUUID();
    }

    private boolean isValidRequest(HandoffRequest request) {
        return request != null
                && StringUtils.hasText(request.getFromAgent())
                && StringUtils.hasText(request.getToAgent())
                && request.getContext() != null;
    }

    private String resolveTopic(HandoffRequest request) {
        if (request == null || request.getContext() == null) {
            return null;
        }
        Object topic = request.getContext().get("topic");
        return topic == null ? null : String.valueOf(topic);
    }
}
