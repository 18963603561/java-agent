package com.example.agent.context.compression;

import com.example.agent.budget.trim.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.application.LlmCompressionOrchestrator;
import com.example.agent.capabilities.context.compression.application.model.LlmCompressionCommand;
import com.example.agent.capabilities.context.compression.application.model.LlmCompressionResult;
import com.example.agent.capabilities.context.compression.parser.CompressionResponseParser;
import com.example.agent.capabilities.context.compression.parser.DefaultCompressionValidationPolicy;
import com.example.agent.capabilities.context.compression.prompt.DefaultCompressionPromptBuilder;
import com.example.agent.capabilities.context.compression.summary.CompressionSummaryGuard;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.security.redaction.RedactionProperties;
import com.example.agent.security.redaction.RedactionService;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LLM 压缩编排服务测试。
 */
class LlmCompressionOrchestratorTest {

    @Test
    void shouldCompressSuccessfullyWhenModelResponseValid() {
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();

        ModelResponse response = new ModelResponse();
        response.setContent("{\"summary\":\"压缩结论\",\"summaryVersion\":\"v2\"}");
        response.setInputTokens(120);
        response.setOutputTokens(40);
        // 模型调用桩：返回合法 JSON 响应用于验证主成功链路。
        when(invocationService.invoke(any(), any(), any(), any(), any(), any(), any())).thenReturn(response);

        LlmCompressionOrchestrator orchestrator = new LlmCompressionOrchestrator(
                invocationService,
                new DefaultCompressionPromptBuilder(),
                new CompressionResponseParser(new ObjectMapper(), new DefaultCompressionValidationPolicy()),
                new CompressionSummaryGuard(new RedactionService(new RedactionProperties(),
                        new MetricsPublisher(new SimpleMeterRegistry()))),
                properties);

        LlmCompressionResult result = orchestrator.compress(buildCommand());

        assertTrue(result.isSuccess());
        assertEquals("压缩结论", result.getSummary());
        assertEquals("v2", result.getSummaryVersion());
        assertEquals(120, result.getInputTokens());
        assertEquals(40, result.getOutputTokens());
    }

    @Test
    void shouldRetryWhenTimeoutThenSucceed() {
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getLlm().setRetry(1);

        ModelResponse response = new ModelResponse();
        response.setContent("{\"summary\":\"重试成功\",\"summaryVersion\":\"v1\"}");
        // 重试链路：首次超时抛错，第二次返回有效响应。
        when(invocationService.invoke(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ErrorCodeException(HttpStatus.GATEWAY_TIMEOUT, "MODEL_TIMEOUT", "timeout"))
                .thenReturn(response);

        LlmCompressionOrchestrator orchestrator = new LlmCompressionOrchestrator(
                invocationService,
                new DefaultCompressionPromptBuilder(),
                new CompressionResponseParser(new ObjectMapper(), new DefaultCompressionValidationPolicy()),
                new CompressionSummaryGuard(new RedactionService(new RedactionProperties(),
                        new MetricsPublisher(new SimpleMeterRegistry()))),
                properties);

        LlmCompressionResult result = orchestrator.compress(buildCommand());

        assertTrue(result.isSuccess());
        assertEquals("重试成功", result.getSummary());
        assertEquals(1, result.getRetryCount());
        verify(invocationService, times(2)).invoke(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * 构造压缩命令。
     */
    private LlmCompressionCommand buildCommand() {
        ContextSnapshot snapshot = new ContextSnapshot();
        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setSummary("待压缩内容");
        snapshot.setWorkingMemory(workingMemory);

        LlmCompressionCommand command = new LlmCompressionCommand();
        command.setSnapshot(snapshot);
        command.setWorkflowId("wf-1");
        command.setSessionId("s-1");
        command.setTriggerReason("OVER_TOTAL");
        return command;
    }
}

