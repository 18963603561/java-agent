package com.example.agent.tools;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.agent.capabilities.tools.mcp.McpToolStartupWarmup;
import com.example.agent.capabilities.tools.mcp.McpToolSyncService;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class McpToolStartupWarmupTest {

    @Test
    void warmupRunsWhenStartupEnabled() {
        McpToolSyncService syncService = Mockito.mock(McpToolSyncService.class);
        McpToolStartupWarmup warmup = new McpToolStartupWarmup(syncService);
        ReflectionTestUtils.setField(warmup, "startupEnabled", true);

        warmup.warmup();

        verify(syncService, times(1)).refreshAll(anyBoolean(), eq("startup"));
    }

    @Test
    void warmupSkipsWhenStartupDisabled() {
        McpToolSyncService syncService = Mockito.mock(McpToolSyncService.class);
        McpToolStartupWarmup warmup = new McpToolStartupWarmup(syncService);
        ReflectionTestUtils.setField(warmup, "startupEnabled", false);

        warmup.warmup();

        verify(syncService, never()).refreshAll(anyBoolean(), eq("startup"));
    }

    @Test
    void warmupSwallowsException() {
        McpToolSyncService syncService = Mockito.mock(McpToolSyncService.class);
        Mockito.doThrow(new RuntimeException("boom")).when(syncService).refreshAll(anyBoolean(), eq("startup"));
        McpToolStartupWarmup warmup = new McpToolStartupWarmup(syncService);
        ReflectionTestUtils.setField(warmup, "startupEnabled", true);

        warmup.warmup();

        verify(syncService, times(1)).refreshAll(anyBoolean(), eq("startup"));
    }
}

