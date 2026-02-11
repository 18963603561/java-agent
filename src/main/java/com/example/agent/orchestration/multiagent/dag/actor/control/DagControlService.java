package com.example.agent.orchestration.multiagent.dag.actor.control;

import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagDistributedProperties;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxDispatcher;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagShardRouter;
import com.example.agent.orchestration.multiagent.dag.actor.recovery.DagDeadLetterMessage;
import com.example.agent.orchestration.multiagent.dag.actor.recovery.DagRecoveryService;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeLeaseService;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagDeadLetterRepository;
import com.example.agent.orchestration.multiagent.model.RunStatus;
import com.example.agent.orchestration.multiagent.observability.MultiAgentEventKeys;
import com.example.agent.orchestration.multiagent.observability.MultiAgentMetricKeys;
import com.example.agent.orchestration.multiagent.observability.MultiAgentTagKeys;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * DAG 控制面服务。
 * <p>用途：提供 pause、resume、rebalance、recover、deadletter-replay 等控制命令统一入口。</p>
 * <p>输入输出：输入为控制命令与租户上下文，输出为控制结果，包含幂等命中状态。</p>
 */
@Service
public class DagControlService {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagControlService.class);

    /**
     * 控制命令状态。
     */
    private final Map<String, String> commandState = new ConcurrentHashMap<>();

    /**
     * 控制命令幂等索引。
     */
    private final Map<String, DagControlResult> idempotencyIndex = new ConcurrentHashMap<>();

    /**
     * DAG 分发器。
     */
    private final DagMailboxDispatcher dagMailboxDispatcher;

    /**
     * DAG 恢复服务。
     */
    private final DagRecoveryService dagRecoveryService;

    /**
     * DAG 分片路由器。
     */
    private final DagShardRouter dagShardRouter;

    /**
     * 分布式配置。
     */
    private final DagDistributedProperties dagDistributedProperties;

    /**
     * 租约服务。
     */
    private final DagNodeLeaseService dagNodeLeaseService;

    /**
     * 死信仓储。
     */
    private final DagDeadLetterRepository deadLetterRepository;

    /**
     * 事件发布器。
     */
    private final MultiAgentEventPublisher eventPublisher;

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    public DagControlService(DagMailboxDispatcher dagMailboxDispatcher,
                             DagRecoveryService dagRecoveryService,
                             DagShardRouter dagShardRouter,
                             DagDistributedProperties dagDistributedProperties,
                             DagNodeLeaseService dagNodeLeaseService,
                             DagDeadLetterRepository deadLetterRepository,
                             MultiAgentEventPublisher eventPublisher,
                             MetricsPublisher metricsPublisher) {
        this.dagMailboxDispatcher = dagMailboxDispatcher;
        this.dagRecoveryService = dagRecoveryService;
        this.dagShardRouter = dagShardRouter;
        this.dagDistributedProperties = dagDistributedProperties;
        this.dagNodeLeaseService = dagNodeLeaseService;
        this.deadLetterRepository = deadLetterRepository;
        this.eventPublisher = eventPublisher;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 执行控制命令。
     */
    public DagControlResult execute(DagControlCommand command, TenantContext tenantContext, AtomicLong seqCounter) {
        DagControlResult invalidResult = validateCommand(command);
        if (invalidResult != null) {
            return invalidResult;
        }
        String idempotencyKey = command.getIdempotencyKey();
        // 关键逻辑：同 idempotencyKey 命中时直接返回历史结果，确保控制命令幂等。
        if (StringUtils.hasText(idempotencyKey)) {
            DagControlResult existing = idempotencyIndex.get(idempotencyKey);
            if (existing != null) {
                DagControlResult deduplicatedResult = copyResult(existing);
                deduplicatedResult.setDeduplicated(true);
                return deduplicatedResult;
            }
        }

        ControlCommandType commandType = ControlCommandType.parse(command.getCommand());
        String normalizedCommand = commandType == null ? normalizeCommand(command.getCommand()) : commandType.name();
        DagControlResult result;
        try {
            // 关键逻辑：按命令类型分发到对应控制动作，统一成功/失败输出。
            if (commandType == null) {
                result = buildFailedResult(command,
                        RunStatus.UNSUPPORTED,
                        "不支持的控制命令: " + normalizedCommand);
            } else {
                result = switch (commandType) {
                    case PAUSE -> doPause(command);
                    case RESUME -> doResume(command);
                    case REBALANCE -> doRebalance(command);
                    case RECOVER -> doRecover(command);
                    case REPLAY_DEADLETTER -> doReplayDeadLetter(command);
                };
            }
        } catch (Exception exception) {
            // 关键逻辑：控制命令异常必须输出上下文并上报失败指标。
            log.error("DAG控制命令执行异常, workflowId={}, dagRunId={}, command={}",
                    command.getWorkflowId(),
                    command.getDagRunId(),
                    normalizedCommand,
                    exception);
            metricsPublisher.incrementWithTags(MultiAgentMetricKeys.DAG_CONTROL_FAILED,
                    MultiAgentTagKeys.COMMAND,
                    normalizedCommand);
            result = buildFailedResult(command, RunStatus.ERROR, exception.getMessage());
        }

        publishControlEvent(command, tenantContext, seqCounter, result);
        if (StringUtils.hasText(idempotencyKey)) {
            idempotencyIndex.put(idempotencyKey, copyResult(result));
        }
        if (RunStatus.SUCCESS.code().equalsIgnoreCase(result.getStatus())) {
            metricsPublisher.incrementWithTags(MultiAgentMetricKeys.DAG_CONTROL_SUCCESS,
                    MultiAgentTagKeys.COMMAND,
                    normalizedCommand);
        }
        return result;
    }

    /**
     * 查询运行状态。
     */
    public String queryRunState(String workflowId, String dagRunId) {
        return commandState.getOrDefault(buildStateKey(workflowId, dagRunId), RunStatus.RUNNING.code());
    }

    /**
     * 查询待处理消息数。
     */
    public int queryPendingCount(String dagRunId) {
        List<?> pending = dagMailboxDispatcher.listPending(dagRunId);
        return pending == null ? 0 : pending.size();
    }

    /**
     * 查询死信消息。
     */
    public List<DagDeadLetterMessage> queryDeadLetters(String dagRunId) {
        if (!StringUtils.hasText(dagRunId)) {
            return List.of();
        }
        return deadLetterRepository.findByDagRun(dagRunId);
    }

    /**
     * 查询租约归属。
     */
    public boolean isLeaseOwner(String dagRunId, String nodeId) {
        return dagNodeLeaseService.isOwner(dagRunId, nodeId, dagDistributedProperties.getInstanceId());
    }

    /**
     * 查询分片归属。
     */
    public String queryShardOwner(String tenantId, String workflowId, String nodeId) {
        return dagShardRouter.assign(tenantId,
                workflowId,
                nodeId,
                dagDistributedProperties.resolveActiveInstancesOrThrow("control_query_shard_owner")).getInstanceId();
    }

    /**
     * 获取当前实例列表。
     */
    public List<String> queryActiveInstances() {
        List<String> instances = dagDistributedProperties.resolveActiveInstancesOrThrow("control_query_active_instances");
        return Collections.unmodifiableList(instances);
    }

    /**
     * 获取处理器摘要。
     */
    public Map<String, Integer> queryHandlerSummary() {
        Map<String, Integer> summary = dagMailboxDispatcher.handlerSummary();
        return summary == null ? Map.of() : summary;
    }

    /**
     * 校验请求。
     */
    private DagControlResult validateCommand(DagControlCommand command) {
        if (command == null) {
            return buildInvalidResult(null, null, null, "请求不能为空");
        }
        if (!StringUtils.hasText(command.getWorkflowId())) {
            return buildInvalidResult(command.getWorkflowId(), command.getDagRunId(), command.getCommand(), "workflowId不能为空");
        }
        if (!StringUtils.hasText(command.getDagRunId())) {
            return buildInvalidResult(command.getWorkflowId(), command.getDagRunId(), command.getCommand(), "dagRunId不能为空");
        }
        if (!StringUtils.hasText(command.getCommand())) {
            return buildInvalidResult(command.getWorkflowId(), command.getDagRunId(), command.getCommand(), "command不能为空");
        }
        return null;
    }

    /**
     * 执行暂停。
     */
    private DagControlResult doPause(DagControlCommand command) {
        commandState.put(buildStateKey(command.getWorkflowId(), command.getDagRunId()), RunStatus.PAUSED.code());
        return buildSuccessResult(command, RunStatus.PAUSED, "DAG运行已暂停");
    }

    /**
     * 执行恢复运行。
     */
    private DagControlResult doResume(DagControlCommand command) {
        commandState.put(buildStateKey(command.getWorkflowId(), command.getDagRunId()), RunStatus.RUNNING.code());
        int dispatched = dagMailboxDispatcher.dispatchDagRun(command.getDagRunId());
        metricsPublisher.recordSummary(MultiAgentMetricKeys.DAG_CONTROL_RESUME_DISPATCHED, dispatched);
        return buildSuccessResult(command, RunStatus.RUNNING, "DAG运行已恢复, dispatched=" + dispatched);
    }

    /**
     * 执行重平衡。
     */
    private DagControlResult doRebalance(DagControlCommand command) {
        int pendingCount = queryPendingCount(command.getDagRunId());
        metricsPublisher.recordSummary(MultiAgentMetricKeys.DAG_CONTROL_REBALANCE_PENDING, pendingCount);
        return buildSuccessResult(command,
                RunStatus.RUNNING,
                "重平衡已触发, pendingCount=" + pendingCount + ", instanceCount=" + queryActiveInstances().size());
    }

    /**
     * 执行恢复。
     */
    private DagControlResult doRecover(DagControlCommand command) {
        int recoveredCount = dagRecoveryService.recoverDagRun(command.getDagRunId(), command.getWorkflowId());
        return buildSuccessResult(command, RunStatus.RUNNING, "恢复完成, recoveredCount=" + recoveredCount);
    }

    /**
     * 执行死信重放。
     */
    private DagControlResult doReplayDeadLetter(DagControlCommand command) {
        // 关键逻辑：复用 idempotencyKey 作为 deadLetterId，降低 API 变更面。
        if (!StringUtils.hasText(command.getIdempotencyKey())) {
            return buildFailedResult(command,
                    RunStatus.INVALID_ARGUMENT,
                    "重放死信需提供 idempotencyKey 作为 deadLetterId");
        }
        boolean replayed = dagRecoveryService.replayDeadLetterById(command.getIdempotencyKey());
        if (!replayed) {
            return buildFailedResult(command,
                    RunStatus.NOT_FOUND,
                    "死信不存在: " + command.getIdempotencyKey());
        }
        return buildSuccessResult(command, RunStatus.RUNNING, "死信重放完成");
    }

    /**
     * 构建失败结果。
     */
    private DagControlResult buildFailedResult(DagControlCommand command, RunStatus status, String message) {
        DagControlResult result = new DagControlResult();
        result.setWorkflowId(command == null ? null : command.getWorkflowId());
        result.setDagRunId(command == null ? null : command.getDagRunId());
        result.setCommand(command == null ? null : normalizeCommand(command.getCommand()));
        result.setStatus(status.code());
        result.setMessage(message);
        result.setExecutedAt(Instant.now());
        result.setDeduplicated(false);
        return result;
    }

    /**
     * 构建无效请求结果。
     */
    private DagControlResult buildInvalidResult(String workflowId, String dagRunId, String command, String message) {
        DagControlResult result = new DagControlResult();
        result.setWorkflowId(workflowId);
        result.setDagRunId(dagRunId);
        result.setCommand(normalizeCommand(command));
        result.setStatus(RunStatus.INVALID_ARGUMENT.code());
        result.setMessage(message);
        result.setExecutedAt(Instant.now());
        result.setDeduplicated(false);
        return result;
    }

    /**
     * 构建成功结果。
     */
    private DagControlResult buildSuccessResult(DagControlCommand command, RunStatus state, String message) {
        DagControlResult result = new DagControlResult();
        result.setWorkflowId(command.getWorkflowId());
        result.setDagRunId(command.getDagRunId());
        result.setCommand(normalizeCommand(command.getCommand()));
        result.setStatus(RunStatus.SUCCESS.code());
        result.setMessage(message);
        result.setExecutedAt(Instant.now());
        result.setDeduplicated(false);
        commandState.put(buildStateKey(command.getWorkflowId(), command.getDagRunId()), state.code());
        return result;
    }

    /**
     * 发布控制事件。
     */
    private void publishControlEvent(DagControlCommand command,
                                     TenantContext tenantContext,
                                     AtomicLong seqCounter,
                                     DagControlResult result) {
        if (command == null || result == null || eventPublisher == null) {
            return;
        }
        Map<String, Object> details = new HashMap<>();
        details.put(MultiAgentEventKeys.DAG_RUN_ID, result.getDagRunId());
        details.put(MultiAgentTagKeys.COMMAND, result.getCommand());
        details.put(MultiAgentEventKeys.STATUS, result.getStatus());
        details.put(MultiAgentEventKeys.MESSAGE, result.getMessage());
        details.put("deduplicated", result.isDeduplicated());
        // 关键逻辑：通过 TEAM_STATUS 汇总控制面动作结果，便于统一观测。
        eventPublisher.publishTeamStatus(tenantContext,
                command.getWorkflowId(),
                seqCounter,
                RunStatus.DAG_CONTROL.code(),
                details);
    }

    /**
     * 复制控制结果。
     */
    private DagControlResult copyResult(DagControlResult source) {
        DagControlResult target = new DagControlResult();
        target.setWorkflowId(source.getWorkflowId());
        target.setDagRunId(source.getDagRunId());
        target.setCommand(source.getCommand());
        target.setStatus(source.getStatus());
        target.setMessage(source.getMessage());
        target.setExecutedAt(source.getExecutedAt());
        target.setDeduplicated(source.isDeduplicated());
        return target;
    }

    /**
     * 归一化命令。
     */
    private String normalizeCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return "";
        }
        return command.trim().toUpperCase().replace('-', '_');
    }

    /**
     * 构建状态索引键。
     */
    private String buildStateKey(String workflowId, String dagRunId) {
        return String.valueOf(workflowId) + ":" + String.valueOf(dagRunId);
    }
}
