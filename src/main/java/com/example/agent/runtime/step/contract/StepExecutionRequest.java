package com.example.agent.runtime.step.contract;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.control.RuntimeControlEventPublisher;
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.security.auth.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 步骤执行请求对象。
 *
 * <p>用途：为步骤执行器提供统一的执行入参，集中承载步骤定义、任务上下文与链路信息。
 * <p>输入：步骤定义、任务请求、步骤输入、运行时上下文、租户与链路标识。
 * <p>输出：作为执行器执行过程中的只读上下文载体。
 * <p>边界：{@code stepInput} 是本次执行的临时合并视图，{@code runtimeContext} 是跨步骤共享上下文。
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
     * 步骤输入类型化视图。
     */
    private final StepInputView stepInputView;

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
     * 运行时事件发布器。
     */
    private final RuntimeControlEventPublisher eventPublisher;

    public StepExecutionRequest(StepSpec step,
                                TaskRequest taskRequest,
                                Map<String, Object> stepInput,
                                StepInputView stepInputView,
                                RuntimeContext runtimeContext,
                                TenantContext tenantContext,
                                String workflowId,
                                String taskId,
                                AtomicLong seqCounter,
                                StepRecord record,
                                RuntimeControlEventPublisher eventPublisher) {
        this.step = step;
        this.taskRequest = taskRequest;
        this.stepInputView = stepInputView == null ? StepInputView.from(step, runtimeContext) : stepInputView;
        Map<String, Object> mergedStepInput = this.stepInputView.toExecutionMap();
        if (stepInput != null && !stepInput.isEmpty()) {
            Map<String, Object> copied = new LinkedHashMap<>(mergedStepInput);
            copied.putAll(stepInput);
            this.stepInput = copied;
        } else {
            this.stepInput = new LinkedHashMap<>(mergedStepInput);
        }
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

    public StepInputView getStepInputView() {
        return stepInputView;
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
