package com.example.agent.governance;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 简易熔断管理器，按失败次数触发熔断。
 */
@Service
public class CircuitBreakerManager {

    private final ConcurrentHashMap<String, CircuitState> states = new ConcurrentHashMap<>();

    @Value("${agent.circuit.failure-threshold:3}")
    private int failureThreshold;

    @Value("${agent.circuit.open-seconds:30}")
    private int openSeconds;

    public boolean allow(String key) {
        CircuitState state = states.get(key);
        if (state == null) {
            return true;
        }
        if (!state.open) {
            return true;
        }
        long now = Instant.now().getEpochSecond();
        return now - state.openedAt > openSeconds;
    }

    public void recordFailure(String key) {
        CircuitState state = states.computeIfAbsent(key, k -> new CircuitState());
        int failures = state.failures.incrementAndGet();
        if (failures >= failureThreshold) {
            state.open = true;
            state.openedAt = Instant.now().getEpochSecond();
        }
    }

    public void recordSuccess(String key) {
        CircuitState state = states.computeIfAbsent(key, k -> new CircuitState());
        state.failures.set(0);
        state.open = false;
    }

    private static class CircuitState {
        private final AtomicInteger failures = new AtomicInteger();
        private boolean open;
        private long openedAt;
    }
}
