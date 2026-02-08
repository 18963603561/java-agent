package com.example.agent.memory;

import com.example.agent.capabilities.context.ContextPolicy;
import com.example.agent.capabilities.memory.RetrievalPriority;
import com.example.agent.capabilities.memory.recall.RecallPolicyResolver;
import com.example.agent.capabilities.memory.recall.RecallPolicySnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecallPolicyResolverTest {

    @Test
    void resolveShouldParsePolicyFromMapAndNormalizePriority() {
        RecallPolicyResolver resolver = new RecallPolicyResolver();

        Map<String, Object> policyMap = Map.of(
                "policyId", "p-1",
                "retrievalPriority", List.of("recent", "SEMANTIC", "invalid", "RECENT"),
                "enableSensitiveMask", false
        );

        RecallPolicySnapshot snapshot = resolver.resolve(Map.of("contextPolicy", policyMap));
        assertNotNull(snapshot.getContextPolicy());
        assertEquals("p-1", snapshot.getContextPolicy().getPolicyId());
        assertEquals(List.of(RetrievalPriority.RECENT, RetrievalPriority.SEMANTIC, RetrievalPriority.SUMMARY),
                snapshot.getRetrievalPriority());
        assertFalse(snapshot.isEnableSensitiveMask());
    }

    @Test
    void resolveShouldFallbackToDefaultWhenPolicyMissing() {
        RecallPolicyResolver resolver = new RecallPolicyResolver();

        RecallPolicySnapshot snapshot = resolver.resolve(Map.of());
        assertEquals(RetrievalPriority.defaultOrder(), snapshot.getRetrievalPriority());
        assertTrue(snapshot.isEnableSensitiveMask());
    }

    @Test
    void resolveShouldAcceptContextPolicyObject() {
        RecallPolicyResolver resolver = new RecallPolicyResolver();
        ContextPolicy policy = new ContextPolicy();
        policy.setRetrievalPriority(List.of("SUMMARY"));

        RecallPolicySnapshot snapshot = resolver.resolve(Map.of("contextPolicy", policy));
        assertEquals(List.of(RetrievalPriority.SUMMARY, RetrievalPriority.SEMANTIC, RetrievalPriority.RECENT),
                snapshot.getRetrievalPriority());
    }
}
