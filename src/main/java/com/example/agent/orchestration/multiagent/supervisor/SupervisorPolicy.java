package com.example.agent.orchestration.multiagent.supervisor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Supervisor 策略配置。
 */
@Component
public class SupervisorPolicy {

    private final int maxFailures;
    private final FailurePropagationPolicy failurePropagationPolicy;

    public SupervisorPolicy(@Value("${agent.multiagent.supervisor.max-failures:2}") int maxFailures,
                            @Value("${agent.multiagent.supervisor.failure-policy:PARTIAL_SUCCESS}")
                            String failurePolicy) {
        this.maxFailures = Math.max(1, maxFailures);
        this.failurePropagationPolicy = parsePolicy(failurePolicy);
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public FailurePropagationPolicy getFailurePropagationPolicy() {
        return failurePropagationPolicy;
    }

    private FailurePropagationPolicy parsePolicy(String value) {
        if (value == null || value.isBlank()) {
            return FailurePropagationPolicy.PARTIAL_SUCCESS;
        }
        try {
            return FailurePropagationPolicy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FailurePropagationPolicy.PARTIAL_SUCCESS;
        }
    }
}

