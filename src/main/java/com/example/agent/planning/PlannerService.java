package com.example.agent.planning;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextAssembler;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.EvidencePack;
import com.example.agent.capabilities.context.EvidencePackService;
import com.example.agent.capabilities.context.EvidenceStats;
import com.example.agent.capabilities.context.PromptAssemblyInput;
import com.example.agent.governance.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationResult;
import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.model.StepPolicy;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

/**
 * 规划服务。
 *
 * <p>用途：基于任务请求与上下文生成可执行步骤计划。
 * <p>职责：统一编排能力评估、LLM 规划、规则回退与步骤规范化。
 */
@Service
public class PlannerService {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    /**
     * TOOL 姝ラ鍙傛暟鏍￠獙寮€鍏筹紝寮€鍚悗缂哄弬浼氳Е鍙戝洖閫€銆?
     */
    @Value("${agent.planner.strict-tool-arguments:false}")
    private boolean strictToolArguments;


    /**
     * 妯″瀷璋冪敤鏈嶅姟銆?
     * <p>绀轰緥锛氳皟鐢ㄦā鍨嬬敓鎴愯鍒掓楠ゃ€?
     */
    private final ModelInvocationService modelInvocationService;
    /**
     * 宸ュ叿瑙ｆ瀽鍣ㄣ€?
     * <p>绀轰緥锛氬皢宸ュ叿閰嶇疆娉ㄥ叆妯″瀷璇锋眰銆?
     */
    private final ModelToolResolver modelToolResolver;
    /**
     * 鎻愮ず璇嶈閰嶅櫒銆?
     * <p>绀轰緥锛氱敓鎴愮粨鏋勫寲娑堟伅鍒楄〃銆?
     */
    private final PromptAssembler promptAssembler;
    private final JsonOutputRepairService jsonOutputRepairService;
    /**
     * 瑙勫垝鐩稿叧閰嶇疆銆?
     * <p>绀轰緥锛氭帶鍒舵槸鍚﹀惎鐢ㄦā鍨嬭鍒掋€?
     */
    private final PlannerProperties plannerProperties;
    /**
     * 鑳藉姏杈圭晫璇勪及鍣ㄣ€?
     * <p>绀轰緥锛氭牴鎹闄╁喅瀹氭槸鍚﹂渶瑕佸鎵广€?
     */
    private final CapabilityBoundaryEvaluator capabilityBoundaryEvaluator;
    /**
     * 搴忓垪鍖栧伐鍏枫€?
     * <p>绀轰緥锛氬皢涓婁笅鏂囪浆鎹负 {@code JSON}銆?
     */
    private final ObjectMapper objectMapper;
    /**
     * 涓婁笅鏂囪閰嶅櫒銆?
     * <p>绀轰緥锛氭瀯寤烘彁绀鸿瘝鎵€闇€鐨勪笂涓嬫枃鐗囨銆?
     */
    private final ContextAssembler contextAssembler;
    /**
     * 涓婁笅鏂囦簨浠跺彂甯冨櫒銆?
     * <p>绀轰緥锛氬彂甯冩彁绀鸿瘝瑁呴厤闃舵浜嬩欢銆?
     */
    private final ContextEventPublisher contextEventPublisher;

    /**
     * 规划提示词构建器。
     */
    private final PlanningPromptBuilder planningPromptBuilder;

