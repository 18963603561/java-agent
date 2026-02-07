package com.example.agent.runtime.engine;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlannerService;
import com.example.agent.runtime.finalize.RuntimeFinalizationService;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.prepare.RuntimePreparationResult;
import com.example.agent.runtime.prepare.RuntimePreparationService;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 运行时执行门面。
 *
 * <p>用途：负责主流程编排（准备、规划、步骤循环、收口），将步骤细节与事件细节委托给专用服务。
 * <p>输入：任务请求、租户上下文、工作流标识、任务标识、序列计数器。
 * <p>输出：运行时执行结果。
 * <p>边界：规划为空时直接收口为空规划结果；步骤异常由步骤协调服务按策略处理。
 */
@Service
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    /**
     * 规划服务。
     */
    private final PlannerService plannerService;

    /**
     * 运行时准备服务。
     */
    private final RuntimePreparationService runtimePreparationService;

    /**
     * 运行时收口服务。
     */
    private final RuntimeFinalizationService runtimeFinalizationService;

    /**
     * 事件分发服务。
     */
    private final RuntimeEventDispatchService runtimeEventDispatchService;

    /**
     * 步骤执行协调服务。
     */
    private final StepExecutionCoordinator stepExecutionCoordinator;

    public AgentRuntime(PlannerService plannerService,
                        RuntimePreparationService runtimePreparationService,
                        RuntimeFinalizationService runtimeFinalizationService,
                        RuntimeEventDispatchService runtimeEventDispatchService,
                        StepExecutionCoordinator stepExecutionCoordinator) {
        this.plannerService = plannerService;
        this.runtimePreparationService = runtimePreparationService;
        this.runtimeFinalizationService = runtimeFinalizationService;
        this.runtimeEventDispatchService = runtimeEventDispatchService;
        this.stepExecutionCoordinator = stepExecutionCoordinator;
    }

    /**
     * 执行任务的运行时循环。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     * @return 运行时结果
     */
    public RuntimeResult run(TaskRequest request,
                             TenantContext tenantContext,
                             String workflowId,
                             String taskId,
                             AtomicLong seqCounter) {
        RuntimePreparationResult preparationResult = runtimePreparationService.prepare(
                request,
                tenantContext,
                workflowId,
                taskId,
                seqCounter
        );
        RuntimeContext runtimeContext = preparationResult.getRuntimeContext();
        TaskRequest effectiveRequest = preparationResult.getEffectiveRequest();

        List<StepResult> stepOutputs = new ArrayList<>();
        int decomposeAttempts = 0;
        PlanResult plan = plannerService.plan(effectiveRequest, tenantContext, workflowId, seqCounter);
        runtimeEventDispatchService.publishPlanEvent(
                tenantContext,
                workflowId,
                seqCounter,
                plan,
                EventType.PLAN_GENERATED
        );

        while (true) {
            boolean replan = false;
            if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
                log.warn("规划为空, tenantId={}, workflowId={}",
                        tenantContext != null ? tenantContext.getTenantId() : null,
                        workflowId);
                return runtimeFinalizationService.finalizeWhenPlanEmpty(
                        effectiveRequest,
                        tenantContext,
                        workflowId,
                        taskId,
                        plan,
                        stepOutputs
                );
            }

            for (StepSpec step : plan.getSteps()) {
                StepExecutionCoordinator.StepExecutionResult outcome = stepExecutionCoordinator.executeStep(
                        step,
                        effectiveRequest,
                        tenantContext,
                        workflowId,
                        taskId,
                        seqCounter,
                        runtimeContext,
                        decomposeAttempts,
                        stepOutputs
                );
                if (outcome == StepExecutionCoordinator.StepExecutionResult.REPLAN) {
                    decomposeAttempts++;
                    TaskRequest replanRequest = rebuildRequestForReplan(effectiveRequest, decomposeAttempts);
                    plan = plannerService.plan(replanRequest, tenantContext, workflowId, seqCounter);
                    runtimeEventDispatchService.publishPlanEvent(
                            tenantContext,
                            workflowId,
                            seqCounter,
                            plan,
                            EventType.PLAN_REVISED
                    );
                    replan = true;
                    break;
                }
            }

            if (!replan) {
                return runtimeFinalizationService.finalizeRun(
                        effectiveRequest,
                        tenantContext,
                        workflowId,
                        taskId,
                        seqCounter,
                        plan,
                        stepOutputs
                );
            }
        }
    }

    /**
     * 构建重规划请求。
     *
     * @param request 原始请求
     * @param attempt 重规划次数
     * @return 重规划请求
     */
    private TaskRequest rebuildRequestForReplan(TaskRequest request, int attempt) {
        TaskRequest replan = new TaskRequest();
        if (request != null) {
            replan.setQuery(request.getQuery());
            replan.setSessionId(request.getSessionId());
            replan.setSkillName(request.getSkillName());
            replan.setIdempotencyKey(request.getIdempotencyKey());
            replan.setToolChoice(request.getToolChoice());
            Map<String, Object> context = request.getContext() != null
                    ? new HashMap<>(request.getContext())
                    : new HashMap<>();
            context.put("planReason", "decompose");
            context.put("planAttempt", attempt);
            replan.setContext(context);
        }
        return replan;
    }
}
