package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.experiment.domain.model.RolloutDecision;
import com.example.agent.capabilities.context.compression.experiment.infrastructure.policy.DefaultCompressionRolloutPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 压缩灰度策略测试。
 */
class CompressionRolloutPolicyTest {

    @Test
    void shouldDisableWhenDualTrackSwitchOff() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setDualTrackEnabled(false);
        properties.getRollout().setGlobalRatio(1D);

        DefaultCompressionRolloutPolicy policy = new DefaultCompressionRolloutPolicy(properties);
        RolloutDecision decision = policy.decide("tenant-a", "context_compress", "s-1", "wf-1");

        assertFalse(decision.isDualTrackEnabled());
        assertEquals("DUAL_TRACK_DISABLED", decision.getReason());
    }

    @Test
    void shouldEnableWhenTenantWhitelistHit() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setDualTrackEnabled(true);
        properties.getRollout().setGlobalRatio(0D);
        properties.getRollout().setTenantWhitelist(List.of("tenant-a"));

        DefaultCompressionRolloutPolicy policy = new DefaultCompressionRolloutPolicy(properties);
        RolloutDecision decision = policy.decide("tenant-a", "context_compress", "s-1", "wf-1");

        assertTrue(decision.isDualTrackEnabled());
        assertEquals("TENANT_WHITELIST", decision.getReason());
    }

    @Test
    void shouldEnableWhenSceneWhitelistHit() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setDualTrackEnabled(true);
        properties.getRollout().setGlobalRatio(0D);
        properties.getRollout().setSceneWhitelist(List.of("context_compress"));

        DefaultCompressionRolloutPolicy policy = new DefaultCompressionRolloutPolicy(properties);
        RolloutDecision decision = policy.decide("tenant-b", "context_compress", "s-2", "wf-2");

        assertTrue(decision.isDualTrackEnabled());
        assertEquals("SCENE_WHITELIST", decision.getReason());
    }

    @Test
    void shouldEnableWhenGlobalRatioFull() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setDualTrackEnabled(true);
        properties.getRollout().setGlobalRatio(1D);

        DefaultCompressionRolloutPolicy policy = new DefaultCompressionRolloutPolicy(properties);
        RolloutDecision decision = policy.decide("tenant-c", "scene-x", "s-3", "wf-3");

        assertTrue(decision.isDualTrackEnabled());
        assertEquals("GLOBAL_RATIO_FULL", decision.getReason());
    }

    @Test
    void shouldRemainStableForSameBucketKey() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setDualTrackEnabled(true);
        properties.getRollout().setGlobalRatio(0.5D);

        DefaultCompressionRolloutPolicy policy = new DefaultCompressionRolloutPolicy(properties);
        RolloutDecision first = policy.decide("tenant-d", "scene-x", "s-4", "wf-4");
        RolloutDecision second = policy.decide("tenant-d", "scene-x", "s-4", "wf-4");

        assertEquals(first.isDualTrackEnabled(), second.isDualTrackEnabled());
        assertEquals(first.getReason(), second.getReason());
        assertEquals(first.getBucketKey(), second.getBucketKey());
    }
}

