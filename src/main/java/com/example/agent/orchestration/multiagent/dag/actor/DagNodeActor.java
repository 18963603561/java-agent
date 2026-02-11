package com.example.agent.orchestration.multiagent.dag.actor;

import com.example.agent.orchestration.multiagent.dag.DagNode;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagDistributedProperties;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagMessageDedupRepository;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAG 节点 Actor。
 *
 * <p>用途：串行消费本节点消息，并在依赖满足后触发执行。</p>
 * <p>输入输出：输入为 mailbox 中的消息，输出为节点执行回调与后续消息派发。</p>
 * <p>边界条件：同一消息仅处理一次，重复消息仅记录调试日志。</p>
 */
public class DagNodeActor {

    private static final Logger log = LoggerFactory.getLogger(DagNodeActor.class);

    private final DagNode dagNode;
    private final DagMailbox mailbox;
    private final DagNodeRuntimeState runtimeState;
    private final Runnable executeNodeTask;
    private final Consumer<String> dependencySatisfiedNotifier;
    private final String dagRunId;
    private final DagMessageDedupRepository dedupRepository;
    private final DagDistributedProperties distributedProperties;

    public DagNodeActor(DagNode dagNode,
                        DagMailbox mailbox,
                        DagNodeRuntimeState runtimeState,
                        Runnable executeNodeTask,
                        Consumer<String> dependencySatisfiedNotifier) {
        this(dagNode,
                mailbox,
                runtimeState,
                executeNodeTask,
                dependencySatisfiedNotifier,
                "",
                null,
                null);
    }

    /**
     * 全量构造器。
     */
    public DagNodeActor(DagNode dagNode,
                        DagMailbox mailbox,
                        DagNodeRuntimeState runtimeState,
                        Runnable executeNodeTask,
                        Consumer<String> dependencySatisfiedNotifier,
                        String dagRunId,
                        DagMessageDedupRepository dedupRepository,
                        DagDistributedProperties distributedProperties) {
        this.dagNode = dagNode;
        this.mailbox = mailbox;
        this.runtimeState = runtimeState;
        this.executeNodeTask = executeNodeTask;
        this.dependencySatisfiedNotifier = dependencySatisfiedNotifier;
        this.dagRunId = dagRunId;
        this.dedupRepository = dedupRepository;
        this.distributedProperties = distributedProperties;
    }

    public DagNode getDagNode() {
        return dagNode;
    }

    public DagNodeRuntimeState getRuntimeState() {
        return runtimeState;
    }

    /**
     * 投递消息。
     *
     * @param message 待处理消息
     * @return true 表示投递成功
     */
    public boolean enqueue(DagMessage message) {
        return mailbox.offer(message);
    }

    /**
     * 投递消息并返回结构化结果。
     *
     * @param message 待处理消息
     * @return 消息投递结果
     */
    public DagMessageDeliveryResult deliver(DagMessage message) {
        return mailbox.deliver(message);
    }

    /**
     * 串行消费当前邮箱中的全部消息。
     */
    public void drainMailbox() {
        while (!mailbox.isEmpty()) {
            DagMessage message = mailbox.poll();
            if (message == null) {
                continue;
            }
            processMessage(message);
        }
    }

    /**
     * 处理单条消息。
     *
     * @param message 消息内容
     */
    public void processMessage(DagMessage message) {
        if (message == null) {
            return;
        }
        // 关键逻辑：跨实例场景优先使用全局去重仓储，确保消息仅生效一次。
        if (dedupRepository != null && distributedProperties != null) {
            boolean accepted = dedupRepository.register(dagRunId,
                    dagNode.getNodeId(),
                    message.getMessageId(),
                    distributedProperties.getDedupTtlMs());
            if (!accepted) {
                log.debug("忽略全局重复DAG消息, dagRunId={}, nodeId={}, messageId={}",
                        dagRunId,
                        dagNode.getNodeId(),
                        message.getMessageId());
                return;
            }
        }
        // 关键逻辑：通过消息幂等键去重，保证依赖扣减不会重复发生。
        if (!runtimeState.registerMessage(message.getMessageId())) {
            log.debug("忽略重复DAG消息, nodeId={}, messageId={}", dagNode.getNodeId(), message.getMessageId());
            return;
        }
        switch (message.getType()) {
            case DEPENDENCY_SATISFIED -> handleDependencySatisfied(message);
            case NODE_START -> startIfReady();
            case NODE_COMPLETE, NODE_FAIL -> log.debug("节点事件已由运行时消费, nodeId={}, type={}",
                    dagNode.getNodeId(),
                    message.getType());
            default -> log.warn("未知DAG消息类型, nodeId={}, type={}", dagNode.getNodeId(), message.getType());
        }
    }

    private void handleDependencySatisfied(DagMessage message) {
        // 关键逻辑：仅处理发往当前节点的依赖消息，避免跨节点污染。
        if (!Objects.equals(dagNode.getNodeId(), message.getToNode())) {
            return;
        }
        int remaining = runtimeState.decrementDependency();
        log.debug("依赖消息处理完成, nodeId={}, fromNode={}, remainingDependencies={}",
                dagNode.getNodeId(),
                message.getFromNode(),
                remaining);
        // 关键逻辑：依赖清零后通知运行时推进就绪判定。
        if (remaining == 0) {
            dependencySatisfiedNotifier.accept(dagNode.getNodeId());
        }
    }

    /**
     * 触发执行。
     *
     * @return true 表示已启动执行
     */
    public boolean startIfReady() {
        // 关键逻辑：仅允许 READY 状态进入 RUNNING，防止重复启动。
        if (!runtimeState.tryStart()) {
            return false;
        }
        executeNodeTask.run();
        return true;
    }
}
