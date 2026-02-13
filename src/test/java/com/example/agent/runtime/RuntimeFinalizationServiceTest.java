package com.example.agent.runtime;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.write.MemoryWriteService;
import com.example.agent.planning.PlanResult;
import com.example.agent.runtime.control.RuntimeControlEventPublisher;
import com.example.agent.runtime.finalize.RuntimeFinalizationService;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepResultMeta;
import com.example.agent.runtime.model.StepResultRaw;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.output.FinalOutputService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeFinalizationServiceTest {

    @Test
    void finalizeRunShouldPublishLlmOutputEventWhenFinalOutputGeneratedByService() {
        // 构建最终输出服务模拟。
        FinalOutputService finalOutputService = Mockito.mock(FinalOutputService.class);
        // 构建记忆写入服务模拟。
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        // 构建运行时事件发布器模拟。
        RuntimeControlEventPublisher eventPublisher = Mockito.mock(RuntimeControlEventPublisher.class);
        // 构建运行时收口服务。
        RuntimeFinalizationService service = new RuntimeFinalizationService(
                finalOutputService,
                memoryWriteService,
                eventPublisher
        );

        // 构建最终输出结果映射。
        Map<String, Object> generated = Map.of(
                "answer", "done",
                "confidence", 0.9,
                "modelId", "gpt-4o",
                "rawRef", "raw://1"
        );
        // 配置最终输出服务返回结果。
        when(finalOutputService.finalizeOutput(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(generated);

        // 构建任务请求对象。
        TaskRequest request = new TaskRequest();
        // 写入请求查询。
        request.setQuery("q");
        // 构建规划结果对象。
        PlanResult plan = new PlanResult("plan-1", "summary", List.of(new StepSpec("TOOL", Map.of("tool", "search"))));
        // 构建空步骤结果列表。
        List<StepResult> stepOutputs = List.of();
        // 构建租户上下文对象。
        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");
        // 构建序列计数器。
        AtomicLong seqCounter = new AtomicLong(0);

        // 调用收口服务执行收口流程。
        RuntimeResult result = service.finalizeRun(
                request,
                tenantContext,
                "wf-1",
                "task-1",
                seqCounter,
                plan,
                stepOutputs
        );

        // 校验运行结果不为空。
        assertNotNull(result);
        // 读取最终输出映射。
        Map<String, Object> finalOutput = result.getFinalOutput();
        // 校验最终输出不为空。
        assertNotNull(finalOutput);
        // 读取元信息映射。
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) finalOutput.get("meta");
        // 读取结果映射。
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) finalOutput.get("result");
        // 读取摘要映射。
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) finalOutput.get("summary");
        // 校验元信息映射不为空。
        assertNotNull(meta);
        // 校验结果映射不为空。
        assertNotNull(resultMap);
        // 校验摘要映射不为空。
        assertNotNull(summary);
        // 校验元信息包含规划标识。
        assertEquals("plan-1", meta.get("planId"));
        // 校验元信息包含规划摘要。
        assertEquals("summary", meta.get("planSummary"));
        // 校验元信息包含模型标识。
        assertEquals("gpt-4o", meta.get("modelId"));
        // 校验元信息包含原始引用。
        assertEquals("raw://1", meta.get("rawRef"));
        // 校验结果包含答案字段。
        assertEquals("done", resultMap.get("answer"));
        // 校验结果包含置信度字段。
        assertEquals(0.9, resultMap.get("confidence"));
        // 校验摘要文本等于答案。
        assertEquals("done", summary.get("text"));

        // 构建参数捕获器。
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        // 验证事件发布并捕获负载。
        verify(eventPublisher).publish(eq(tenantContext), eq("wf-1"), eq(seqCounter), eq(EventType.LLM_OUTPUT),
                payloadCaptor.capture());
        // 读取事件负载。
        Map<String, Object> payload = payloadCaptor.getValue();
        // 校验事件响应文本。
        assertEquals("done", payload.get("response"));
        // 读取事件元数据映射。
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        // 校验元数据映射不为空。
        assertNotNull(metadata);
        // 校验元数据包含规划标识。
        assertEquals("plan-1", metadata.get("planId"));
        // 校验元数据包含规划摘要。
        assertEquals("summary", metadata.get("planSummary"));
        // 校验元数据包含步骤数量。
        assertEquals(0, metadata.get("stepCount"));
        // 校验元数据包含模型标识。
        assertEquals("gpt-4o", metadata.get("modelId"));
        // 校验元数据包含置信度。
        assertEquals(0.9, metadata.get("confidence"));
        // 校验元数据包含原始引用。
        assertEquals("raw://1", metadata.get("rawRef"));

        // 校验记忆写入被调用。
        verify(memoryWriteService).saveTaskMemory(eq(request), any(RuntimeResult.class), eq(tenantContext), eq("task-1"));
    }

    @Test
    void finalizeRunShouldPublishLlmOutputEventWhenFinalOutputExtractedFromLastLlmStep() {
        // 构建最终输出服务模拟。
        FinalOutputService finalOutputService = Mockito.mock(FinalOutputService.class);
        // 构建记忆写入服务模拟。
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        // 构建运行时事件发布器模拟。
        RuntimeControlEventPublisher eventPublisher = Mockito.mock(RuntimeControlEventPublisher.class);
        // 构建运行时收口服务。
        RuntimeFinalizationService service = new RuntimeFinalizationService(
                finalOutputService,
                memoryWriteService,
                eventPublisher
        );

        // 构建任务请求对象。
        TaskRequest request = new TaskRequest();
        // 写入请求查询。
        request.setQuery("q");
        // 构建规划结果对象。
        PlanResult plan = new PlanResult("plan-2", "summary-2", List.of(new StepSpec("LLM", Map.of())));
        // 构建步骤结果对象。
        StepResult stepResult = buildStepResult("step-1", "LLM", Map.of("answer", "from-step", "highlights", "h"));
        // 构建语义摘要对象。
        com.example.agent.runtime.model.SemanticSummary summary = new com.example.agent.runtime.model.SemanticSummary(
                "from-step",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false
        );
        // 写入语义摘要到步骤结果。
        stepResult.setSummary(summary);
        // 构建步骤结果列表。
        List<StepResult> stepOutputs = List.of(stepResult);
        // 构建租户上下文对象。
        TenantContext tenantContext = new TenantContext("tenant-2", "user-2", List.of(), "req-2", "trace-2");
        // 构建序列计数器。
        AtomicLong seqCounter = new AtomicLong(10);

        // 调用收口服务执行收口流程。
        RuntimeResult result = service.finalizeRun(
                request,
                tenantContext,
                "wf-2",
                "task-2",
                seqCounter,
                plan,
                stepOutputs
        );

        // 校验运行结果不为空。
        assertNotNull(result);
        // 读取最终输出映射。
        Map<String, Object> finalOutput = result.getFinalOutput();
        // 校验最终输出不为空。
        assertNotNull(finalOutput);
        // 读取结果映射。
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) finalOutput.get("result");
        // 校验结果映射不为空。
        assertNotNull(resultMap);
        // 校验答案字段来自步骤输出。
        assertEquals("from-step", resultMap.get("answer"));

        // 构建参数捕获器。
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        // 验证事件发布并捕获负载。
        verify(eventPublisher).publish(eq(tenantContext), eq("wf-2"), eq(seqCounter), eq(EventType.LLM_OUTPUT),
                payloadCaptor.capture());
        // 读取事件负载。
        Map<String, Object> payload = payloadCaptor.getValue();
        // 校验事件响应文本。
        assertEquals("from-step", payload.get("response"));

        // 校验最终输出服务未被调用。
        verify(finalOutputService, never()).finalizeOutput(any(), any(), any(), any(), any(), any(), any());
        // 校验记忆写入被调用。
        verify(memoryWriteService).saveTaskMemory(eq(request), any(RuntimeResult.class), eq(tenantContext), eq("task-2"));
    }

    @Test
    void finalizeRunShouldSkipLlmOutputEventWhenFinalOutputEmpty() {
        FinalOutputService finalOutputService = Mockito.mock(FinalOutputService.class);
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        RuntimeControlEventPublisher eventPublisher = Mockito.mock(RuntimeControlEventPublisher.class);
        RuntimeFinalizationService service = new RuntimeFinalizationService(
                finalOutputService,
                memoryWriteService,
                eventPublisher
        );

        when(finalOutputService.finalizeOutput(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of());

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        PlanResult plan = new PlanResult("plan-3", "summary-3", List.of(new StepSpec("TOOL", Map.of("tool", "search"))));
        TenantContext tenantContext = new TenantContext("tenant-3", "user-3", List.of(), "req-3", "trace-3");

        RuntimeResult result = service.finalizeRun(
                request,
                tenantContext,
                "wf-3",
                "task-3",
                new AtomicLong(0),
                plan,
                List.of()
        );

        assertNotNull(result);
        assertEquals(Map.of(), result.getFinalOutput());
        verify(eventPublisher, never()).publish(any(), any(), any(), eq(EventType.LLM_OUTPUT), any());
        verify(memoryWriteService).saveTaskMemory(eq(request), any(RuntimeResult.class), eq(tenantContext), eq("task-3"));
    }

    private StepResult buildStepResult(String stepId, String type, Map<String, Object> rawData) {
        StepResult stepResult = new StepResult();
        StepResultMeta meta = new StepResultMeta();
        meta.setStepId(stepId);
        meta.setType(type);
        meta.setStatus(StepState.COMPLETED);
        stepResult.setMeta(meta);
        StepResultRaw raw = new StepResultRaw();
        raw.setData(rawData);
        raw.setTruncated(false);
        stepResult.setRaw(raw);
        return stepResult;
    }
}
