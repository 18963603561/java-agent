package com.example.agent.planning;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.governance.evaluation.CapabilityEvaluationResult;
import com.example.agent.planning.approval.PlanningApprovalService;
import com.example.agent.planning.builder.HeuristicPlanBuilder;
import com.example.agent.planning.capability.PlanningCapabilityService;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.context.PlanningContextMapper;
import com.example.agent.planning.engine.LlmPlanEngine;
import com.example.agent.planning.engine.LlmPlanEngineResult;
import com.example.agent.security.auth.TenantContext;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 规划服务。
 *
 * <p>用途：编排能力评估、LLM 规划与规则回退，输出可执行规划结果。
 * <p>边界：本类仅负责流程编排，不再承载模型调用、解析与规则构建细节。
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    private final PlannerProperties plannerProperties;
    private final PlanningCapabilityService planningCapabilityService;
    private final PlanningApprovalService planningApprovalService;
    private final LlmPlanEngine llmPlanEngine;
    private final HeuristicPlanBuilder heuristicPlanBuilder;
    private final PlanningContextMapper planningContextMapper;

    /**
     * 构造规划服务。
     *
     * @param plannerProperties 规划配置
     * @param planningCapabilityService 规划能力评估服务
     * @param planningApprovalService 规划审批策略服务
     * @param llmPlanEngine LLM 规划引擎
     * @param heuristicPlanBuilder 规则规划构建器
     * @param planningContextMapper 规划上下文映射器
     */
    public PlannerService(PlannerProperties plannerProperties,
                          PlanningCapabilityService planningCapabilityService,
                          PlanningApprovalService planningApprovalService,
                          LlmPlanEngine llmPlanEngine,
                          HeuristicPlanBuilder heuristicPlanBuilder,
                          PlanningContextMapper planningContextMapper) {
        this.plannerProperties = plannerProperties;
        this.planningCapabilityService = planningCapabilityService;
        this.planningApprovalService = planningApprovalService;
        this.llmPlanEngine = llmPlanEngine;
        this.heuristicPlanBuilder = heuristicPlanBuilder;
        this.planningContextMapper = planningContextMapper;
    }

    /**
     * 规划入口。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 规划结果
     */
    public PlanResult plan(TaskRequest request, TenantContext tenantContext) {
        return plan(request, tenantContext, null, null);
    }

    /**
     * 带链路上下文的规划入口。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 规划结果
     */
    public PlanResult plan(TaskRequest request,
                           TenantContext tenantContext,
                           String workflowId,
                           AtomicLong seqCounter) {
        long start = System.currentTimeMillis();
        String planId = UUID.randomUUID().toString();
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        log.info("规划开始, tenantId={}, workflowId={}, planId={}, llmEnabled={}, fallbackEnabled={}",
                tenantId,
                workflowId,
                planId,
                plannerProperties.isLlmEnabled(),
                plannerProperties.isFallbackEnabled());
        long contextStart = System.currentTimeMillis();
        PlanningContext planningContext = planningContextMapper.fromTaskRequest(request);
        log.debug("规划上下文映射完成, tenantId={}, workflowId={}, planId={}, costMs={}",
                tenantId,
                workflowId,
                planId,
                System.currentTimeMillis() - contextStart);

        long evaluateStart = System.currentTimeMillis();
        CapabilityEvaluationResult evaluation = planningCapabilityService.evaluateAndApply(request,
                planningContext,
                tenantContext,
                workflowId,
                seqCounter);
        log.debug("规划能力评估完成, tenantId={}, workflowId={}, planId={}, costMs={}",
                tenantId,
                workflowId,
                planId,
                System.currentTimeMillis() - evaluateStart);

        if (plannerProperties.isLlmEnabled()) {
            long llmStart = System.currentTimeMillis();
            LlmPlanEngineResult llmResult = llmPlanEngine.execute(request,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    planningContext,
                    planId);
            log.debug("规划LLM链路完成, tenantId={}, workflowId={}, planId={}, costMs={}, success={}",
                    tenantId,
                    workflowId,
                    planId,
                    System.currentTimeMillis() - llmStart,
                    llmResult.isSuccess());
            if (llmResult.isSuccess()) {
                PlanResult result = llmResult.getPlanResult();
                planningApprovalService.applyApprovalRequirement(result, request, evaluation);
                log.info("规划结束(LLM), tenantId={}, workflowId={}, planId={}, steps={}",
                        tenantId,
                        workflowId,
                        planId,
                        result != null && result.getSteps() != null ? result.getSteps().size() : 0);
                log.debug("规划总耗时, tenantId={}, workflowId={}, planId={}, costMs={}",
                        tenantId,
                        workflowId,
                        planId,
                        System.currentTimeMillis() - start);
                return result;
            }
            log.warn("LLM 规划未产出结果，进入规则回退, tenantId={}, workflowId={}, planId={}",
                    tenantId,
                    workflowId,
                    planId);
        }

        if (!plannerProperties.isFallbackEnabled()) {
            log.error("规划失败，回退关闭, tenantId={}, workflowId={}, planId={}", tenantId, workflowId, planId);
            throw new IllegalStateException("planner_fallback_disabled");
        }

        long fallbackStart = System.currentTimeMillis();
        PlanResult fallback = heuristicPlanBuilder.build(planId,
                request != null ? request.getQuery() : null,
                planningContext,
                tenantId);
        log.debug("规划规则回退生成完成, tenantId={}, workflowId={}, planId={}, costMs={}",
                tenantId,
                workflowId,
                planId,
                System.currentTimeMillis() - fallbackStart);
        planningApprovalService.applyApprovalRequirement(fallback, request, evaluation);
        log.info("规划结束(规则回退), tenantId={}, workflowId={}, planId={}, steps={}",
                tenantId,
                workflowId,
                planId,
                fallback != null && fallback.getSteps() != null ? fallback.getSteps().size() : 0);
        log.debug("规划总耗时, tenantId={}, workflowId={}, planId={}, costMs={}",
                tenantId,
                workflowId,
                planId,
                System.currentTimeMillis() - start);
        return fallback;
    }
}
