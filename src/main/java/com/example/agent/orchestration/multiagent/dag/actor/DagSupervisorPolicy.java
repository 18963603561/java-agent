package com.example.agent.orchestration.multiagent.dag.actor;

import com.example.agent.orchestration.multiagent.supervisor.FailurePropagationPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DAG 监督策略。
 *
 * <p>用途：统一定义 DAG Actor 运行时失败传播、节点重试与超时治理参数。</p>
 * <p>输入输出：输入来源于配置与默认值，输出供运行时执行器读取。</p>
 * <p>边界条件：所有阈值字段均进行下限保护，避免非法配置导致运行时失控。</p>
 */
@Component
public class DagSupervisorPolicy {

    private final int maxFailures;
    private final int maxRetriesPerNode;
    private final long nodeTimeoutMs;
    private final FailurePropagationPolicy failurePropagationPolicy;

    @Autowired
    public DagSupervisorPolicy(
            @Value("${agent.multiagent.dag.supervision.max-failures:2}") int maxFailures,
            @Value("${agent.multiagent.dag.supervision.max-retries-per-node:1}") int maxRetriesPerNode,
            @Value("${agent.multiagent.dag.supervision.node-timeout-ms:30000}") long nodeTimeoutMs,
            @Value("${agent.multiagent.dag.supervision.failure-policy:PARTIAL_SUCCESS}") String failurePolicyText) {
        this(maxFailures,
                maxRetriesPerNode,
                nodeTimeoutMs,
                parseFailurePolicy(failurePolicyText));
    }

    public DagSupervisorPolicy(int maxFailures,
                               int maxRetriesPerNode,
                               long nodeTimeoutMs,
                               FailurePropagationPolicy failurePropagationPolicy) {
        this.maxFailures = Math.max(1, maxFailures);
        this.maxRetriesPerNode = Math.max(0, maxRetriesPerNode);
        this.nodeTimeoutMs = Math.max(1000L, nodeTimeoutMs);
        this.failurePropagationPolicy = failurePropagationPolicy == null
                ? FailurePropagationPolicy.PARTIAL_SUCCESS
                : failurePropagationPolicy;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public int getMaxRetriesPerNode() {
        return maxRetriesPerNode;
    }

    public long getNodeTimeoutMs() {
        return nodeTimeoutMs;
    }

    public FailurePropagationPolicy getFailurePropagationPolicy() {
        return failurePropagationPolicy;
    }

    private static FailurePropagationPolicy parseFailurePolicy(String failurePolicyText) {
        if (failurePolicyText == null || failurePolicyText.isBlank()) {
            return FailurePropagationPolicy.PARTIAL_SUCCESS;
        }
        try {
            return FailurePropagationPolicy.valueOf(failurePolicyText.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return FailurePropagationPolicy.PARTIAL_SUCCESS;
        }
    }
}
