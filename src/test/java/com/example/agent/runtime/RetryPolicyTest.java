package com.example.agent.runtime;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryPolicyTest {

    @Test
    void nextDelayUsesExponentialBackoffWithCap() {
        RetryPolicy policy = new RetryPolicy(100, 500, 0);
        assertEquals(Duration.ofMillis(100), policy.nextDelay(1));
        assertEquals(Duration.ofMillis(200), policy.nextDelay(2));
        assertEquals(Duration.ofMillis(400), policy.nextDelay(3));
        assertEquals(Duration.ofMillis(500), policy.nextDelay(4));
    }
}
