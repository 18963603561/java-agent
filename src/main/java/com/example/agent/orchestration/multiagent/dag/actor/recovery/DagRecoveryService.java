package com.example.agent.orchestration.multiagent.dag.actor.recovery;

import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxDispatcher;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxTransport;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMessageEnvelope;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagRuntimeStateRepository;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeRuntimeSnapshot;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * DAG 恢复服务。
 *
 * <p>用途：在实例重启或异常中断后，重驱待处理消息并校验运行态一致性。</p>
 */
@Service
public class DagRecoveryService {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagRecoveryService.class);

    /**
     * 邮箱分发器。
     */
    private final DagMailboxDispatcher mailboxDispatcher;

    /**
     * 邮箱传输层。
     */
    private final DagMailboxTransport mailboxTransport;

    /**
     * 运行状态仓储。
     */
    private final DagRuntimeStateRepository runtimeStateRepository;

    /**
     * 工作区服务。
     */
    private final WorkspaceSyncService workspaceSyncService;

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    /**
     * 死信仓储。
     */
    private final DagDeadLetterRepository deadLetterRepository;

    public DagRecoveryService(DagMailboxDispatcher mailboxDispatcher,
                              DagMailboxTransport mailboxTransport,
                              DagRuntimeStateRepository runtimeStateRepository,
                              WorkspaceSyncService workspaceSyncService,
                              MetricsPublisher metricsPublisher,
                              DagDeadLetterRepository deadLetterRepository) {
        this.mailboxDispatcher = mailboxDispatcher;
        this.mailboxTransport = mailboxTransport;
        this.runtimeStateRepository = runtimeStateRepository;
        this.workspaceSyncService = workspaceSyncService;
        this.metricsPublisher = metricsPublisher;
        this.deadLetterRepository = deadLetterRepository;
    }

    /**
     * 对指定运行执行恢复。
     */
    public int recoverDagRun(String dagRunId, String workflowId) {
        if (!StringUtils.hasText(dagRunId) || !StringUtils.hasText(workflowId)) {
            return 0;
        }
        // 关键逻辑：记录恢复开始时间，用于输出恢复耗时指标。
        Instant startedAt = Instant.now();
        int recoveredCount = 0;
        // 关键逻辑：先触发消息分发推进，尽快恢复依赖状态。
        recoveredCount += mailboxDispatcher.dispatchDagRun(dagRunId);
        List<DagNodeRuntimeSnapshot> snapshots = runtimeStateRepository.findByDagRun(dagRunId);
        Map<String, List<Map<String, Object>>> workspaceSnapshot = workspaceSyncService.snapshot(workflowId);
        // 关键逻辑：对运行态快照与工作区事实进行比对，识别潜在卡点。
        for (DagNodeRuntimeSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            if ("SUCCEEDED".equalsIgnoreCase(snapshot.getStatus())) {
                continue;
            }
            if (snapshot.getRemainingDependencies() <= 0) {
                continue;
            }
            if (workspaceSnapshot.isEmpty()) {
                continue;
            }
            recoveredCount++;
            metricsPublisher.incrementWithTags("dag.recovery.detected", "nodeId", snapshot.getNodeId());
            log.info("DAG恢复检测到可补偿节点, dagRunId={}, nodeId={}, remainingDependencies={}",
                    dagRunId,
                    snapshot.getNodeId(),
                    snapshot.getRemainingDependencies());
        }
        // 关键逻辑：恢复流程结束后统一记录耗时指标，便于观测恢复性能。
        long recoveryDurationMs = Duration.between(startedAt, Instant.now()).toMillis();
        metricsPublisher.recordSummary("dag.recovery.duration", recoveryDurationMs);
        return recoveredCount;
    }

    /**
     * 重放死信消息。
     */
    public void replayDeadLetter(DagDeadLetterMessage deadLetterMessage) {
        if (deadLetterMessage == null || deadLetterMessage.getEnvelope() == null) {
            return;
        }
        DagMessageEnvelope envelope = deadLetterMessage.getEnvelope();
        // 关键逻辑：重放前重置可用时间与投递计数，确保立即进入消费。
        envelope.setAvailableAtEpochMs(System.currentTimeMillis());
        envelope.setDeliveryAttempt(1);
        mailboxTransport.send(envelope);
        metricsPublisher.increment("dag.recovery.deadletter.replayed");
    }

    /**
     * 按死信标识重放并清理。
     *
     * @param deadLetterId 死信标识
     * @return true 表示重放并清理成功，false 表示未命中死信
     */
    public boolean replayDeadLetterById(String deadLetterId) {
        if (!StringUtils.hasText(deadLetterId)) {
            return false;
        }
        // 关键逻辑：先查死信再重放，避免无效重放请求导致状态不一致。
        return deadLetterRepository.findById(deadLetterId)
                .map(message -> {
                    replayDeadLetter(message);
                    // 关键逻辑：重放成功后删除死信，防止重复重放。
                    deadLetterRepository.deleteById(deadLetterId);
                    log.info("DAG死信重放完成, deadLetterId={}, dagRunId={}, nodeId={}",
                            deadLetterId,
                            message.getDagRunId(),
                            message.getNodeId());
                    return true;
                })
                .orElse(false);
    }
}
