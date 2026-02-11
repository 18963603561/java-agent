package com.example.agent.orchestration.multiagent.dag.actor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DAG 背压策略。
 *
 * <p>用途：集中管理 DAG Actor 的并发、队列、邮箱与等待退避参数。</p>
 * <p>输入输出：输入来源于配置与默认值，输出供运行时调度与背压判断使用。</p>
 * <p>边界条件：阈值字段统一做下限保护，确保运行时行为可预期。</p>
 */
@Component
public class DagBackpressurePolicy {

    private final int maxActiveNodes;
    private final int maxReadyQueueSize;
    private final int mailboxCapacity;
    private final long waitTimeoutMs;
    private final long backoffMinMs;
    private final long backoffMaxMs;

    @Autowired
    public DagBackpressurePolicy(
            @Value("${agent.multiagent.dag.backpressure.max-active-nodes:4}") int maxActiveNodes,
            @Value("${agent.multiagent.dag.backpressure.max-ready-queue-size:2048}") int maxReadyQueueSize,
            @Value("${agent.multiagent.dag.backpressure.mailbox-capacity:128}") int mailboxCapacity,
            @Value("${agent.multiagent.dag.backpressure.wait-timeout-ms:30000}") long waitTimeoutMs,
            @Value("${agent.multiagent.dag.backpressure.backoff-min-ms:200}") long backoffMinMs,
            @Value("${agent.multiagent.dag.backpressure.backoff-max-ms:5000}") long backoffMaxMs) {
        this.maxActiveNodes = Math.max(1, maxActiveNodes);
        this.maxReadyQueueSize = Math.max(1, maxReadyQueueSize);
        this.mailboxCapacity = Math.max(1, mailboxCapacity);
        this.waitTimeoutMs = Math.max(1000L, waitTimeoutMs);
        this.backoffMinMs = Math.max(10L, backoffMinMs);
        this.backoffMaxMs = Math.max(this.backoffMinMs, backoffMaxMs);
    }

    public int getMaxActiveNodes() {
        return maxActiveNodes;
    }

    public int getMaxReadyQueueSize() {
        return maxReadyQueueSize;
    }

    public int getMailboxCapacity() {
        return mailboxCapacity;
    }

    public long getWaitTimeoutMs() {
        return waitTimeoutMs;
    }

    public long getBackoffMinMs() {
        return backoffMinMs;
    }

    public long getBackoffMaxMs() {
        return backoffMaxMs;
    }
}