    /**
     * 鏋勯€犺鍒掓湇鍔°€?
     *
     * <p>杈撳叆锛氭ā鍨嬭皟鐢ㄦ湇鍔°€佸伐鍏疯В鏋愬櫒涓庨厤缃璞°€?
     * <p>杈撳嚭锛氬垵濮嬪寲鍚庣殑瑙勫垝鏈嶅姟銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * new PlannerService(invocationService, toolResolver, promptAssembler, props, evaluator, mapper, assembler, publisher);
     * }</pre>
     *
     * @param modelInvocationService 妯″瀷璋冪敤鏈嶅姟
     * @param modelToolResolver 宸ュ叿瑙ｆ瀽鍣?
     * @param promptAssembler 鎻愮ず璇嶈閰嶅櫒
     * @param plannerProperties 瑙勫垝閰嶇疆
     * @param capabilityBoundaryEvaluator 鑳藉姏璇勪及鍣?
     * @param objectMapper 搴忓垪鍖栧伐鍏?
     * @param contextAssembler 涓婁笅鏂囪閰嶅櫒
     * @param contextEventPublisher 涓婁笅鏂囦簨浠跺彂甯冨櫒
     */
    public PlannerService(ModelInvocationService modelInvocationService,
                          ModelToolResolver modelToolResolver,
                          PromptAssembler promptAssembler,
                          PlannerProperties plannerProperties,
                          CapabilityBoundaryEvaluator capabilityBoundaryEvaluator,
                          ObjectMapper objectMapper,
                          ContextAssembler contextAssembler,
                          ContextEventPublisher contextEventPublisher,
                          JsonOutputRepairService jsonOutputRepairService,
                          PlanningPromptBuilder planningPromptBuilder) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.plannerProperties = plannerProperties;
        this.capabilityBoundaryEvaluator = capabilityBoundaryEvaluator;
        this.objectMapper = objectMapper;
        this.contextAssembler = contextAssembler;
        this.contextEventPublisher = contextEventPublisher;
        this.jsonOutputRepairService = jsonOutputRepairService;
        this.planningPromptBuilder = planningPromptBuilder;
    }

    /**
     * 鐢熸垚瑙勫垝缁撴灉銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹備笌绉熸埛涓婁笅鏂囥€?
     * <p>杈撳嚭锛氳鍒掔粨鏋滃璞°€?
     * <p>杈圭晫锛氫細杞皟甯︿笂涓嬫枃鐨勬柟娉曪紝淇濇寔缁熶竴閫昏緫銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * PlanResult plan = plan(request, tenantContext);
     * }</pre>
     *
     * @param request 浠诲姟璇锋眰
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @return 瑙勫垝缁撴灉
     */
    public PlanResult plan(TaskRequest request, TenantContext tenantContext) {
        return plan(request, tenantContext, null, null);
    }

    /**
     * 甯﹁繍琛屼笂涓嬫枃鐨勮鍒掑叆鍙ｏ紝鐢ㄤ簬鍙戝竷妯″瀷浜嬩欢銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹傘€佺鎴蜂笂涓嬫枃涓庨摼璺爣璇嗐€?
     * <p>杈撳嚭锛氳鍒掔粨鏋滃璞°€?
     * <p>杈圭晫锛氬綋妯″瀷瑙勫垝澶辫触涓旂鐢ㄥ洖閫€鏃舵姏鍑哄紓甯搞€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * PlanResult plan = plan(request, tenantContext, workflowId, seqCounter);
     * }</pre>
     *
     * @param request 浠诲姟璇锋眰
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param workflowId 宸ヤ綔娴佹爣璇?
     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?
     * @return 瑙勫垝缁撴灉
     */
    public PlanResult plan(TaskRequest request,
                           TenantContext tenantContext,
                           String workflowId,
                           AtomicLong seqCounter) {
        String planId = UUID.randomUUID().toString();
        String query = request != null ? request.getQuery() : null;
        Map<String, Object> context = request != null && request.getContext() != null
                ? new HashMap<>(request.getContext())
                : new HashMap<>();
        if (request != null && request.getToolChoice() != null
                && !context.containsKey(PlanningContextKeys.TOOL_CHOICE)) {
            context.put(PlanningContextKeys.TOOL_CHOICE, request.getToolChoice());
        }
        // 璇勪及鑳藉姏杈圭晫锛屽喅瀹氭槸鍚﹂渶瑕佸鎵规垨鎺ㄨ崘绛栫暐銆?
        CapabilityEvaluationResult evaluation = evaluateCapability(request, context, tenantContext, workflowId,
                seqCounter);
        applyEvaluationToContext(context, evaluation);

        // 浼樺厛灏濊瘯妯″瀷瑙勫垝锛屽け璐ュ垯鍥為€€瑙勫垯瑙勫垝銆?
        if (plannerProperties.isLlmEnabled()) {
            PlanResult llmPlan = tryLlmPlan(request, tenantContext, workflowId, seqCounter, context, planId);
            if (llmPlan != null) {
                applyApprovalRequirement(llmPlan, request, evaluation);
                return llmPlan;
            }
        }

        if (!plannerProperties.isFallbackEnabled()) {
            throw new IllegalStateException("planner_fallback_disabled");
        }
        // 瑙勫垯瑙勫垝浣滀负鍏滃簳绛栫暐銆?
        PlanResult fallback = buildHeuristicPlan(planId, query, context, tenantContext);
        applyApprovalRequirement(fallback, request, evaluation);
        return fallback;
    }

    /**
     * 鎵ц鑳藉姏杈圭晫璇勪及銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹傘€佷笂涓嬫枃涓庨摼璺俊鎭€?
     * <p>杈撳嚭锛氳瘎浼扮粨鏋滃璞°€?
     * <p>杈圭晫锛氳瘎浼板櫒鏈惎鐢ㄦ椂杩斿洖 {@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * CapabilityEvaluationResult result = evaluateCapability(request, context, ctx, wfId, seq);
     * }</pre>
     */
    private CapabilityEvaluationResult evaluateCapability(TaskRequest request,
                                                          Map<String, Object> context,
                                                          TenantContext tenantContext,
                                                          String workflowId,
                                                          AtomicLong seqCounter) {
        if (capabilityBoundaryEvaluator == null || !capabilityBoundaryEvaluator.isEnabled()) {
            return null;
        }
        // 鏋勫缓璇勪及杈撳叆锛屽寘鍚棶棰樸€佸伐鍏锋憳瑕佷笌棰勭畻淇℃伅銆?
        CapabilityEvaluationInput input = new CapabilityEvaluationInput();
        input.setTaskDescription(request != null ? request.getQuery() : null);
        input.setPlanSummary(resolvePlanSummary(context));
        input.setToolSummary(resolveToolSummary(context));
        input.setBudgetThresholdTokens(resolveBudgetThreshold(context));
        input.setFailureTypes(resolveFailureTypes(context));
        input.setComplexityScore(estimateComplexity(request != null ? request.getQuery() : null));
        return capabilityBoundaryEvaluator.evaluate(input, tenantContext, workflowId, seqCounter);
    }

    /**
     * 灏嗚瘎浼扮粨鏋滃啓鍏ヤ笂涓嬫枃銆?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠涓庤瘎浼扮粨鏋溿€?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氳瘎浼拌璺宠繃鏃朵笉鍐欏叆銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * applyEvaluationToContext(context, evaluation);
     * }</pre>
     */
    private void applyEvaluationToContext(Map<String, Object> context, CapabilityEvaluationResult evaluation) {
        if (context == null || evaluation == null || evaluation.isSkipped()) {
            return;
        }
        if (evaluation.isShouldAskApproval() && !context.containsKey(PlanningContextKeys.REQUIRES_APPROVAL)) {
            context.put(PlanningContextKeys.REQUIRES_APPROVAL, true);
            context.putIfAbsent(PlanningContextKeys.APPROVAL_SOURCE, "evaluation");
        }
        if (!hasExplicitStrategy(context) && evaluation.getRecommendedStrategy() != null) {
            mapStrategyToContext(context, evaluation.getRecommendedStrategy());
        }
        context.put(PlanningContextKeys.CAPABILITY_SCORE, evaluation.getComplexityScore());
        context.put(PlanningContextKeys.CAPABILITY_RISK, evaluation.getRiskLevel() != null
                ? evaluation.getRiskLevel().name()
                : null);
    }

    /**
     * 灏嗗鎵硅姹傜粦瀹氬埌瑙勫垝姝ラ銆?
     *
     * <p>杈撳叆锛氳鍒掔粨鏋溿€佷换鍔¤姹備笌璇勪及缁撴灉銆?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氭棤姝ラ鎴栧凡鏄惧紡鎸囧畾瀹℃壒鏃朵笉澶勭悊銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * applyApprovalRequirement(plan, request, evaluation);
     * }</pre>
     */
    private void applyApprovalRequirement(PlanResult plan,
                                          TaskRequest request,
                                          CapabilityEvaluationResult evaluation) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return;
        }
        if (evaluation == null || evaluation.isSkipped() || !evaluation.isShouldAskApproval()) {
            return;
        }
        if (hasExplicitApproval(request) || hasExplicitApproval(plan.getSteps())) {
            return;
        }
        StepSpec first = plan.getSteps().get(0);
        markStepRequiresApproval(first, "evaluation");
    }

    /**
     * 鍒ゆ柇浠诲姟璇锋眰鏄惁鏄惧紡瑕佹眰瀹℃壒銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹傚璞°€?
     * <p>杈撳嚭锛氭槸鍚﹀瓨鍦ㄥ鎵规爣璁般€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * boolean required = hasExplicitApproval(request);
     * }</pre>
     */
    private boolean hasExplicitApproval(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return false;
        }
        return request.getContext().containsKey(PlanningContextKeys.REQUIRES_APPROVAL);
    }

    /**
     * 鍒ゆ柇姝ラ鍒楄〃涓槸鍚︽樉寮忔爣璁板鎵广€?
     *
     * <p>杈撳叆锛氭楠ゅ垪琛ㄣ€?
     * <p>杈撳嚭锛氭槸鍚﹀瓨鍦ㄥ鎵规爣璁般€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * boolean required = hasExplicitApproval(steps);
     * }</pre>
     */
    private boolean hasExplicitApproval(List<StepSpec> steps) {
        if (steps == null) {
            return false;
        }
        for (StepSpec step : steps) {
            if (step == null) {
                continue;
            }
            if (step.getRequiresApproval() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 鏍囪姝ラ闇€瑕佸鎵广€?
     *
     * <p>杈撳叆锛氭楠ゅ璞′笌鏉ユ簮鏍囪瘑銆?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氭楠や负绌烘椂鐩存帴杩斿洖銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * markStepRequiresApproval(step, "evaluation");
     * }</pre>
     */
    private void markStepRequiresApproval(StepSpec step, String source) {
        if (step == null) {
            return;
        }
        step.setRequiresApproval(true);
        step.setApprovalSource(source);
    }

    /**
     * 鍒ゆ柇涓婁笅鏂囦腑鏄惁鏄惧紡鎸囧畾绛栫暐銆?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氭槸鍚﹀瓨鍦ㄧ瓥鐣ュ瓧娈点€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * boolean explicit = hasExplicitStrategy(context);
     * }</pre>
     */
    private boolean hasExplicitStrategy(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        return context.containsKey(PlanningContextKeys.STRATEGY)
                || context.containsKey(PlanningContextKeys.MODE)
                || context.containsKey(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY)
                || context.containsKey(PlanningContextKeys.REACT)
                || context.containsKey(PlanningContextKeys.REACT_ENABLED);
    }

    /**
     * 灏嗘帹鑽愮瓥鐣ユ槧灏勫埌涓婁笅鏂囧瓧娈点€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠涓庣瓥鐣ュ悕绉般€?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氱瓥鐣ヤ负绌烘椂涓嶅鐞嗐€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * mapStrategyToContext(context, "react");
     * }</pre>
     */
    private void mapStrategyToContext(Map<String, Object> context, String strategy) {
        if (context == null || strategy == null) {
            return;
        }
        String normalized = strategy.toLowerCase(Locale.ROOT);
        if ("thought_tree".equals(normalized) || "tree_of_thoughts".equals(normalized)) {
            context.put(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY, "tree_of_thoughts");
            return;
        }
        if ("debate".equals(normalized)) {
            context.put(PlanningContextKeys.STRATEGY, "debate");
            return;
        }
        if ("research".equals(normalized)) {
            context.put(PlanningContextKeys.MODE, "deep_research");
            context.put(PlanningContextKeys.STRATEGY, "research");
            return;
        }
        if ("react".equals(normalized)) {
            context.put(PlanningContextKeys.REACT, true);
        }
    }

    /**
     * 鑾峰彇涓婁笅鏂囦腑鐨勮鍒掓憳瑕併€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氳鍒掓憳瑕佸瓧绗︿覆鎴?{@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String summary = resolvePlanSummary(context);
     * }</pre>
     */
    private String resolvePlanSummary(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object summary = context.get(PlanningContextKeys.PLAN_SUMMARY);
        return summary instanceof String value ? value : null;
    }

    /**
     * 鑾峰彇涓婁笅鏂囦腑鐨勫伐鍏锋憳瑕併€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氬伐鍏锋憳瑕佸瓧绗︿覆鎴?{@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String tools = resolveToolSummary(context);
     * }</pre>
     */
    private String resolveToolSummary(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object tool = context.get(PlanningContextKeys.TOOL);
        Object toolName = context.get(PlanningContextKeys.TOOL_NAME);
        Object fallbackTool = context.get(PlanningContextKeys.FALLBACK_TOOL);
        StringBuilder builder = new StringBuilder();
        if (tool instanceof String value && !value.isBlank()) {
            builder.append(value);
        }
        if (toolName instanceof String value && !value.isBlank()) {
            appendWithComma(builder, value);
        }
        if (fallbackTool instanceof String value && !value.isBlank()) {
            appendWithComma(builder, value);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * 浠ラ€楀彿鎷兼帴瀛楃涓层€?
     *
     * <p>杈撳叆锛氬瓧绗︿覆鏋勫缓鍣ㄤ笌寰呮嫾鎺ュ€笺€?
     * <p>杈撳嚭锛氭棤銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * appendWithComma(builder, "toolA");
     * }</pre>
     */
    private void appendWithComma(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(value);
    }

    /**
     * 鑾峰彇棰勭畻闃堝€奸厤缃€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氶槇鍊兼暣鏁帮紝榛樿 {@code 0}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * int threshold = resolveBudgetThreshold(context);
     * }</pre>
     */
    private int resolveBudgetThreshold(Map<String, Object> context) {
        if (context == null) {
            return 0;
        }
        Object threshold = context.get(PlanningContextKeys.BUDGET_THRESHOLD_TOKENS);
        if (threshold instanceof Number number) {
            return number.intValue();
        }
        if (threshold instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    /**
     * 瑙ｆ瀽涓婁笅鏂囦腑鐨勫け璐ョ被鍨嬪垪琛ㄣ€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氬け璐ョ被鍨嬪垪琛ㄣ€?
     * <p>杈圭晫锛氳В鏋愬け璐ユ椂杩斿洖绌哄垪琛ㄣ€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * List<String> failures = resolveFailureTypes(context);
     * }</pre>
     */
    private List<String> resolveFailureTypes(Map<String, Object> context) {
        if (context == null) {
            return List.of();
        }
        Object failures = context.get(PlanningContextKeys.FAILURE_TYPES);
        if (failures instanceof List<?> list) {
            List<String> output = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String value && !value.isBlank()) {
                    output.add(value);
                }
            }
            return output;
        }
        if (failures instanceof String text && !text.isBlank()) {
            return List.of(text.trim());
        }
        return List.of();
    }

    /**
     * 灏濊瘯浣跨敤妯″瀷鐢熸垚瑙勫垝銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹傘€佺鎴蜂笂涓嬫枃涓庝笂涓嬫枃淇℃伅銆?
     * <p>杈撳嚭锛氳鍒掔粨鏋滃璞℃垨 {@code null}銆?
     * <p>杈圭晫锛氭ā鍨嬭緭鍑轰笉鍚堟硶鏃惰繑鍥?{@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * PlanResult plan = tryLlmPlan(request, ctx, wfId, seq, context, planId);
     * }</pre>
     */
    private PlanResult tryLlmPlan(TaskRequest request,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  Map<String, Object> context,
                                  String planId) {
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        try {
            String prompt = buildPlanPrompt(request, context);
            ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.PLANNER);
            applyPromptBundle(modelRequest, prompt, request, context, tenantContext, workflowId, seqCounter);
            modelToolResolver.applyTooling(modelRequest, request, null, true);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("planId", planId);
            metadata.put("promptScene", "planner");

            long invokeStart = System.currentTimeMillis();
            log.info("开始调用规划模型, tenantId={}, workflowId={}, planId={}", tenantId, workflowId, planId);
            ModelResponse response = modelInvocationService.invoke(
                    modelRequest,
                    ModelScene.PLANNER,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    "plan",
                    metadata
            );
            log.info("规划模型调用结束, tenantId={}, workflowId={}, planId={}, costMs={}, hasContent={}",
                    tenantId,
                    workflowId,
                    planId,
                    System.currentTimeMillis() - invokeStart,
                    response != null && StringUtils.hasText(response.getContent()));

            if (response == null || !StringUtils.hasText(response.getContent())) {
                log.warn("规划模型输出为空, tenantId={}, workflowId={}, planId={}", tenantId, workflowId, planId);
                return null;
            }

            String rawContent = response.getContent();
            String parseErrorType = null;
            boolean repairAttempted = false;
            boolean repairSuccess = false;
            PlanParsingResult parsed;
            try {
                parsed = parsePlan(rawContent, request, context);
            } catch (Exception ex) {
                log.warn("规划解析失败, tenantId={}, workflowId={}, planId={}, reason={}",
                        tenantId,
                        workflowId,
                        planId,
                        ex.getMessage(),
                        ex);
                parsed = null;
                parseErrorType = "json_parse_error";
            }

            if (parsed == null || parsed.steps == null || parsed.steps.isEmpty()) {
                if (parseErrorType == null) {
                    parseErrorType = resolveParseErrorType(rawContent);
                }
                repairAttempted = true;
                long repairStart = System.currentTimeMillis();
                PlanParsingResult repaired = tryRepairPlan(rawContent, request, context, tenantId, workflowId, planId);
                log.info("规划修复调用结束, tenantId={}, workflowId={}, planId={}, costMs={}, success={}",
                        tenantId,
                        workflowId,
                        planId,
                        System.currentTimeMillis() - repairStart,
                        repaired != null && repaired.steps != null && !repaired.steps.isEmpty());
                if (repaired != null && repaired.steps != null && !repaired.steps.isEmpty()) {
                    parsed = repaired;
                    repairSuccess = true;
                }
            }

            if (parsed == null || parsed.steps == null || parsed.steps.isEmpty()) {
                log.warn("规划生成失败, tenantId={}, workflowId={}, planId={}, parseErrorType={}, repairAttempted={}, repairSuccess={}",
                        tenantId,
                        workflowId,
                        planId,
                        parseErrorType,
                        repairAttempted,
                        repairSuccess);
                recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), false,
                        parseErrorType, repairAttempted, repairSuccess);
                return null;
            }

            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), true, null,
                    repairAttempted, repairSuccess);
            PlanResult result = new PlanResult(planId, parsed.summary, parsed.steps);
            log.info("规划生成成功(LLM), tenantId={}, workflowId={}, planId={}, steps={}",
                    tenantId,
                    workflowId,
                    planId,
                    parsed.steps.size());
            return result;
        } catch (Exception ex) {
            log.warn("规划模型链路异常, tenantId={}, workflowId={}, planId={}, reason={}",
                    tenantId,
                    workflowId,
                    planId,
                    ex.getMessage(),
                    ex);
            return null;
        }
    }

    /**
     * 浣跨敤瑙勫垯绛栫暐鐢熸垚瑙勫垝銆?
     *
     * <p>杈撳叆锛氳鍒掓爣璇嗐€侀棶棰樹笌涓婁笅鏂囦俊鎭€?
     * <p>杈撳嚭锛氳鍒掔粨鏋滃璞°€?
     * <p>杈圭晫锛氶棶棰樹负绌烘椂浣跨敤榛樿澶嶆潅搴︺€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * PlanResult plan = buildHeuristicPlan(planId, query, context, tenantContext);
     * }</pre>
     */
    private PlanResult buildHeuristicPlan(String planId,
                                          String query,
                                          Map<String, Object> context,
                                          TenantContext tenantContext) {
        double complexityScore = estimateComplexity(query);
        String cognitiveStrategy = resolveCognitiveStrategy(context, complexityScore);
        String executionStrategy = resolveExecutionStrategy(context, complexityScore);
        String mode = context.get(PlanningContextKeys.MODE) instanceof String value ? value.toLowerCase(Locale.ROOT) : "";
        String strategy = context.get(PlanningContextKeys.STRATEGY) instanceof String value
                ? value.toLowerCase(Locale.ROOT)
                : "";

        List<StepSpec> steps = new ArrayList<>();
        List<Map<String, Object>> planSteps = new ArrayList<>();
        List<Map<String, String>> dependencies = new ArrayList<>();

        String previousStepKey = null;
        String thoughtStepKey = null;
        // 浼樺厛澶勭悊鏄惧紡閾惧紡鎺ㄧ悊绛栫暐銆?
        if (isChainOfThoughtRequested(mode, strategy, cognitiveStrategy)) {
            String stepKey = "step-1";
            Map<String, Object> input = new HashMap<>();
            input.put("question", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("CHAIN_OF_THOUGHT", input));
            planSteps.add(Map.of("id", stepKey, "type", "CHAIN_OF_THOUGHT", "name", "chain-of-thought"));
            String summary = String.format(Locale.ROOT,
                    "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                    executionStrategy, cognitiveStrategy, complexityScore, steps.size());
            PlanResult result = new PlanResult(planId, summary, steps);
            log.info("规划生成（链式推理）, tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put(PlanningContextKeys.PLAN_STEPS, planSteps);
            context.put(PlanningContextKeys.PLAN_DEPENDENCIES, dependencies);
            context.put(PlanningContextKeys.EXECUTION_STRATEGY, executionStrategy);
            context.put(PlanningContextKeys.COGNITIVE_STRATEGY, cognitiveStrategy);
            return result;
        }
        // 闇€瑕佹€濈淮鏍戠瓥鐣ユ椂鍏堟彃鍏ユ€濈淮鏍戞楠ゃ€?
        if (needsThoughtTree(cognitiveStrategy, complexityScore)) {
            thoughtStepKey = "step-1";
            Map<String, Object> thoughtInput = new HashMap<>();
            thoughtInput.put("prompt", query);
            thoughtInput.put("stepKey", thoughtStepKey);
            thoughtInput.put("critical", Boolean.TRUE);
            thoughtInput.put("strategy", cognitiveStrategy);
            steps.add(buildStepSpec("THOUGHT_TREE", thoughtInput));
            planSteps.add(Map.of("id", thoughtStepKey, "type", "THOUGHT_TREE", "name", "thought-tree"));
            previousStepKey = thoughtStepKey;
        }

        // 澶氭櫤鑳戒綋绛栫暐銆?
        if ("multi_agent".equals(strategy) || "multi-agent".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("MULTI_AGENT", input));
            planSteps.add(Map.of("id", stepKey, "type", "MULTI_AGENT", "name", "multi-agent"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

        // 杈╄绛栫暐銆?
        if ("debate".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("topic", query);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("DEBATE", input));
            planSteps.add(Map.of("id", stepKey, "type", "DEBATE", "name", "debate"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

        // 鐮旂┒绛栫暐銆?
        if ("deep_research".equals(mode) || "research".equals(strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("RESEARCH", input));
            planSteps.add(Map.of("id", stepKey, "type", "RESEARCH", "name", "research"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            previousStepKey = stepKey;
        }

        // 鍙嶅簲寮忕瓥鐣ャ€?
        if (shouldUseReact(context, mode, strategy)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            steps.add(buildStepSpec("REACT", input));
            planSteps.add(Map.of("id", stepKey, "type", "REACT", "name", "react"));
            if (previousStepKey != null) {
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            String summary = String.format(Locale.ROOT,
                    "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                    executionStrategy, cognitiveStrategy, complexityScore, steps.size());
            PlanResult result = new PlanResult(planId, summary, steps);
            log.info("规划生成（ReAct）, tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put(PlanningContextKeys.PLAN_STEPS, planSteps);
            context.put(PlanningContextKeys.PLAN_DEPENDENCIES, dependencies);
            context.put(PlanningContextKeys.EXECUTION_STRATEGY, executionStrategy);
            context.put(PlanningContextKeys.COGNITIVE_STRATEGY, cognitiveStrategy);
            return result;
        }

        if (isToolsDisabled(context)) {
            String stepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("context", context);
            input.put("stepKey", stepKey);
            input.put("critical", complexityScore >= 0.6);
            input.put("strategy", cognitiveStrategy);
            if (previousStepKey != null) {
                input.put("dependsOn", List.of(previousStepKey));
                dependencies.add(Map.of("from", previousStepKey, "to", stepKey));
            }
            steps.add(buildStepSpec("LLM", input));
            planSteps.add(Map.of("id", stepKey, "type", "LLM", "name", "llm"));

            String summary = String.format(Locale.ROOT,
                    "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                    executionStrategy, cognitiveStrategy, complexityScore, steps.size());
            PlanResult result = new PlanResult(planId, summary, steps);

            log.info("规划生成（大模型直答）, tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put(PlanningContextKeys.PLAN_STEPS, planSteps);
            context.put(PlanningContextKeys.PLAN_DEPENDENCIES, dependencies);
            context.put(PlanningContextKeys.EXECUTION_STRATEGY, executionStrategy);
            context.put(PlanningContextKeys.COGNITIVE_STRATEGY, cognitiveStrategy);
            return result;
        }

        // 榛樿宸ュ叿姝ラ銆?
        String toolStepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
        Map<String, Object> toolInput = new HashMap<>();
        toolInput.put("query", query);
        toolInput.put("context", context);
        toolInput.put("stepKey", toolStepKey);
        toolInput.put("critical", complexityScore >= 0.6);
        toolInput.put("strategy", cognitiveStrategy);
        Object toolName = context.get(PlanningContextKeys.TOOL);
        if (toolName instanceof String name && !name.isBlank()) {
            toolInput.put(PlanningContextKeys.TOOL, name);
        }
        Object fallbackTool = context.get(PlanningContextKeys.FALLBACK_TOOL);
        if (fallbackTool instanceof String fallbackName && !fallbackName.isBlank()) {
            toolInput.put(PlanningContextKeys.FALLBACK_TOOL, fallbackName);
        }
        if (previousStepKey != null) {
            toolInput.put("dependsOn", List.of(previousStepKey));
            dependencies.add(Map.of("from", previousStepKey, "to", toolStepKey));
        }
        steps.add(buildStepSpec("TOOL", toolInput));
        planSteps.add(Map.of("id", toolStepKey, "type", "TOOL", "name", "tool-exec"));

        String summary = String.format(Locale.ROOT,
                "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                executionStrategy, cognitiveStrategy, complexityScore, steps.size());
        PlanResult result = new PlanResult(planId, summary, steps);

        log.info("规划生成（规则回退）, tenantId={}, planId={}, summary={}",
                tenantContext.getTenantId(), planId, summary);
        context.put(PlanningContextKeys.PLAN_STEPS, planSteps);
        context.put(PlanningContextKeys.PLAN_DEPENDENCIES, dependencies);
        context.put(PlanningContextKeys.EXECUTION_STRATEGY, executionStrategy);
        context.put(PlanningContextKeys.COGNITIVE_STRATEGY, cognitiveStrategy);
        return result;
    }

    /**
     * 鏋勫缓瑙勫垝鎻愮ず璇嶃€?
     *
     * <p>杈撳叆锛氫换鍔¤姹備笌涓婁笅鏂囨槧灏勩€?
     * <p>杈撳嚭锛氭彁绀鸿瘝瀛楃涓层€?
     * <p>杈圭晫锛氬簭鍒楀寲澶辫触鏃朵娇鐢ㄧ┖涓婁笅鏂囥€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String prompt = buildPlanPrompt(request, context);
     * }</pre>
     */
    private String buildPlanPrompt(TaskRequest request, Map<String, Object> context) {
        if (planningPromptBuilder == null) {
            throw new IllegalStateException("planning_prompt_builder_missing");
        }
        return planningPromptBuilder.buildPrompt(request, buildContextSummary(context));
    }

    private Map<String, Object> buildContextSummary(Map<String, Object> context) {
        Map<String, Object> summary = new HashMap<>();
        String snapshotId = resolveSnapshotId(context);
        if (StringUtils.hasText(snapshotId)) {
            summary.put("snapshotId", snapshotId);
        }
        Integer tokenBudget = resolveTokenBudget(context);
        if (tokenBudget != null) {
            summary.put("tokenBudget", tokenBudget);
        }
        Integer memoryItems = resolveMemoryItems(context);
        if (memoryItems != null) {
            summary.put("memoryItems", memoryItems);
        }
        List<String> tools = resolveTools(context);
        if (!tools.isEmpty()) {
            summary.put("tools", tools);
        }
        Integer evidenceCount = resolveEvidenceCount(context);
        if (evidenceCount != null) {
            summary.put("evidenceCount", evidenceCount);
        }
        if (summary.isEmpty()) {
            summary.put("summary", "(summary disabled)");
        }
        return summary;
    }

    private String resolveSnapshotId(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get(PlanningContextKeys.SNAPSHOT_ID);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        ContextSnapshot snapshot = resolveContextSnapshot(context);
        if (snapshot != null && StringUtils.hasText(snapshot.getSnapshotId())) {
            return snapshot.getSnapshotId();
        }
        return null;
    }

    private Integer resolveTokenBudget(Map<String, Object> context) {
        ContextBudgetAllocation allocation = resolveContextBudget(context);
        if (allocation != null && allocation.getTotalTokens() != null) {
            return allocation.getTotalTokens();
        }
        return resolveInt(context != null ? context.get(PlanningContextKeys.BUDGET_THRESHOLD_TOKENS) : null);
    }

    private Integer resolveMemoryItems(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object memoryObj = context.get(PlanningContextKeys.MEMORY);
        if (memoryObj instanceof Map<?, ?> memoryMap) {
            Integer count = resolveInt(memoryMap.get(PlanningContextKeys.COUNT));
            if (count != null) {
                return count;
            }
        }
        ContextSnapshot snapshot = resolveContextSnapshot(context);
        if (snapshot != null && snapshot.getWorkingMemory() != null) {
            return snapshot.getWorkingMemory().getWorkingMemoryItems();
        }
        return null;
    }

    private List<String> resolveTools(Map<String, Object> context) {
        if (context == null) {
            return List.of();
        }
        List<String> tools = new ArrayList<>();
        addToolName(tools, context.get(PlanningContextKeys.TOOL));
        addToolName(tools, context.get(PlanningContextKeys.TOOL_NAME));
        Object toolsObj = context.get(PlanningContextKeys.TOOLS);
        if (toolsObj instanceof List<?> list) {
            for (Object item : list) {
                addToolName(tools, item);
            }
        }
        return tools;
    }

    private void addToolName(List<String> tools, Object value) {
        if (tools == null || value == null) {
            return;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text) || tools.contains(text)) {
            return;
        }
        tools.add(text);
    }

    private Integer resolveEvidenceCount(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object evidence = context.get(EvidencePackService.CONTEXT_EVIDENCE_PACK);
        if (evidence instanceof EvidencePack pack) {
            EvidenceStats stats = pack.getStats();
            if (stats != null && stats.getResearchCount() != null) {
                return stats.getResearchCount();
            }
            if (stats != null && stats.getTotalCount() != null) {
                return stats.getTotalCount();
            }
            if (pack.getEvidences() != null) {
                return pack.getEvidences().size();
            }
        }
        return null;
    }

    private Integer resolveInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private PlanParsingResult tryRepairPlan(String rawContent,
                                            TaskRequest request,
                                            Map<String, Object> context,
                                            String tenantId,
                                            String workflowId,
                                            String planId) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return null;
        }
        String contextJson;
        try {
            Map<String, Object> promptContext = new HashMap<>();
            promptContext.put("query", request != null ? request.getQuery() : null);
            promptContext.put("contextSummary", buildContextSummary(context));
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
        String repaired = jsonOutputRepairService.repair("planner", rawContent, JsonOutputSchema.PLANNER,
                contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        try {
            return parsePlan(repaired, request, context);
        } catch (Exception ex) {
            log.warn("规划修复结果解析失败, tenantId={}, workflowId={}, planId={}, reason={}",
                    tenantId,
                    workflowId,
                    planId,
                    ex.getMessage(),
                    ex);
            return null;
        }
    }

    private void recordPromptTrace(Map<String, Object> metadata,
                                   String promptText,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   String modelId,
                                   boolean parseSuccess,
                                   String parseErrorType,
                                   boolean repairAttempted,
                                   boolean repairSuccess) {
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace == null) {
            trace = PromptTrace.fromPrompt("planner", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "plan", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "missing_field";
    }

    /**
     * 搴旂敤鎻愮ず璇嶈閰嶅櫒骞跺彂甯冭閰嶉樁娈典簨浠躲€?
     *
     * <p>杈撳叆锛氭ā鍨嬭姹傘€佹彁绀鸿瘝涓庝笂涓嬫枃淇℃伅銆?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氳閰嶅櫒涓虹┖鏃剁洿鎺ヨ繑鍥炪€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * applyPromptBundle(modelRequest, prompt, request, context, ctx, wfId, seq);
     * }</pre>
     */
    private void applyPromptBundle(ModelRequest modelRequest,
                                   String prompt,
                                   TaskRequest request,
                                   Map<String, Object> context,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter) {
        if (promptAssembler == null || modelRequest == null) {
            return;
        }
        Map<String, Object> assemblyContext = context != null ? new HashMap<>(context) : new HashMap<>();
        PromptAssemblyInput assemblyInput = buildPromptAssemblyInput(prompt, request, assemblyContext);
        Integer beforeTokens = resolveTokenTotal(assemblyInput != null ? assemblyInput.getBudgetUsedTokens() : null);
        if (assemblyInput != null) {
            assemblyContext.put("promptAssemblyInput", assemblyInput);
        }
        // 灏嗘彁绀鸿瘝杞崲涓烘秷鎭粨鏋勩€?
        PromptBundle bundle = promptAssembler.build(prompt, request, assemblyContext);
        if (bundle != null && bundle.getMessages() != null) {
            modelRequest.setMessages(bundle.getMessages());
        }
        publishPlanStage(tenantContext, workflowId, seqCounter, assemblyContext, assemblyInput, bundle,
                beforeTokens);
    }

    /**
     * 鍙戝竷瑙勫垝鎻愮ず璇嶈閰嶉樁娈典簨浠躲€?
     *
     * <p>杈撳叆锛氱鎴蜂笂涓嬫枃銆佸伐浣滄祦鏍囪瘑涓庤閰嶇粨鏋溿€?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氫簨浠跺彂甯冨櫒涓虹┖鏃剁洿鎺ヨ繑鍥炪€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * publishPlanStage(ctx, wfId, seq, context, input, bundle, beforeTokens);
     * }</pre>
     */
    private void publishPlanStage(TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  Map<String, Object> context,
                                  PromptAssemblyInput assemblyInput,
                                  PromptBundle bundle,
                                  Integer beforeTokens) {
        if (contextEventPublisher == null || tenantContext == null || workflowId == null || bundle == null) {
            return;
        }
        ContextSnapshot snapshot = resolveContextSnapshot(context);
        ContextBudgetAllocation allocation = resolveContextBudget(context);
        Integer afterTokens = resolveTokenTotal(assemblyInput != null ? assemblyInput.getBudgetUsedTokens() : null);
        if (afterTokens == null) {
            afterTokens = bundle.getEstimatedTokens();
        }
        List<String> truncatedSections = bundle.getTruncatedSections() != null
                ? bundle.getTruncatedSections()
                : List.of();
        contextEventPublisher.publishSnapshotStage(
                tenantContext,
                workflowId,
                seqCounter,
                snapshot,
                null,
                allocation,
                null,
                null,
                truncatedSections,
                ContextSnapshotStage.PLAN_ASSEMBLED,
                beforeTokens,
                afterTokens);
    }

    /**
     * 璁＄畻浠ょ墝鎬绘暟銆?
     *
     * <p>杈撳叆锛氫护鐗屾槑缁嗘槧灏勩€?
     * <p>杈撳嚭锛氫护鐗屾€绘暟鎴?{@code null}銆?
     * <p>杈圭晫锛氭槧灏勪负绌烘椂杩斿洖 {@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * Integer total = resolveTokenTotal(tokens);
     * }</pre>
     */
    private Integer resolveTokenTotal(Map<String, Integer> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        Integer total = tokens.get("total");
        if (total != null) {
            return total;
        }
        int sum = 0;
        for (Integer value : tokens.values()) {
            sum += value == null ? 0 : value;
        }
        return sum;
    }

    /**
     * 鏋勫缓鎻愮ず璇嶈閰嶈緭鍏ャ€?
     *
     * <p>杈撳叆锛氭彁绀鸿瘝銆佷换鍔¤姹備笌涓婁笅鏂囨槧灏勩€?
     * <p>杈撳嚭锛氳閰嶈緭鍏ュ璞℃垨 {@code null}銆?
     * <p>杈圭晫锛氳閰嶅櫒涓虹┖鏃惰繑鍥?{@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * PromptAssemblyInput input = buildPromptAssemblyInput(prompt, request, context);
     * }</pre>
     */
    private PromptAssemblyInput buildPromptAssemblyInput(String prompt,
                                                         TaskRequest request,
                                                         Map<String, Object> context) {
        if (contextAssembler == null) {
            return null;
        }
        ContextSnapshot snapshot = resolveContextSnapshot(context);
        ContextBudgetAllocation allocation = resolveContextBudget(context);
        ContextPruneResult pruneResult = resolveContextPrune(context);
        String tenantId = null;
        String workflowId = null;
        if (snapshot != null && snapshot.getRuntimeMeta() != null) {
            if (snapshot.getRuntimeMeta().getTenantId() != null
                    && !snapshot.getRuntimeMeta().getTenantId().isBlank()) {
                tenantId = snapshot.getRuntimeMeta().getTenantId();
            }
            if (snapshot.getRuntimeMeta().getWorkflowId() != null
                    && !snapshot.getRuntimeMeta().getWorkflowId().isBlank()) {
                workflowId = snapshot.getRuntimeMeta().getWorkflowId();
            }
        }
        if (tenantId == null && request != null && request.getContext() != null) {
            Object value = request.getContext().get(PlanningContextKeys.TENANT_ID);
            if (value instanceof String text && !text.isBlank()) {
                tenantId = text;
            }
        }
        if (workflowId == null && context != null && context.get(PlanningContextKeys.WORKFLOW_ID) instanceof String text
                && !text.isBlank()) {
            workflowId = text;
        }
        return contextAssembler.assemble(snapshot, allocation, null, pruneResult, null,
                tenantId, workflowId, prompt);
    }

    /**
     * 瑙ｆ瀽涓婁笅鏂囧揩鐓у璞°€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氫笂涓嬫枃蹇収瀵硅薄鎴?{@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * ContextSnapshot snapshot = resolveContextSnapshot(context);
     * }</pre>
     */
    private ContextSnapshot resolveContextSnapshot(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get(PlanningContextKeys.CONTEXT_SNAPSHOT);
        if (value instanceof ContextSnapshot snapshot) {
            return snapshot;
        }
        return null;
    }

    /**
     * 瑙ｆ瀽涓婁笅鏂囬绠楀垎閰嶅璞°€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氶绠楀垎閰嶅璞℃垨 {@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * ContextBudgetAllocation allocation = resolveContextBudget(context);
     * }</pre>
     */
    private ContextBudgetAllocation resolveContextBudget(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get(PlanningContextKeys.CONTEXT_BUDGET);
        if (value instanceof ContextBudgetAllocation allocation) {
            return allocation;
        }
        return null;
    }

    /**
     * 瑙ｆ瀽涓婁笅鏂囪鍓粨鏋滃璞°€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氳鍓粨鏋滃璞℃垨 {@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * ContextPruneResult prune = resolveContextPrune(context);
     * }</pre>
     */
    private ContextPruneResult resolveContextPrune(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get(PlanningContextKeys.CONTEXT_PRUNE);
        if (value instanceof ContextPruneResult pruneResult) {
            return pruneResult;
        }
        return null;
    }

    /**
     * 瑙ｆ瀽妯″瀷瑙勫垝杈撳嚭銆?
     *
     * <p>杈撳叆锛氭ā鍨嬭緭鍑哄唴瀹广€佷换鍔¤姹備笌涓婁笅鏂囨槧灏勩€?
     * <p>杈撳嚭锛氳В鏋愮粨鏋滃璞℃垨 {@code null}銆?
     * <p>杈圭晫锛氱粨鏋勪笉鍚堟硶鏃惰繑鍥?{@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * PlanParsingResult parsed = parsePlan(content, request, context);
     * }</pre>
     */
    private PlanParsingResult parsePlan(String content, TaskRequest request, Map<String, Object> context)
            throws Exception {
        Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });
        Object stepsObj = root.get("steps");
        if (!(stepsObj instanceof List<?> stepList)) {
            return null;
        }
        List<StepSpec> steps = new ArrayList<>();
        int index = 0;
        for (Object item : stepList) {
            index++;
            if (!(item instanceof Map<?, ?> stepMap)) {
                continue;
            }
            String type = stepMap.get("type") instanceof String typeValue ? typeValue : "TOOL";
            Map<String, Object> input = new HashMap<>();
            if (stepMap.get("input") instanceof Map<?, ?> inputMap) {
                inputMap.forEach((key, value) -> input.put(String.valueOf(key), value));
            }
            if (!input.containsKey("query") && request != null) {
                input.put("query", request.getQuery());
            }
            if (!input.containsKey("context")) {
                input.put("context", context);
            }
            Object toolName = stepMap.get(PlanningContextKeys.TOOL);
            if (toolName instanceof String name && !name.isBlank()) {
                input.putIfAbsent(PlanningContextKeys.TOOL, name);
            }
            if (stepMap.get("dependsOn") instanceof List<?> deps) {
                input.putIfAbsent("dependsOn", deps);
            }
            if (isToolStep(type) && strictToolArguments) {
                String reason = validateToolStepInput(input);
                if (reason != null) {
                    log.warn("规划 TOOL 步骤缺少必要参数, stepIndex={}, reason={}", index, reason);
                    return null;
                }
            }
            steps.add(buildStepSpec(type, input));
        }
        String summary = root.get("summary") instanceof String value ? value : "llm-plan";
        return new PlanParsingResult(summary, steps);
    }

    /**
     * 浠庡姩鎬佽緭鍏ユ槧灏勬瀯寤烘楠よ鏍煎璞°€?
     *
     * <p>椋庨櫓鐐癸細瑙勫垝杈撳嚭鍙兘鍖呭惈浠绘剰瀛楁锛屽繀椤绘樉寮忔媶鍒?context/dependsOn/policy锛岄伩鍏嶅弬鏁版薄鏌撱€?
     *
     * @param type 姝ラ绫诲瀷
     * @param input 杈撳叆鏄犲皠
     * @return 寮虹被鍨嬫楠よ鏍?
     */
    private StepSpec buildStepSpec(String type, Map<String, Object> input) {
        StepSpec step = new StepSpec();
        step.setStepType(type);
        if (input == null || input.isEmpty()) {
            return step;
        }

        Map<String, Object> arguments = new HashMap<>(input);
        Map<String, Object> context = null;
        Object contextObj = arguments.remove("context");
        if (contextObj instanceof Map<?, ?> map) {
            Map<String, Object> contextMap = new HashMap<>();
            map.forEach((key, value) -> contextMap.put(String.valueOf(key), value));
            context = contextMap;
        }
        step.setContext(context);

        List<String> dependsOn = null;
        Object dependsObj = arguments.remove("dependsOn");
        if (dependsObj instanceof List<?> list && !list.isEmpty()) {
            dependsOn = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    dependsOn.add(String.valueOf(item));
                }
            }
            if (dependsOn.isEmpty()) {
                dependsOn = null;
            }
        }
        step.setDependsOn(dependsOn);

        Object requiresApprovalObj = arguments.remove(PlanningContextKeys.REQUIRES_APPROVAL);
        Object approvalSourceObj = arguments.remove(PlanningContextKeys.APPROVAL_SOURCE);
        if (requiresApprovalObj != null || approvalSourceObj != null) {
            StepPolicy policy = new StepPolicy();
            if (requiresApprovalObj instanceof Boolean boolValue) {
                policy.setRequiresApproval(boolValue);
            } else if (requiresApprovalObj instanceof String text && !text.isBlank()) {
                policy.setRequiresApproval(Boolean.parseBoolean(text));
            }
            if (approvalSourceObj != null) {
                policy.setApprovalSource(String.valueOf(approvalSourceObj));
            }
            step.setPolicy(policy);
        }

        step.setArguments(arguments.isEmpty() ? null : arguments);
        return step;
    }

    /**
     * 鍒ゆ柇鏄惁涓?TOOL 姝ラ绫诲瀷銆?
     *
     * @param type 姝ラ绫诲瀷
     * @return 鏄惁涓?TOOL
     */
    private boolean isToolStep(String type) {
        return type != null && "TOOL".equalsIgnoreCase(type);
    }

    /**
     * 鏍￠獙 TOOL 姝ラ鐨勫繀瑕佸瓧娈垫槸鍚﹀畬鏁淬€?
     *
     * <p>杈撳叆锛氭楠よ緭鍏ユ槧灏勩€?
     * <p>杈撳嚭锛氱己澶卞師鍥狅紝杩斿洖 {@code null} 琛ㄧず鏍￠獙閫氳繃銆?
     */
    private String validateToolStepInput(Map<String, Object> input) {
        if (input == null) {
            return "input_empty";
        }
        Object tool = input.get(PlanningContextKeys.TOOL);
        Object toolName = input.get(PlanningContextKeys.TOOL_NAME);
        String resolvedTool = tool instanceof String value && StringUtils.hasText(value)
                ? value
                : toolName != null ? toolName.toString() : null;
        if (!StringUtils.hasText(resolvedTool)) {
            return "missing_tool_name";
        }
        Object arguments = input.get("arguments");
        if (!(arguments instanceof Map<?, ?> map) || map.isEmpty()) {
            return "missing_arguments";
        }
        return null;
    }

    /**
     * 绮楃暐浼拌闂澶嶆潅搴︺€?
     *
     * <p>杈撳叆锛氶棶棰樻枃鏈€?
     * <p>杈撳嚭锛氬鏉傚害鍒嗘暟銆?
     * <p>杈圭晫锛氭枃鏈负绌烘椂杩斿洖浣庡鏉傚害銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * double score = estimateComplexity(query);
     * }</pre>
     */
    private double estimateComplexity(String query) {
        if (query == null || query.isBlank()) {
            return 0.1;
        }
        String trimmed = query.trim();
        int length = trimmed.length();
        int clauses = trimmed.split("[，。！？!?\\s]+").length;
        double lengthScore = Math.min(1.0, length / 200.0);
        double clauseScore = Math.min(0.5, clauses * 0.1);
        return Math.min(1.0, lengthScore + clauseScore);
    }

    /**
     * 瑙ｆ瀽璁ょ煡绛栫暐銆?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠涓庡鏉傚害鍒嗘暟銆?
     * <p>杈撳嚭锛氱瓥鐣ュ悕绉板瓧绗︿覆銆?
     * <p>杈圭晫锛氭湭鎸囧畾鏃朵娇鐢ㄥ鏉傚害鎺ㄦ柇榛樿绛栫暐銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String strategy = resolveCognitiveStrategy(context, score);
     * }</pre>
     */
    private String resolveCognitiveStrategy(Map<String, Object> context, double complexityScore) {
        Object strategy = context.get(PlanningContextKeys.STRATEGY);
        if (strategy == null) {
            strategy = context.get(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY);
        }
        if (strategy instanceof String value && !value.isBlank()) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (complexityScore >= 0.7) {
            return "tree_of_thoughts";
        }
        if (complexityScore >= 0.4) {
            return "reflection";
        }
        return "simple";
    }

    /**
     * 瑙ｆ瀽鎵ц绛栫暐銆?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠涓庡鏉傚害鍒嗘暟銆?
     * <p>杈撳嚭锛氱瓥鐣ュ悕绉板瓧绗︿覆銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String strategy = resolveExecutionStrategy(context, score);
     * }</pre>
     */
    private String resolveExecutionStrategy(Map<String, Object> context, double complexityScore) {
        Object strategy = context.get(PlanningContextKeys.EXECUTION_STRATEGY);
        if (strategy instanceof String value && !value.isBlank()) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (complexityScore >= 0.7) {
            return "sequential";
        }
        return "sequential";
    }

    /**
     * 鍒ゆ柇鏄惁闇€瑕佹€濈淮鏍戠瓥鐣ャ€?
     *
     * <p>杈撳叆锛氳鐭ョ瓥鐣ヤ笌澶嶆潅搴﹀垎鏁般€?
     * <p>杈撳嚭锛氭槸鍚﹂渶瑕佹€濈淮鏍戙€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * boolean needed = needsThoughtTree(strategy, score);
     * }</pre>
     */
    private boolean needsThoughtTree(String cognitiveStrategy, double complexityScore) {
        if (cognitiveStrategy == null) {
            return false;
        }
        if ("tree_of_thoughts".equalsIgnoreCase(cognitiveStrategy)
                || "tot".equalsIgnoreCase(cognitiveStrategy)) {
            return true;
        }
        return complexityScore >= 0.8;
    }

    /**
     * 鍒ゆ柇鏄惁鍚敤鍙嶅簲寮忔墽琛屾ā寮忋€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆佹ā寮忎笌绛栫暐銆?
     * <p>杈撳嚭锛氭槸鍚﹀惎鐢ㄣ€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * boolean enabled = shouldUseReact(context, mode, strategy);
     * }</pre>
     */
    private boolean shouldUseReact(Map<String, Object> context, String mode, String strategy) {
        if ("react".equalsIgnoreCase(mode) || "react".equalsIgnoreCase(strategy)) {
            return true;
        }
        if (context == null) {
            return false;
        }
        Object react = context.get(PlanningContextKeys.REACT);
        if (react == null) {
            react = context.get(PlanningContextKeys.REACT_ENABLED);
        }
        if (react instanceof Boolean value) {
            return value;
        }
        if (react instanceof String text && !text.isBlank()) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /**
     * 鍒ゆ柇鏄惁鏄惧紡绂佺敤宸ュ叿銆?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氭槸鍚︾鐢ㄥ伐鍏枫€?
     */
    private boolean isToolsDisabled(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        if (isTruthy(context.get(PlanningContextKeys.DISABLE_TOOLS))) {
            return true;
        }
        ModelToolChoice choice = parseToolChoice(context.get(PlanningContextKeys.TOOL_CHOICE));
        return choice != null && choice.getMode() == ModelToolChoice.Mode.NONE;
    }

    private ModelToolChoice parseToolChoice(Object raw) {
        if (raw instanceof ModelToolChoice choice) {
            return choice;
        }
        if (raw instanceof String value) {
            return ModelToolChoice.fromString(value);
        }
        if (raw instanceof Map<?, ?> map) {
            String mode = map.get("mode") != null ? map.get("mode").toString() : null;
            if ((mode == null || mode.isBlank()) && map.get("type") != null) {
                mode = map.get("type").toString();
            }
            String name = map.get("toolName") != null ? map.get("toolName").toString() : null;
            if ((name == null || name.isBlank()) && map.get("name") != null) {
                name = map.get("name").toString();
            }
            if (mode != null && "specified".equalsIgnoreCase(mode)) {
                return ModelToolChoice.specified(name);
            }
            ModelToolChoice parsed = ModelToolChoice.fromString(mode);
            if (parsed != null && parsed.getMode() == ModelToolChoice.Mode.SPECIFIED) {
                parsed.setToolName(name);
            }
            return parsed;
        }
        return null;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /**
     * 鍒ゆ柇鏄惁鏄惧紡璇锋眰閾惧紡鎺ㄧ悊銆?
     *
     * <p>杈撳叆锛氭ā寮忋€佺瓥鐣ヤ笌璁ょ煡绛栫暐銆?
     * <p>杈撳嚭锛氭槸鍚︿负閾惧紡鎺ㄧ悊銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * boolean enabled = isChainOfThoughtRequested(mode, strategy, cognitive);
     * }</pre>
     */
    private boolean isChainOfThoughtRequested(String mode, String strategy, String cognitiveStrategy) {
        return isChainOfThoughtValue(mode)
                || isChainOfThoughtValue(strategy)
                || isChainOfThoughtValue(cognitiveStrategy);
    }

    /**
     * 鍒ゆ柇瀛楃涓叉槸鍚﹁〃绀洪摼寮忔帹鐞嗐€?
     *
     * <p>杈撳叆锛氬瓧绗︿覆鍊笺€?
     * <p>杈撳嚭锛氭槸鍚﹀尮閰嶉摼寮忔帹鐞嗗叧閿瓧銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * boolean match = isChainOfThoughtValue("cot");
     * }</pre>
     */
    private boolean isChainOfThoughtValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "cot".equals(normalized)
                || "chain_of_thought".equals(normalized)
                || "chain-of-thought".equals(normalized);
    }

    /**
     * 瑙勫垝瑙ｆ瀽缁撴灉杞戒綋銆?
     *
     * <p>鐢ㄩ€旓細鎵胯浇妯″瀷瑙ｆ瀽鍑虹殑鎽樿涓庢楠ゅ垪琛ㄣ€?
     * <p>绀轰緥锛歿@code new PlanParsingResult("summary", steps)}銆?
     */
    private static class PlanParsingResult {
        private final String summary;
        private final List<StepSpec> steps;

        /**
         * 鏋勯€犺В鏋愮粨鏋溿€?
         *
         * <p>杈撳叆锛氭憳瑕佷笌姝ラ鍒楄〃銆?
         * <p>杈撳嚭锛氳В鏋愮粨鏋滃璞°€?
         * <p>绀轰緥锛?
         * <pre>{@code
         * new PlanParsingResult("summary", steps);
         * }</pre>
         *
         * @param summary 鎽樿
         * @param steps 姝ラ鍒楄〃
         */
        private PlanParsingResult(String summary, List<StepSpec> steps) {
            this.summary = summary;
            this.steps = steps;
        }
    }
}

