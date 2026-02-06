package com.example.agent.runtime.step;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.control.RuntimeControlEventPublisher;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 步骤执行请求对象。
 *
 * <p>用途：为步骤执行器提供显式输入，避免在组件间透传大量参数。
 * <p>输入：步骤定义、任务请求、合并后的步骤输入、运行上下文与链路上下文。
 * <p>输出：供步骤执行器生成输出映射，并由上层统一完成记录/反思/恢复。
 * <p>边界：{@code stepInput} 为本次执行的临时合并视图；{@code runtimeContext} 为跨步骤共享的运行上下文。
 */
public class StepExecutionRequest {

    /**
     * 步骤定义。
     */
    private final StepSpec step;

    /**
     * 任务请求（携带运行上下文）。
     */
    private final TaskRequest taskRequest;

    /**
     * 合并后的步骤输入视图。
     */
    private final Map<String, Object> stepInput;

    /**
     * 运行上下文（跨步骤共享）。
     */
    private final RuntimeContext runtimeContext;

    /**
     * 租户上下文。
     */
    private final TenantContext tenantContext;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 任务标识。
     */
    private final String taskId;

    /**
     * 事件序列计数器。
     */
    private final AtomicLong seqCounter;

    /**
     * 当前步骤记录对象。
     */
    private final StepRecord record;

    /**
     * 运行时事件发布器（由门面注入）。
     */
    private final RuntimeControlEventPublisher eventPublisher;

    public StepExecutionRequest(StepSpec step,
                                TaskRequest taskRequest,
                                Map<String, Object> stepInput,
                                RuntimeContext runtimeContext,
                                TenantContext tenantContext,
                                String workflowId,
                                String taskId,
                                AtomicLong seqCounter,
                                StepRecord record,
                                RuntimeControlEventPublisher eventPublisher) {
        this.step = step;
        this.taskRequest = taskRequest;
        this.stepInput = stepInput;
        this.runtimeContext = runtimeContext;
        this.tenantContext = tenantContext;
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.seqCounter = seqCounter;
        this.record = record;
        this.eventPublisher = eventPublisher;
    }

    public StepSpec getStep() {
        return step;
    }

    public TaskRequest getTaskRequest() {
        return taskRequest;
    }

    public Map<String, Object> getStepInput() {
        return stepInput;
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getTaskId() {
        return taskId;
    }

    public AtomicLong getSeqCounter() {
        return seqCounter;
    }

    public StepRecord getRecord() {
        return record;
    }

    public RuntimeControlEventPublisher getEventPublisher() {
        return eventPublisher;
    }
}
