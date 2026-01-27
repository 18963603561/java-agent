package com.example.agent.tools.hook;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.streaming.EventStreamService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookManagerTest {

    @Test
    void preToolExecutesHooksInOrder() {
        List<String> order = new ArrayList<>();
        HookHandler hookA = new RecordingHookHandler("hook-a", order);
        HookHandler hookB = new RecordingHookHandler("hook-b", order);

        HookProperties properties = new HookProperties();
        HookProperties.HookConfig configA = new HookProperties.HookConfig();
        configA.setHookId("hook-a");
        configA.setOrder(2);
        HookProperties.HookConfig configB = new HookProperties.HookConfig();
        configB.setHookId("hook-b");
        configB.setOrder(1);
        properties.setHooks(List.of(configA, configB));

        HookManager hookManager = buildHookManager(properties, List.of(hookA, hookB));
        try {
            hookManager.preTool(buildTenant(), null, "demo_tool");
        } finally {
            hookManager.shutdownExecutor();
        }

        assertEquals(List.of("hook-b", "hook-a"), order);
    }

    @Test
    void preToolTimeoutFailOpenAllows() {
        HookHandler slow = new SlowHookHandler("slow");
        HookProperties properties = new HookProperties();
        properties.setTimeoutPolicy(HookTimeoutPolicy.FAIL_OPEN);
        HookProperties.HookConfig config = new HookProperties.HookConfig();
        config.setHookId("slow");
        config.setTimeoutMs(50);
        properties.setHooks(List.of(config));

        HookManager hookManager = buildHookManager(properties, List.of(slow));
        try {
            assertDoesNotThrow(() -> hookManager.preTool(buildTenant(), null, "demo_tool"));
            HookRecord record = hookManager.listRecords().stream()
                    .filter(item -> "slow".equals(item.getHookId()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(record);
            assertTrue(record.isTimeout());
            assertTrue(record.isAllowed());
        } finally {
            hookManager.shutdownExecutor();
        }
    }

    @Test
    void preToolTimeoutFailClosedBlocks() {
        HookHandler slow = new SlowHookHandler("slow");
        HookProperties properties = new HookProperties();
        properties.setTimeoutPolicy(HookTimeoutPolicy.FAIL_CLOSED);
        HookProperties.HookConfig config = new HookProperties.HookConfig();
        config.setHookId("slow");
        config.setTimeoutMs(50);
        properties.setHooks(List.of(config));

        HookManager hookManager = buildHookManager(properties, List.of(slow));
        try {
            assertThrows(ErrorCodeException.class,
                    () -> hookManager.preTool(buildTenant(), null, "demo_tool"));
            HookRecord record = hookManager.listRecords().stream()
                    .filter(item -> "slow".equals(item.getHookId()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(record);
            assertTrue(record.isTimeout());
            assertFalse(record.isAllowed());
        } finally {
            hookManager.shutdownExecutor();
        }
    }

    private HookManager buildHookManager(HookProperties properties, List<HookHandler> handlers) {
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        return new HookManager(properties, eventPublisher, eventStreamService, metricsPublisher, handlers);
    }

    private TenantContext buildTenant() {
        return new TenantContext("tenant-a", "user-a", List.of(), "req-1", "trace-1");
    }

    private static class RecordingHookHandler implements HookHandler {
        private final String hookId;
        private final List<String> order;

        private RecordingHookHandler(String hookId, List<String> order) {
            this.hookId = hookId;
            this.order = order;
        }

        @Override
        public String getHookId() {
            return hookId;
        }

        @Override
        public HookDecision handle(HookContext context) {
            order.add(hookId);
            return new HookDecision(true, "ok", Collections.emptyMap());
        }
    }

    private static class SlowHookHandler implements HookHandler {
        private final String hookId;

        private SlowHookHandler(String hookId) {
            this.hookId = hookId;
        }

        @Override
        public String getHookId() {
            return hookId;
        }

        @Override
        public HookDecision handle(HookContext context) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return new HookDecision(true, "ok", Collections.emptyMap());
        }
    }
}
