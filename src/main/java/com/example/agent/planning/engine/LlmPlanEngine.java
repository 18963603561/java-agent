package com.example.agent.planning.engine;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.PlanningFieldKeys;
import com.example.agent.planning.PlanningPromptBuilder;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.parser.PlanParseAttemptResult;
import com.example.agent.planning.parser.PlanParseErrorTypes;
import com.example.agent.planning.parser.PlanParseResult;
import com.example.agent.planning.parser.PlanParser;
import com.example.agent.planning.telemetry.PlanTelemetry;
import com.example.agent.capabilities.llm.contract.LlmTaskContextMapper;
import com.example.agent.security.auth.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM 规划引擎。
 *
 * <p>用途：封装规划模型调用、输出修复与解析流程，向编排层输出标准结果。
 */
@Component
public class LlmPlanEngine {

    private static final Logger log = LoggerFactory.getLogger(LlmPlanEngine.class);

    private final ModelInvocationService modelInvocationService;
    private final ModelToolResolver modelToolResolver;
    private final PlanningPromptBuilder planningPromptBuilder;
    private final JsonOutputRepairService jsonOutputRepairService;
    private final ObjectMapper objectMapper;
    private final PlanParser planParser;
    private final PlanTelemetry planTelemetry;

    /**
     * 构造 LLM 规划引擎。
     *
     * @param modelInvocationService 模型调用服务
     * @param modelToolResolver 工具解析器
     * @param planningPromptBuilder 规划提示词构建器
     * @param jsonOutputRepairService JSON 修复服务
     * @param objectMapper 序列化工具
     * @param planParser 规划解析器
     * @param planTelemetry 规划遥测组件
     */
    public LlmPlanEngine(ModelInvocationService modelInvocationService,
                         ModelToolResolver modelToolResolver,
                         PlanningPromptBuilder planningPromptBuilder,
                         JsonOutputRepairService jsonOutputRepairService,
                         ObjectMapper objectMapper,
                         PlanParser planParser,
                         PlanTelemetry planTelemetry) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.planningPromptBuilder = planningPromptBuilder;
        this.jsonOutputRepairService = jsonOutputRepairService;
        this.objectMapper = objectMapper;
        this.planParser = planParser;
        this.planTelemetry = planTelemetry;
    }

    /**
     * 执行 LLM 规划。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列
     * @param planningContext 规划上下文
     * @param planId 规划标识
     * @return 引擎结果
     */
    public LlmPlanEngineResult execute(TaskRequest request,
                                       TenantContext tenantContext,
                                       String workflowId,
                                       AtomicLong seqCounter,
                                       PlanningContext planningContext,
                                       String planId) {
        long start = System.currentTimeMillis();
        PlanningContext resolvedContext = planningContext != null ? planningContext : new PlanningContext(new HashMap<>());
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        try {
            long promptStart = System.currentTimeMillis();
            String prompt = planningPromptBuilder.buildPrompt(request, buildContextSummary(resolvedContext));
            log.debug("规划提示词构建完成, tenantId={}, workflowId={}, planId={}, costMs={}",
                    tenantId,
                    workflowId,
                    planId,
                    System.currentTimeMillis() - promptStart);
            ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.PLANNER);
            String resolvedTenantId = resolveTenantId(request, resolvedContext);
            String resolvedWorkflowId = resolveWorkflowId(workflowId, resolvedContext);
            long bundleStart = System.currentTimeMillis();
            planTelemetry.applyPromptBundle(modelRequest,
                    prompt,
                    request,
                    resolvedContext.mutableValues(),
                    resolvedContext.getContextSnapshot(),
                    resolvedContext.getContextBudget(),
                    resolvedContext.getContextPrune(),
                    resolvedTenantId,
                    resolvedWorkflowId,
                    tenantContext,
                    seqCounter);
            log.debug("规划提示词打包完成, tenantId={}, workflowId={}, planId={}, costMs={}",
                    tenantId,
                    workflowId,
                    planId,
                    System.currentTimeMillis() - bundleStart);

            long toolBindStart = System.currentTimeMillis();
            modelToolResolver.applyTooling(
                    modelRequest,
                    LlmTaskContextMapper.fromTaskRequest(request),
                    null,
                    true);
            log.debug("规划工具绑定完成, tenantId={}, workflowId={}, planId={}, costMs={}",
                    tenantId,
                    workflowId,
                    planId,
                    System.currentTimeMillis() - toolBindStart);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put(PlanningFieldKeys.PLAN_ID, planId);
            metadata.put(PlanningFieldKeys.PROMPT_SCENE, PlanningFieldKeys.SCENE_PLANNER);

            long invokeStart = System.currentTimeMillis();
            planTelemetry.logModelInvokeStart(tenantId, workflowId, planId);
            ModelResponse response = modelInvocationService.invoke(
                    modelRequest,
                    ModelScene.PLANNER,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    "plan",
                    metadata
            );
            planTelemetry.logModelInvokeEnd(
                    tenantId,
                    workflowId,
                    planId,
                    System.currentTimeMillis() - invokeStart,
                    response != null && StringUtils.hasText(response.getContent()));

            if (response == null || !StringUtils.hasText(response.getContent())) {
                log.warn("规划模型输出为空, tenantId={}, workflowId={}, planId={}", tenantId, workflowId, planId);
                planTelemetry.recordPromptTrace(metadata,
                        prompt,
                        tenantContext,
                        workflowId,
                        seqCounter,
                        response != null ? response.getModelId() : null,
                        false,
                        PlanParseErrorTypes.EMPTY_OUTPUT,
                        false,
                        false);
                return new LlmPlanEngineResult(null);
            }

            long parseStart = System.currentTimeMillis();
            LlmPlanAttemptResult parseResult = resolvePlanResult(response.getContent(),
                    request,
                    resolvedContext,
                    tenantId,
                    workflowId,
                    planId);
            log.debug("规划解析阶段完成, tenantId={}, workflowId={}, planId={}, costMs={}, success={}",
                    tenantId,
                    workflowId,
                    planId,
                    System.currentTimeMillis() - parseStart,
                    parseResult.isSuccess());
            if (!parseResult.isSuccess()) {
                log.warn("规划生成失败, tenantId={}, workflowId={}, planId={}, parseErrorType={}, repairAttempted={}, repairSuccess={}",
                        tenantId,
                        workflowId,
                        planId,
                        parseResult.getParseErrorType(),
                        parseResult.isRepairAttempted(),
                        parseResult.isRepairSuccess());
                planTelemetry.recordPromptTrace(metadata,
                        prompt,
                        tenantContext,
                        workflowId,
                        seqCounter,
                        response.getModelId(),
                        false,
                        parseResult.getParseErrorType(),
                        parseResult.isRepairAttempted(),
                        parseResult.isRepairSuccess());
                return new LlmPlanEngineResult(null);
            }

            planTelemetry.recordPromptTrace(metadata,
                    prompt,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    response.getModelId(),
                    true,
                    null,
                    parseResult.isRepairAttempted(),
                    parseResult.isRepairSuccess());
            PlanResult result = parseResult.getPlanResult();
            log.info("规划生成成功(LLM), tenantId={}, workflowId={}, planId={}, steps={}",
                    tenantId,
                    workflowId,
                    planId,
                    result.getSteps().size());
            log.debug("规划总耗时(LLM), tenantId={}, workflowId={}, planId={}, costMs={}",
                    tenantId,
                    workflowId,
                    planId,
                    System.currentTimeMillis() - start);
            return new LlmPlanEngineResult(result);
        } catch (Exception ex) {
            log.warn("规划模型链路异常, tenantId={}, workflowId={}, planId={}, reason={}",
                    tenantId,
                    workflowId,
                    planId,
                    ex.getMessage(),
                    ex);
            return new LlmPlanEngineResult(null);
        }
    }

    private LlmPlanAttemptResult resolvePlanResult(String rawContent,
                                                   TaskRequest request,
                                                   PlanningContext planningContext,
                                                   String tenantId,
                                                   String workflowId,
                                                   String planId) {
        PlanParseAttemptResult parseAttempt = planParser.parseAttempt(rawContent,
                request,
                planningContext.mutableValues());
        if (parseAttempt == null) {
            log.warn("规划解析返回空尝试结果, tenantId={}, workflowId={}, planId={}", tenantId, workflowId, planId);
            return LlmPlanAttemptResult.failure(PlanParseErrorTypes.JSON_PARSE_ERROR, false, false);
        }
        if (parseAttempt.isSuccess()) {
            PlanResult success = toPlanResult(planId, parseAttempt.getResult());
            return LlmPlanAttemptResult.success(success, false, false);
        }
        String parseErrorType = parseAttempt.getErrorType();
        long repairStart = System.currentTimeMillis();
        PlanParseAttemptResult repaired = tryRepairPlan(rawContent,
                request,
                planningContext,
                tenantId,
                workflowId,
                planId);
        planTelemetry.logRepairEnd(tenantId,
                workflowId,
                planId,
                System.currentTimeMillis() - repairStart,
                repaired != null && repaired.isSuccess());
        if (repaired != null && repaired.isSuccess()) {
            PlanResult success = toPlanResult(planId, repaired.getResult());
            return LlmPlanAttemptResult.success(success, true, true);
        }
        return LlmPlanAttemptResult.failure(parseErrorType != null ? parseErrorType : PlanParseErrorTypes.MISSING_STEPS,
                true,
                false);
    }

    private PlanParseAttemptResult tryRepairPlan(String rawContent,
                                                 TaskRequest request,
                                                 PlanningContext planningContext,
                                                 String tenantId,
                                                 String workflowId,
                                                 String planId) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return null;
        }
        String contextJson;
        try {
            Map<String, Object> promptContext = new HashMap<>();
            promptContext.put(PlanningFieldKeys.QUERY, request != null ? request.getQuery() : null);
            promptContext.put(PlanningFieldKeys.CONTEXT_SUMMARY, buildContextSummary(planningContext));
            contextJson = objectMapper.writeValueAsString(promptContext);
        } catch (Exception ex) {
            log.warn("规划修复上下文序列化失败, tenantId={}, workflowId={}, planId={}, reason={}",
                    tenantId,
                    workflowId,
                    planId,
                    ex.getMessage(),
                    ex);
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair(PlanningFieldKeys.SCENE_PLANNER,
                rawContent,
                JsonOutputSchema.PLANNER,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        PlanParseAttemptResult repairedAttempt = planParser.parseAttempt(repaired,
                request,
                planningContext.mutableValues());
        if (!repairedAttempt.isSuccess()) {
            log.warn("规划修复结果解析失败, tenantId={}, workflowId={}, planId={}, errorType={}",
                    tenantId,
                    workflowId,
                    planId,
                    repairedAttempt.getErrorType());
        }
        return repairedAttempt;
    }

    private Map<String, Object> buildContextSummary(PlanningContext planningContext) {
        if (planningContext == null) {
            return Map.of(PlanningFieldKeys.SUMMARY, "(summary disabled)");
        }
        return planningContext.toPromptSummary();
    }

    private String resolveTenantId(TaskRequest request, PlanningContext planningContext) {
        var snapshot = planningContext.getContextSnapshot();
        if (snapshot != null && snapshot.getRuntimeMeta() != null
                && StringUtils.hasText(snapshot.getRuntimeMeta().getTenantId())) {
            return snapshot.getRuntimeMeta().getTenantId();
        }
        if (request != null && request.getContext() != null) {
            Object value = request.getContext().get(PlanningContextKeys.TENANT_ID);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String resolveWorkflowId(String workflowId, PlanningContext planningContext) {
        if (StringUtils.hasText(workflowId)) {
            return workflowId;
        }
        var snapshot = planningContext.getContextSnapshot();
        if (snapshot != null && snapshot.getRuntimeMeta() != null
                && StringUtils.hasText(snapshot.getRuntimeMeta().getWorkflowId())) {
            return snapshot.getRuntimeMeta().getWorkflowId();
        }
        return planningContext.getString(PlanningContextKeys.WORKFLOW_ID);
    }

    /**
     * 生成新规划标识。
     *
     * @return 新规划标识
     */
    public String newPlanId() {
        return UUID.randomUUID().toString();
    }

    private PlanResult toPlanResult(String planId, PlanParseResult parseResult) {
        if (parseResult == null || parseResult.getSteps() == null || parseResult.getSteps().isEmpty()) {
            return null;
        }
        return PlanResult.readonly(planId, parseResult.getSummary(), parseResult.getSteps());
    }
}
