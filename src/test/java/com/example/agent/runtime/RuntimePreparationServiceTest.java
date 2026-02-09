package com.example.agent.runtime;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextBuilder;
import com.example.agent.capabilities.context.evidence.EvidencePackService;
import com.example.agent.capabilities.context.builder.exception.ContextBuildException;
import com.example.agent.capabilities.memory.recall.MemoryRecallResult;
import com.example.agent.capabilities.memory.recall.MemoryRecallService;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.example.agent.runtime.prepare.RuntimePreparationService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.payload.ContextEventPublisher;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimePreparationServiceTest {

    @Test
    void prepareThrowsContextBuildExceptionWhenBuilderFails() {
        MemoryRecallService memoryRecallService = Mockito.mock(MemoryRecallService.class);
        when(memoryRecallService.recall(any(), any(), any())).thenReturn(MemoryRecallResult.skipped("skip"));

        HookManager hookManager = Mockito.mock(HookManager.class);
        EvidencePackService evidencePackService = Mockito.mock(EvidencePackService.class);
        ContextBuilder contextBuilder = Mockito.mock(ContextBuilder.class);
        ContextEventPublisher contextEventPublisher = Mockito.mock(ContextEventPublisher.class);

        ContextBuildException contextBuildException = new ContextBuildException(
                "build failed",
                "BUILD_EXECUTION_FAILED",
                "BUILD_PIPELINE",
                "t1",
                "wf-1",
                null);
        when(contextBuilder.build(any())).thenThrow(contextBuildException);

        RuntimePreparationService service = new RuntimePreparationService(
                memoryRecallService,
                hookManager,
                evidencePackService,
                contextBuilder,
                contextEventPublisher);

        TaskRequest request = new TaskRequest();
        request.setQuery("测试问题");
        request.setSessionId("s1");

        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        assertThrows(ContextBuildException.class,
                () -> service.prepare(request, tenantContext, "wf-1", "task-1", new AtomicLong(0)));

        verify(memoryRecallService).recall(eq(request), any(), eq(tenantContext));
        verify(contextBuilder).build(any());
    }
}

