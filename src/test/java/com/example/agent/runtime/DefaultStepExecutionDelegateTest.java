package com.example.agent.runtime;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import com.example.agent.runtime.step.executor.DefaultStepExecutionDelegate;
import com.example.agent.runtime.step.executor.StepExecutorRouter;
import com.example.agent.runtime.step.executor.ToolStepExecutor;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultStepExecutionDelegateTest {

    @Test
    void executeShouldDelegateToRouter() {
        ToolStepExecutor toolStepExecutor = mock(ToolStepExecutor.class);
        StepExecutorRouter stepExecutorRouter = mock(StepExecutorRouter.class);
        StepExecutionDelegate delegate = new DefaultStepExecutionDelegate(toolStepExecutor, stepExecutorRouter);

        StepExecutionRequest request = mock(StepExecutionRequest.class);
        StepExecutionOutput expected = StepExecutionOutput.fromPayload(Map.of("answer", "ok"));
        when(stepExecutorRouter.execute(request)).thenReturn(expected);

        StepExecutionOutput actual = delegate.execute(request);

        assertSame(expected, actual);
        verify(stepExecutorRouter).execute(request);
    }

    @Test
    void executeToolShouldDelegateToToolExecutor() {
        ToolStepExecutor toolStepExecutor = mock(ToolStepExecutor.class);
        StepExecutorRouter stepExecutorRouter = mock(StepExecutorRouter.class);
        StepExecutionDelegate delegate = new DefaultStepExecutionDelegate(toolStepExecutor, stepExecutorRouter);

        StepExecutionRequest request = mock(StepExecutionRequest.class);
        Map<String, Object> arguments = Map.of("q", "hello");
        StepExecutionOutput expected = StepExecutionOutput.fromPayload(Map.of("answer", "ok"));
        when(toolStepExecutor.executeTool(request, "search", arguments)).thenReturn(expected);

        StepExecutionOutput actual = delegate.executeTool(request, "search", arguments);

        assertSame(expected, actual);
        verify(toolStepExecutor).executeTool(request, "search", arguments);
    }

    @Test
    void resolveToolNameShouldDelegateToToolExecutor() {
        ToolStepExecutor toolStepExecutor = mock(ToolStepExecutor.class);
        StepExecutorRouter stepExecutorRouter = mock(StepExecutorRouter.class);
        StepExecutionDelegate delegate = new DefaultStepExecutionDelegate(toolStepExecutor, stepExecutorRouter);

        TaskRequest taskRequest = new TaskRequest();
        StepSpec stepSpec = new StepSpec();
        when(toolStepExecutor.resolveToolName(any(TaskRequest.class), any(StepSpec.class))).thenReturn("search");

        String toolName = delegate.resolveToolName(taskRequest, stepSpec);

        org.junit.jupiter.api.Assertions.assertEquals("search", toolName);
        verify(toolStepExecutor).resolveToolName(eq(taskRequest), eq(stepSpec));
    }
}
