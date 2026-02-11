package com.example.agent.orchestration.multiagent.dag.actor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAG 节点邮箱。
 *
 * <p>用途：为单节点 Actor 提供有界消息缓存，控制瞬时消息洪峰。</p>
 * <p>输入输出：输入为待处理消息，输出为节点消费顺序中的下一条消息。</p>
 */
public class DagMailbox {

    private static final Logger log = LoggerFactory.getLogger(DagMailbox.class);

    private final String nodeId;
    private final int capacity;
    private final BlockingQueue<DagMessage> queue;

    public DagMailbox(String nodeId, int capacity) {
        this.nodeId = nodeId;
        this.capacity = Math.max(capacity, 1);
        this.queue = new ArrayBlockingQueue<>(this.capacity);
    }

    /**
     * 投递消息。
     *
     * @param message DAG 消息
     * @return true 表示投递成功
     */
    public boolean offer(DagMessage message) {
        return deliver(message).isAccepted();
    }

    /**
     * 投递消息并返回结构化结果。
     *
     * @param message DAG 消息
     * @return 投递结果
     */
    public DagMessageDeliveryResult deliver(DagMessage message) {
        // 关键逻辑：无效消息直接拒绝，避免空对象污染消费链路。
        if (message == null) {
            return DagMessageDeliveryResult.rejected("null_message", queue.size(), capacity);
        }
        boolean accepted = queue.offer(message);
        // 关键逻辑：邮箱满载时记录告警，供背压分析使用。
        if (!accepted) {
            log.warn("DAG邮箱已满, nodeId={}, capacity={}, queueSize={}, messageId={}, type={}",
                    nodeId,
                    capacity,
                    queue.size(),
                    message.getMessageId(),
                    message.getType());
            return DagMessageDeliveryResult.rejected("mailbox_full", queue.size(), capacity);
        }
        return DagMessageDeliveryResult.accepted(queue.size(), capacity);
    }

    /**
     * 读取一条消息。
     *
     * @return 下一条消息，若为空则返回 null
     */
    public DagMessage poll() {
        return queue.poll();
    }

    /**
     * 查询当前消息数。
     */
    public int size() {
        return queue.size();
    }

    /**
     * 查询邮箱是否为空。
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * 查询邮箱容量。
     */
    public int capacity() {
        return capacity;
    }
}
