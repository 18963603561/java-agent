package com.example.agent.runtime;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlannerService;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.ReflectionService;
import com.example.agent.tools.hook.HookManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent Runtime 决策循环驱动，负责执行规划与步骤运行。
 */
@Service
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final PlannerService plannerService;
    private final ReflectionService reflectionService;
    private final StepRuntimeService stepRuntimeService;
    private final EnforcementGateway enforcementGateway;
    private final HookManager hookManager;
    private final FailureClassifier failureClassifier = new FailureClassifier();
    private final RecoveryStrategyManager recoveryStrategyManager;

    public AgentRuntime(PlannerService plannerService,
                        ReflectionService reflectionService,
                        StepRuntimeService stepRuntimeService,
                        EnforcementGateway enforcementGateway,
                        HookManager hookManager,
                        @Value("${agent.runtime.max-retries:1}") int maxRetries) {
        this.plannerService = plannerService;
        this.reflectionService = reflectionService;
        this.stepRuntimeService = stepRuntimeService;
        this.enforcementGateway = enforcementGateway;
        this.hookManager = hookManager;
        this.recoveryStrategyManager = new RecoveryStrategyManager(maxRetries);
    }

    /**
     * 执行任务的运行时循环。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 序列号计数器
     */
    public void run(TaskRequest request,
                    TenantContext tenantContext,
                    String workflowId,
                    String taskId,
                    AtomicLong seqCounter) {
        PlanResult plan = plannerService.plan(request, tenantContext);
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            log.warn("规划为空, tenantId={}, workflowId={}", tenantContext.getTenantId(), workflowId);
            return;
        }

        for (StepRequest step : plan.getSteps()) {
            executeStep(step, request, tenantContext, workflowId, taskId, seqCounter);
        }
    }

    private void executeStep(StepRequest step,
                             TaskRequest request,
                             TenantContext tenantContext,
                             String workflowId,
                             String taskId,
                             AtomicLong seqCounter) {
        int attempt = 0;
        while (true) {
            attempt++;
            StepRecord record = stepRuntimeService.startStep(
                    workflowId,
                    step.getStepType(),
                    attempt,
                    step.getInput(),
                    tenantContext,
                    seqCounter);
            try {
                hookManager.preStep(tenantContext, record);
                String toolName = resolveToolName(request);
                hookManager.preTool(tenantContext, record, toolName);
                Map<String, Object> output = enforcementGateway.execute(
                        request, tenantContext, workflowId, taskId, seqCounter, toolName);
                hookManager.postTool(tenantContext, record, toolName, output);
                hookManager.postStep(tenantContext, record);
                stepRuntimeService.completeStep(record, output, seqCounter);
                ReflectionResult reflection = reflectionService.reflect(step, output, tenantContext);
                if (reflection != null && reflection.isRetryRequested()) {
                    log.info("反思触发重试, stepId={}, attempt={}", record.getStepId(), attempt);
                    continue;
                }
                return;
            } catch (Throwable ex) {
                FailureType failureType = failureClassifier.classify(ex);
                RecoveryStrategy strategy = recoveryStrategyManager.select(failureType, attempt);
                stepRuntimeService.failStep(record, resolveErrorCode(ex),
                        Map.of("message", ex.getMessage() == null ? "step_failed" : ex.getMessage()),
                        seqCounter);
                log.warn("步骤异常, stepId={}, attempt={}, strategy={}", record.getStepId(), attempt, strategy, ex);
                if (strategy == RecoveryStrategy.RETRY) {
                    continue;
                }
                throw ex instanceof RuntimeException runtime ? runtime : new RuntimeException(ex);
            }
        }
    }

    private String resolveToolName(TaskRequest request) {
        if (request.getContext() != null) {
            Object tool = request.getContext().get("tool");
            if (tool instanceof String toolName && !toolName.isBlank()) {
                return toolName;
            }
        }
        return "demo_tool";
    }

    private String resolveErrorCode(Throwable throwable) {
        if (throwable instanceof com.example.agent.common.ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return "INTERNAL_ERROR";
    }
}
