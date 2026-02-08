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
 * 瑙勫垝鏈嶅姟锛岃礋璐ｅ熀浜庝换鍔＄敓鎴愬彲鎵ц姝ラ銆?
 * <p>鐢ㄩ€旓細鏍规嵁杈撳叆闂涓庝笂涓嬫枃閫夋嫨瑙勫垝绛栫暐骞剁敓鎴愭楠ゃ€?
 * <p>杈撳叆锛氫换鍔¤姹備笌绉熸埛涓婁笅鏂囥€?
 * <p>杈撳嚭锛氳鍒掔粨鏋滃璞°€?
 * <p>杈圭晫锛氬綋绂佺敤鍥為€€涓旇鍒掑け璐ユ椂鎶涘嚭寮傚父銆?
 * <p>绀轰緥锛?
 * <pre>{@code
 * PlanResult plan = plannerService.plan(request, tenantContext);
 * }</pre>
 */
@Service
public class PlannerService {

    /**
     * 鏃ュ織璁板綍鍣ㄣ€?
     * <p>绀轰緥锛氳褰曡鍒掔敓鎴愮粨鏋滀笌鎽樿銆?
     */
    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    /**
     * TOOL 姝ラ鍙傛暟鏍￠獙寮€鍏筹紝寮€鍚悗缂哄弬浼氳Е鍙戝洖閫€銆?
     */
    @Value("${agent.planner.strict-tool-arguments:false}")
    private boolean strictToolArguments;


    /**
     * 妯″瀷璋冪敤鏈嶅姟銆?
     * <p>绀轰緥锛氳皟鐢ㄦā鍨嬬敓鎴愯鍒掓楠ゃ€?
     */
    private final ModelInvocationService modelInvocationService;
    /**
     * 宸ュ叿瑙ｆ瀽鍣ㄣ€?
     * <p>绀轰緥锛氬皢宸ュ叿閰嶇疆娉ㄥ叆妯″瀷璇锋眰銆?
     */
    private final ModelToolResolver modelToolResolver;
    /**
     * 鎻愮ず璇嶈閰嶅櫒銆?
     * <p>绀轰緥锛氱敓鎴愮粨鏋勫寲娑堟伅鍒楄〃銆?
     */
    private final PromptAssembler promptAssembler;
    private final JsonOutputRepairService jsonOutputRepairService;
    /**
     * 瑙勫垝鐩稿叧閰嶇疆銆?
     * <p>绀轰緥锛氭帶鍒舵槸鍚﹀惎鐢ㄦā鍨嬭鍒掋€?
     */
    private final PlannerProperties plannerProperties;
    /**
     * 鑳藉姏杈圭晫璇勪及鍣ㄣ€?
     * <p>绀轰緥锛氭牴鎹闄╁喅瀹氭槸鍚﹂渶瑕佸鎵广€?
     */
    private final CapabilityBoundaryEvaluator capabilityBoundaryEvaluator;
    /**
     * 搴忓垪鍖栧伐鍏枫€?
     * <p>绀轰緥锛氬皢涓婁笅鏂囪浆鎹负 {@code JSON}銆?
     */
    private final ObjectMapper objectMapper;
    /**
     * 涓婁笅鏂囪閰嶅櫒銆?
     * <p>绀轰緥锛氭瀯寤烘彁绀鸿瘝鎵€闇€鐨勪笂涓嬫枃鐗囨銆?
     */
    private final ContextAssembler contextAssembler;
    /**
     * 涓婁笅鏂囦簨浠跺彂甯冨櫒銆?
     * <p>绀轰緥锛氬彂甯冩彁绀鸿瘝瑁呴厤闃舵浜嬩欢銆?
     */
    private final ContextEventPublisher contextEventPublisher;

    /**
     * 鏋勯€犺鍒掓湇鍔°€?
     *
     * <p>杈撳叆锛氭ā鍨嬭皟鐢ㄦ湇鍔°€佸伐鍏疯В鏋愬櫒涓庨厤缃璞°€?
     * <p>杈撳嚭锛氬垵濮嬪寲鍚庣殑瑙勫垝鏈嶅姟銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * new PlannerService(invocationService, toolResolver, promptAssembler, props, evaluator, mapper, assembler, publisher);
     * }</pre>
     *
     * @param modelInvocationService 妯″瀷璋冪敤鏈嶅姟
     * @param modelToolResolver 宸ュ叿瑙ｆ瀽鍣?
     * @param promptAssembler 鎻愮ず璇嶈閰嶅櫒
     * @param plannerProperties 瑙勫垝閰嶇疆
     * @param capabilityBoundaryEvaluator 鑳藉姏璇勪及鍣?
     * @param objectMapper 搴忓垪鍖栧伐鍏?
     * @param contextAssembler 涓婁笅鏂囪閰嶅櫒
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
                          JsonOutputRepairService jsonOutputRepairService) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.plannerProperties = plannerProperties;
        this.capabilityBoundaryEvaluator = capabilityBoundaryEvaluator;
        this.objectMapper = objectMapper;
        this.contextAssembler = contextAssembler;
        this.contextEventPublisher = contextEventPublisher;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 鐢熸垚瑙勫垝缁撴灉銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹備笌绉熸埛涓婁笅鏂囥€?
     * <p>杈撳嚭锛氳鍒掔粨鏋滃璞°€?
     * <p>杈圭晫锛氫細杞皟甯︿笂涓嬫枃鐨勬柟娉曪紝淇濇寔缁熶竴閫昏緫銆?
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
     * 甯﹁繍琛屼笂涓嬫枃鐨勮鍒掑叆鍙ｏ紝鐢ㄤ簬鍙戝竷妯″瀷浜嬩欢銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹傘€佺鎴蜂笂涓嬫枃涓庨摼璺爣璇嗐€?
     * <p>杈撳嚭锛氳鍒掔粨鏋滃璞°€?
     * <p>杈圭晫锛氬綋妯″瀷瑙勫垝澶辫触涓旂鐢ㄥ洖閫€鏃舵姏鍑哄紓甯搞€?
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
        if (request != null && request.getToolChoice() != null && !context.containsKey("toolChoice")) {
            context.put("toolChoice", request.getToolChoice());
        }
        // 璇勪及鑳藉姏杈圭晫锛屽喅瀹氭槸鍚﹂渶瑕佸鎵规垨鎺ㄨ崘绛栫暐銆?
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
     * 鎵ц鑳藉姏杈圭晫璇勪及銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹傘€佷笂涓嬫枃涓庨摼璺俊鎭€?
     * <p>杈撳嚭锛氳瘎浼扮粨鏋滃璞°€?
     * <p>杈圭晫锛氳瘎浼板櫒鏈惎鐢ㄦ椂杩斿洖 {@code null}銆?
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
        // 鏋勫缓璇勪及杈撳叆锛屽寘鍚棶棰樸€佸伐鍏锋憳瑕佷笌棰勭畻淇℃伅銆?
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
     * <p>杈圭晫锛氳瘎浼拌璺宠繃鏃朵笉鍐欏叆銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * applyEvaluationToContext(context, evaluation);
     * }</pre>
     */
    private void applyEvaluationToContext(Map<String, Object> context, CapabilityEvaluationResult evaluation) {
        if (context == null || evaluation == null || evaluation.isSkipped()) {
            return;
        }
        if (evaluation.isShouldAskApproval() && !context.containsKey("requiresApproval")) {
            context.put("requiresApproval", true);
            context.putIfAbsent("approvalSource", "evaluation");
        }
        if (!hasExplicitStrategy(context) && evaluation.getRecommendedStrategy() != null) {
            mapStrategyToContext(context, evaluation.getRecommendedStrategy());
        }
        context.put("capabilityScore", evaluation.getComplexityScore());
        context.put("capabilityRisk", evaluation.getRiskLevel() != null
                ? evaluation.getRiskLevel().name()
                : null);
    }

    /**
     * 灏嗗鎵硅姹傜粦瀹氬埌瑙勫垝姝ラ銆?
     *
     * <p>杈撳叆锛氳鍒掔粨鏋溿€佷换鍔¤姹備笌璇勪及缁撴灉銆?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氭棤姝ラ鎴栧凡鏄惧紡鎸囧畾瀹℃壒鏃朵笉澶勭悊銆?
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
     * 鍒ゆ柇浠诲姟璇锋眰鏄惁鏄惧紡瑕佹眰瀹℃壒銆?
     *
     * <p>杈撳叆锛氫换鍔¤姹傚璞°€?
     * <p>杈撳嚭锛氭槸鍚﹀瓨鍦ㄥ鎵规爣璁般€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * boolean required = hasExplicitApproval(request);
     * }</pre>
     */
    private boolean hasExplicitApproval(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return false;
        }
        return request.getContext().containsKey("requiresApproval");
    }

    /**
     * 鍒ゆ柇姝ラ鍒楄〃涓槸鍚︽樉寮忔爣璁板鎵广€?
     *
     * <p>杈撳叆锛氭楠ゅ垪琛ㄣ€?
     * <p>杈撳嚭锛氭槸鍚﹀瓨鍦ㄥ鎵规爣璁般€?
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
     * 鏍囪姝ラ闇€瑕佸鎵广€?
     *
     * <p>杈撳叆锛氭楠ゅ璞′笌鏉ユ簮鏍囪瘑銆?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氭楠や负绌烘椂鐩存帴杩斿洖銆?
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
     * 鍒ゆ柇涓婁笅鏂囦腑鏄惁鏄惧紡鎸囧畾绛栫暐銆?
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
        return context.containsKey("strategy")
                || context.containsKey("mode")
                || context.containsKey("cognitive_strategy")
                || context.containsKey("react")
                || context.containsKey("reactEnabled");
    }

    /**
     * 灏嗘帹鑽愮瓥鐣ユ槧灏勫埌涓婁笅鏂囧瓧娈点€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠涓庣瓥鐣ュ悕绉般€?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氱瓥鐣ヤ负绌烘椂涓嶅鐞嗐€?
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
            context.put("cognitive_strategy", "tree_of_thoughts");
            return;
        }
        if ("debate".equals(normalized)) {
            context.put("strategy", "debate");
            return;
        }
        if ("research".equals(normalized)) {
            context.put("mode", "deep_research");
            context.put("strategy", "research");
            return;
        }
        if ("react".equals(normalized)) {
            context.put("react", true);
        }
    }

    /**
     * 鑾峰彇涓婁笅鏂囦腑鐨勮鍒掓憳瑕併€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氳鍒掓憳瑕佸瓧绗︿覆鎴?{@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String summary = resolvePlanSummary(context);
     * }</pre>
     */
    private String resolvePlanSummary(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object summary = context.get("planSummary");
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
        Object tool = context.get("tool");
        Object toolName = context.get("toolName");
        Object fallbackTool = context.get("fallbackTool");
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
     * 浠ラ€楀彿鎷兼帴瀛楃涓层€?
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
     * 鑾峰彇棰勭畻闃堝€奸厤缃€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氶槇鍊兼暣鏁帮紝榛樿 {@code 0}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * int threshold = resolveBudgetThreshold(context);
     * }</pre>
     */
    private int resolveBudgetThreshold(Map<String, Object> context) {
        if (context == null) {
            return 0;
        }
        Object threshold = context.get("budgetThresholdTokens");
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
        Object failures = context.get("failureTypes");
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
     * <p>杈撳叆锛氫换鍔¤姹傘€佺鎴蜂笂涓嬫枃涓庝笂涓嬫枃淇℃伅銆?
     * <p>杈撳嚭锛氳鍒掔粨鏋滃璞℃垨 {@code null}銆?
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
        try {
            // 鏋勯€犺鍒掓彁绀鸿瘝骞剁敓鎴愭ā鍨嬭姹傘€?
            String prompt = buildPlanPrompt(request, context);
            ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.PLANNER);
            applyPromptBundle(modelRequest, prompt, request, context, tenantContext, workflowId, seqCounter);
            // 瑙勫垝闃舵寮哄埗娉ㄥ叆瀹屾暣宸ュ叿 schema锛屾彁鍗囧弬鏁扮敓鎴愬彲闈犳€?
            modelToolResolver.applyTooling(modelRequest, request, null, true);
            // 璋冪敤妯″瀷鐢熸垚瑙勫垝鍐呭銆?
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("planId", planId);
            metadata.put("promptScene", "planner");
            ModelResponse response = modelInvocationService.invoke(
                    modelRequest,
                    ModelScene.PLANNER,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    "plan",
                    metadata
            );
            if (response == null || response.getContent() == null) {
                return null;
            }
            // 瑙ｆ瀽妯″瀷杈撳嚭涓鸿鍒掓楠ゃ€?
            String rawContent = response.getContent();
            String parseErrorType = null;
            boolean repairAttempted = false;
            boolean repairSuccess = false;
            PlanParsingResult parsed;
            try {
                parsed = parsePlan(rawContent, request, context);
            } catch (Exception ex) {
                log.warn("瑙勫垝瑙ｆ瀽澶辫触, tenantId={}, planId={}, reason={}",
                        tenantContext.getTenantId(), planId, ex.getMessage());
                parsed = null;
                parseErrorType = "json_parse_error";
            }
            if (parsed == null || parsed.steps == null || parsed.steps.isEmpty()) {
                if (parseErrorType == null) {
                    parseErrorType = resolveParseErrorType(rawContent);
                }
                repairAttempted = true;
                PlanParsingResult repaired = tryRepairPlan(rawContent, request, context);
                if (repaired != null && repaired.steps != null && !repaired.steps.isEmpty()) {
                    parsed = repaired;
                    repairSuccess = true;
                }
            }
            if (parsed == null || parsed.steps == null || parsed.steps.isEmpty()) {
                log.warn("瑙勫垝淇澶辫触, tenantId={}, planId={}", tenantContext.getTenantId(), planId);
                recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), false,
                        parseErrorType, repairAttempted, repairSuccess);
                return null;
            }
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), true, null,
                    repairAttempted, repairSuccess);
            PlanResult result = new PlanResult(planId, parsed.summary, parsed.steps);
            log.info("瑙勫垝鐢熸垚(LLM), tenantId={}, planId={}, steps={}",
                    tenantContext.getTenantId(), planId, parsed.steps.size());
            return result;
        } catch (Exception ex) {
            log.warn("瑙勫垝瑙ｆ瀽澶辫触, tenantId={}, planId={}, reason={}",
                    tenantContext.getTenantId(), planId, ex.getMessage());
            return null;
        }
    }

    /**
     * 浣跨敤瑙勫垯绛栫暐鐢熸垚瑙勫垝銆?
     *
     * <p>杈撳叆锛氳鍒掓爣璇嗐€侀棶棰樹笌涓婁笅鏂囦俊鎭€?
     * <p>杈撳嚭锛氳鍒掔粨鏋滃璞°€?
     * <p>杈圭晫锛氶棶棰樹负绌烘椂浣跨敤榛樿澶嶆潅搴︺€?
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
            String mode = context.get("mode") instanceof String value ? value.toLowerCase(Locale.ROOT) : "";
        String strategy = context.get("strategy") instanceof String value ? value.toLowerCase(Locale.ROOT) : "";

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
            log.info("瑙勫垝鐢熸垚(閾惧紡鎺ㄧ悊), tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put("planSteps", planSteps);
            context.put("planDependencies", dependencies);
            context.put("executionStrategy", executionStrategy);
            context.put("cognitiveStrategy", cognitiveStrategy);
            return result;
        }
        // 闇€瑕佹€濈淮鏍戠瓥鐣ユ椂鍏堟彃鍏ユ€濈淮鏍戞楠ゃ€?
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

        // 杈╄绛栫暐銆?
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
            log.info("瑙勫垝鐢熸垚锛圧eAct锛? tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put("planSteps", planSteps);
            context.put("planDependencies", dependencies);
            context.put("executionStrategy", executionStrategy);
            context.put("cognitiveStrategy", cognitiveStrategy);
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

            log.info("瑙勫垝鐢熸垚(澶фā鍨?, tenantId={}, planId={}, summary={}",
                    tenantContext.getTenantId(), planId, summary);
            context.put("planSteps", planSteps);
            context.put("planDependencies", dependencies);
            context.put("executionStrategy", executionStrategy);
            context.put("cognitiveStrategy", cognitiveStrategy);
            return result;
        }

        // 榛樿宸ュ叿姝ラ銆?
        String toolStepKey = previousStepKey == null ? "step-1" : "step-" + (steps.size() + 1);
        Map<String, Object> toolInput = new HashMap<>();
        toolInput.put("query", query);
        toolInput.put("context", context);
        toolInput.put("stepKey", toolStepKey);
        toolInput.put("critical", complexityScore >= 0.6);
        toolInput.put("strategy", cognitiveStrategy);
        Object toolName = context.get("tool");
        if (toolName instanceof String name && !name.isBlank()) {
            toolInput.put("tool", name);
        }
        Object fallbackTool = context.get("fallbackTool");
        if (fallbackTool instanceof String fallbackName && !fallbackName.isBlank()) {
            toolInput.put("fallbackTool", fallbackName);
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

        log.info("瑙勫垝鐢熸垚(瑙勫垯), tenantId={}, planId={}, summary={}",
                tenantContext.getTenantId(), planId, summary);
        context.put("planSteps", planSteps);
        context.put("planDependencies", dependencies);
        context.put("executionStrategy", executionStrategy);
        context.put("cognitiveStrategy", cognitiveStrategy);
        return result;
    }

    /**
     * 鏋勫缓瑙勫垝鎻愮ず璇嶃€?
     *
     * <p>杈撳叆锛氫换鍔¤姹備笌涓婁笅鏂囨槧灏勩€?
     * <p>杈撳嚭锛氭彁绀鸿瘝瀛楃涓层€?
     * <p>杈圭晫锛氬簭鍒楀寲澶辫触鏃朵娇鐢ㄧ┖涓婁笅鏂囥€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String prompt = buildPlanPrompt(request, context);
     * }</pre>
     */
    private String buildPlanPrompt(TaskRequest request, Map<String, Object> context) {
        Map<String, Object> promptContext = new HashMap<>();
        promptContext.put("query", request != null ? request.getQuery() : null);
        promptContext.put("contextSummary", buildContextSummary(context));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(promptContext);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
                浣犳槸浠诲姟瑙勫垝鍣紙planner锛夈€備綘鐨勪换鍔℃槸锛氭牴鎹?PLAN_CONTEXT_JSON 涓殑 query 涓庝笂涓嬫枃锛岀敓鎴愨€滄渶灏忎笖鍙墽琛屸€濈殑姝ラ璁″垝銆?
                
                銆愯鍒掑師鍒欍€?
                1) 鏈€灏忓寲锛氬繀椤昏緭鍑鸿嚦灏?1 涓楠わ紝鑳藉皯姝ヨВ鍐冲氨涓嶈鍑烘楠わ紱鑳?1 姝ヨВ鍐冲氨涓嶈鎷?3 姝ャ€?
                2) 鍙墽琛岋細姣忎釜姝ラ蹇呴』鑳借鎵ц鍣ㄧ洿鎺ユ墽琛岋紙step.type 涓?step.input 蹇呴』鑷唇锛夈€?
                3) 涓嶇紪閫狅細涓嶅緱缂栭€犲閮ㄦ暟鎹粨鏋滐紱鑻ラ渶瑕佹煡璇㈡暟鎹簮锛屽繀椤昏鍒掑伐鍏锋楠ゃ€?
                4) 鍖哄垎涓ょ被闂锛?
                   - DIRECT锛氬父璇嗚В閲?姒傚康璇存槑/绾枃鏈敓鎴愶紝涓嶉渶瑕佸伐鍏凤紝蹇呴』杈撳嚭鑷冲皯 1 涓楠?
                   - TOOL锛氶渶瑕佸閮ㄦ暟鎹?妫€绱?鏁版嵁搴撴煡璇?璋冪敤绯荤粺鎺ュ彛锛屽繀椤昏緭鍑鸿嚦灏?1 涓楠?
                
                銆恠teps=[蹇呴』杈撳嚭鑷冲皯 1 涓楠銆?
                - 闂灞炰簬 DIRECT锛堣В閲婄被銆佸畾涔夌被銆佹敼鍐?鎬荤粨绫荤瓑锛夛紝涓旀棤闇€浠讳綍澶栭儴鏁版嵁銆?
                  summary 鍐欐槑鈥滄棤闇€宸ュ叿锛岀洿鎺ュ洖绛斺€濓紝骞跺彲杈撳嚭 answerMode="DIRECT"銆?
                
                --------------------------------------------------
                銆愬叧閿崗璁紙涓庤繍琛屾椂涓ユ牸瀵归綈锛夈€?
                
                鍙杈撳嚭 steps锛堝嵆浜х敓浠绘剰 step锛夛紝蹇呴』閬靛畧锛?
                
                1) steps[*].input锛?
                   - 蹇呴』鏄?object
                   - 蹇呴』鍖呭惈 input.question锛堝瓧绗︿覆锛屼笉鑳戒负绌猴級
                   - input.question 琛ㄧず鈥滆姝ラ姝ｅ湪鍋氫粈涔?/ 璇ユ楠よ澶勭悊鐨勫瓙闂鏄粈涔堚€?
                   - 杩愯鏃跺皢浼樺厛浣跨敤 input.question 浣滀负璇ユ楠ょ殑闂鏂囨湰
                
                2) steps[*].tool锛?
                   - 涓嶆槸蹇呭～锛屽彲鐪佺暐鎴栦负绌轰覆
                   - 浣嗗綋 step.type="TOOL" 鏃讹紝蹇呴』淇濊瘉鎵ц鍣ㄨ兘瀹氫綅鍒板叿浣撳伐鍏凤細
                     - 浼樺厛浣跨敤 steps[*].tool锛堣嫢濉啓锛?
                     - 鑻?steps[*].tool 涓虹┖锛屽垯 steps[*].input 蹇呴』鍖呭惈 toolName锛堝瓧绗︿覆锛屼笉鑳戒负绌猴級
                     - 鍚﹀垯璇?TOOL 姝ラ涓嶅彲鎵ц锛堜弗绂佽緭鍑猴級
                
                3) TOOL 姝ラ input 瑙勮寖锛?
                   - 蹇呴』鍖呭惈锛?
                     - question: 鎻忚堪鏈宸ュ叿璋冪敤鎰忓浘锛堝繀濉級
                     - arguments: object锛屼粎鍖呭惈璇ュ伐鍏烽渶瑕佺殑瀛楁锛堥伩鍏嶅鍒舵暣娈?query锛?
                     - toolName锛氬綋 steps[*].tool 涓虹┖鏃跺繀濉?
                   - 涓嶅厑璁稿彧鏈夊伐鍏峰弬鏁拌€屾病鏈?question
                   - arguments 缂哄け鎴栦负绌烘椂锛岀姝㈣緭鍑?TOOL 姝ラ
                
                4) 闈?TOOL 姝ラ锛圠LM/COT/REACT锛?input 瑙勮寖锛?
                   - 蹇呴』鍖呭惈锛?
                     - question: 璇ユ楠よ鐢熸垚/鎬荤粨/瑙ｉ噴鐨勫瓙闂锛堝繀濉級
                   - 鍙寘鍚皯閲忓唴閮ㄥ鐞嗗弬鏁帮紙濡傚幓閲嶅瓧娈?distinctKey锛夛紝浣嗙姝㈠鍏ラ暱鏂囨湰鎴栭噸澶嶄笂涓嬫枃銆?
                
                --------------------------------------------------
                銆愬吀鍨嬫ā寮忥細鏌ヨ + 姹囨€?/ 缁熻 / 鍘婚噸銆?
                
                褰?query 鍚屾椂鍖呭惈锛?
                鈥滃娆℃煡璇⑩€?+ 鈥滄渶鍚庢眹鎬?缁熻/瀵规瘮/鍘婚噸/鎬荤粨鈥?
                
                蹇呴』瑙勫垝涓猴細
                
                Step1..N锛氬涓?TOOL 鏌ヨ姝ラ锛堟瘡涓兘瑕佹湁 input.question锛? 
                StepN+1锛氫竴涓眹鎬绘楠わ紙FINAL 鎴?THINK锛屽繀椤绘湁 input.question锛?
                
                姹囨€绘楠よ姹傦細
                - type 浣跨敤 "FINAL"锛堣嫢鎵ц鍣ㄤ笉鏀寔鍙敤 "THINK"锛?
                - tool 鍙渷鐣ユ垨涓虹┖涓?
                - dependsOn 鎸囧悜鎵€鏈?TOOL 姝ラ
                - input.question 娓呮櫚鎻忚堪姹囨€昏姹傦紙濡傦細鍚堝苟缁撴灉銆佹寜 userId 鍘婚噸銆佺粺璁℃暟閲忓苟杈撳嚭鎽樿锛?
                
                --------------------------------------------------
                銆愭楠ゅ瓧娈佃姹傘€?
                - steps[*].type锛?
                  - 缂虹渷涓?"LLM"
                  - 浠呭湪闇€瑕佹枃鏈敓鎴?姹囨€?鍐呴儴澶勭悊鏃朵娇鐢?"FINAL"/"THINK"/"LLM"锛堜互鎵ц鍣ㄦ敮鎸佷负鍑嗭級
                
                - steps[*].tool锛?
                  - 鍙己鐪佹垨涓虹┖涓?
                  - 鑻?type="TOOL" 涓?tool 涓虹┖锛屽垯 input.toolName 蹇呴』闈炵┖
                
                - steps[*].input锛?
                  - 蹇呴』鏄?object
                  - 蹇呴』鍖呭惈 question锛堝繀濉級
                  - TOOL 姝ラ蹇呴』鍖呭惈 arguments锛坥bject锛夛紱蹇呰鏃跺寘鍚?toolName
                
                - steps[*].dependsOn锛?
                  - 榛樿 []
                  - 鏈変緷璧栨椂鎵嶅～鍐?
                
                --------------------------------------------------
                銆愯緭鍑虹害鏉熴€?
                杈撳嚭蹇呴』鏄崟涓?JSON 瀵硅薄锛屼笉鍏佽浠讳綍棰濆鏂囨湰锛屼笉鍏佽 Markdown/浠ｇ爜鍧椼€?
                
                瀛楁绾︽潫锛?
                1) summary: string锛岀己淇℃伅濉┖涓诧紱鏃犳硶缁欏嚭鏈夋晥姝ラ鏃剁敤 summary 璇存槑鍘熷洜銆?
                2) steps: array锛岀己淇℃伅濉?[]銆?
                3) steps[*].type: string锛岀己淇℃伅濉?"TOOL"銆?
                4) steps[*].input: object锛屽繀椤绘槸 object锛屼笖蹇呴』鍖呭惈 question銆?
                5) steps[*].tool: string锛屽彲缂虹渷锛岀己淇℃伅濉┖涓层€?
                6) steps[*].dependsOn: array锛屽彲缂虹渷锛岀己淇℃伅濉?[]銆?
                
                鍏佽棰濆瀛楁浣嗕笉瑕佷緷璧栵紙鎺ㄨ崘锛夛細
                - answerMode: "DIRECT" 鎴?"TOOL"
                - toolRequired: boolean
                
                褰撴棤娉曠‘瀹?action/step 鏃讹紝杈撳嚭 steps=[]锛宻ummary 鍐欐槑鍘熷洜銆?
                
                鏈€灏忕ず渚?JSON锛?
                {"summary":"","steps":[]}
                
                PLAN_CONTEXT_JSON:%s
                """.formatted(contextJson);


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
        Object value = context.get("snapshotId");
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
        return resolveInt(context != null ? context.get("budgetThresholdTokens") : null);
    }

    private Integer resolveMemoryItems(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object memoryObj = context.get("memory");
        if (memoryObj instanceof Map<?, ?> memoryMap) {
            Integer count = resolveInt(memoryMap.get("count"));
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
        addToolName(tools, context.get("tool"));
        addToolName(tools, context.get("toolName"));
        Object toolsObj = context.get("tools");
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

    private PlanParsingResult tryRepairPlan(String rawContent, TaskRequest request, Map<String, Object> context) {
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
            log.warn("瑙勫垝淇瑙ｆ瀽澶辫触, reason={}", ex.getMessage());
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
     * 搴旂敤鎻愮ず璇嶈閰嶅櫒骞跺彂甯冭閰嶉樁娈典簨浠躲€?
     *
     * <p>杈撳叆锛氭ā鍨嬭姹傘€佹彁绀鸿瘝涓庝笂涓嬫枃淇℃伅銆?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氳閰嶅櫒涓虹┖鏃剁洿鎺ヨ繑鍥炪€?
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
        // 灏嗘彁绀鸿瘝杞崲涓烘秷鎭粨鏋勩€?
        PromptBundle bundle = promptAssembler.build(prompt, request, assemblyContext);
        if (bundle != null && bundle.getMessages() != null) {
            modelRequest.setMessages(bundle.getMessages());
        }
        publishPlanStage(tenantContext, workflowId, seqCounter, assemblyContext, assemblyInput, bundle,
                beforeTokens);
    }

    /**
     * 鍙戝竷瑙勫垝鎻愮ず璇嶈閰嶉樁娈典簨浠躲€?
     *
     * <p>杈撳叆锛氱鎴蜂笂涓嬫枃銆佸伐浣滄祦鏍囪瘑涓庤閰嶇粨鏋溿€?
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
     * 鏋勫缓鎻愮ず璇嶈閰嶈緭鍏ャ€?
     *
     * <p>杈撳叆锛氭彁绀鸿瘝銆佷换鍔¤姹備笌涓婁笅鏂囨槧灏勩€?
     * <p>杈撳嚭锛氳閰嶈緭鍏ュ璞℃垨 {@code null}銆?
     * <p>杈圭晫锛氳閰嶅櫒涓虹┖鏃惰繑鍥?{@code null}銆?
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
            Object value = request.getContext().get("tenantId");
            if (value instanceof String text && !text.isBlank()) {
                tenantId = text;
            }
        }
        if (workflowId == null && context != null && context.get("workflowId") instanceof String text
                && !text.isBlank()) {
            workflowId = text;
        }
        return contextAssembler.assemble(snapshot, allocation, null, pruneResult, null,
                tenantId, workflowId, prompt);
    }

    /**
     * 瑙ｆ瀽涓婁笅鏂囧揩鐓у璞°€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氫笂涓嬫枃蹇収瀵硅薄鎴?{@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * ContextSnapshot snapshot = resolveContextSnapshot(context);
     * }</pre>
     */
    private ContextSnapshot resolveContextSnapshot(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get("contextSnapshot");
        if (value instanceof ContextSnapshot snapshot) {
            return snapshot;
        }
        return null;
    }

    /**
     * 瑙ｆ瀽涓婁笅鏂囬绠楀垎閰嶅璞°€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氶绠楀垎閰嶅璞℃垨 {@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * ContextBudgetAllocation allocation = resolveContextBudget(context);
     * }</pre>
     */
    private ContextBudgetAllocation resolveContextBudget(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get("contextBudget");
        if (value instanceof ContextBudgetAllocation allocation) {
            return allocation;
        }
        return null;
    }

    /**
     * 瑙ｆ瀽涓婁笅鏂囪鍓粨鏋滃璞°€?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氳鍓粨鏋滃璞℃垨 {@code null}銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * ContextPruneResult prune = resolveContextPrune(context);
     * }</pre>
     */
    private ContextPruneResult resolveContextPrune(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get("contextPrune");
        if (value instanceof ContextPruneResult pruneResult) {
            return pruneResult;
        }
        return null;
    }

    /**
     * 瑙ｆ瀽妯″瀷瑙勫垝杈撳嚭銆?
     *
     * <p>杈撳叆锛氭ā鍨嬭緭鍑哄唴瀹广€佷换鍔¤姹備笌涓婁笅鏂囨槧灏勩€?
     * <p>杈撳嚭锛氳В鏋愮粨鏋滃璞℃垨 {@code null}銆?
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
            Object toolName = stepMap.get("tool");
            if (toolName instanceof String name && !name.isBlank()) {
                input.putIfAbsent("tool", name);
            }
            if (stepMap.get("dependsOn") instanceof List<?> deps) {
                input.putIfAbsent("dependsOn", deps);
            }
            if (isToolStep(type) && strictToolArguments) {
                String reason = validateToolStepInput(input);
                if (reason != null) {
                    log.warn("瑙勫垝 TOOL 姝ラ缂哄皯蹇呰鍙傛暟, stepIndex={}, reason={}", index, reason);
                    return null;
                }
            }
            steps.add(buildStepSpec(type, input));
        }
        String summary = root.get("summary") instanceof String value ? value : "llm-plan";
        return new PlanParsingResult(summary, steps);
    }

    /**
     * 浠庡姩鎬佽緭鍏ユ槧灏勬瀯寤烘楠よ鏍煎璞°€?
     *
     * <p>椋庨櫓鐐癸細瑙勫垝杈撳嚭鍙兘鍖呭惈浠绘剰瀛楁锛屽繀椤绘樉寮忔媶鍒?context/dependsOn/policy锛岄伩鍏嶅弬鏁版薄鏌撱€?
     *
     * @param type 姝ラ绫诲瀷
     * @param input 杈撳叆鏄犲皠
     * @return 寮虹被鍨嬫楠よ鏍?
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

        Object requiresApprovalObj = arguments.remove("requiresApproval");
        Object approvalSourceObj = arguments.remove("approvalSource");
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
     * 鍒ゆ柇鏄惁涓?TOOL 姝ラ绫诲瀷銆?
     *
     * @param type 姝ラ绫诲瀷
     * @return 鏄惁涓?TOOL
     */
    private boolean isToolStep(String type) {
        return type != null && "TOOL".equalsIgnoreCase(type);
    }

    /**
     * 鏍￠獙 TOOL 姝ラ鐨勫繀瑕佸瓧娈垫槸鍚﹀畬鏁淬€?
     *
     * <p>杈撳叆锛氭楠よ緭鍏ユ槧灏勩€?
     * <p>杈撳嚭锛氱己澶卞師鍥狅紝杩斿洖 {@code null} 琛ㄧず鏍￠獙閫氳繃銆?
     */
    private String validateToolStepInput(Map<String, Object> input) {
        if (input == null) {
            return "input_empty";
        }
        Object tool = input.get("tool");
        Object toolName = input.get("toolName");
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
     * 绮楃暐浼拌闂澶嶆潅搴︺€?
     *
     * <p>杈撳叆锛氶棶棰樻枃鏈€?
     * <p>杈撳嚭锛氬鏉傚害鍒嗘暟銆?
     * <p>杈圭晫锛氭枃鏈负绌烘椂杩斿洖浣庡鏉傚害銆?
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
        int clauses = trimmed.split("[锛屻€?!?\\s]+").length;
        double lengthScore = Math.min(1.0, length / 200.0);
        double clauseScore = Math.min(0.5, clauses * 0.1);
        return Math.min(1.0, lengthScore + clauseScore);
    }

    /**
     * 瑙ｆ瀽璁ょ煡绛栫暐銆?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠涓庡鏉傚害鍒嗘暟銆?
     * <p>杈撳嚭锛氱瓥鐣ュ悕绉板瓧绗︿覆銆?
     * <p>杈圭晫锛氭湭鎸囧畾鏃朵娇鐢ㄥ鏉傚害鎺ㄦ柇榛樿绛栫暐銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String strategy = resolveCognitiveStrategy(context, score);
     * }</pre>
     */
    private String resolveCognitiveStrategy(Map<String, Object> context, double complexityScore) {
        Object strategy = context.get("strategy");
        if (strategy == null) {
            strategy = context.get("cognitive_strategy");
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
     * 瑙ｆ瀽鎵ц绛栫暐銆?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠涓庡鏉傚害鍒嗘暟銆?
     * <p>杈撳嚭锛氱瓥鐣ュ悕绉板瓧绗︿覆銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String strategy = resolveExecutionStrategy(context, score);
     * }</pre>
     */
    private String resolveExecutionStrategy(Map<String, Object> context, double complexityScore) {
        Object strategy = context.get("executionStrategy");
        if (strategy instanceof String value && !value.isBlank()) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (complexityScore >= 0.7) {
            return "sequential";
        }
        return "sequential";
    }

    /**
     * 鍒ゆ柇鏄惁闇€瑕佹€濈淮鏍戠瓥鐣ャ€?
     *
     * <p>杈撳叆锛氳鐭ョ瓥鐣ヤ笌澶嶆潅搴﹀垎鏁般€?
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
     * 鍒ゆ柇鏄惁鍚敤鍙嶅簲寮忔墽琛屾ā寮忋€?
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
        Object react = context.get("react");
        if (react == null) {
            react = context.get("reactEnabled");
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
     * 鍒ゆ柇鏄惁鏄惧紡绂佺敤宸ュ叿銆?
     *
     * <p>杈撳叆锛氫笂涓嬫枃鏄犲皠銆?
     * <p>杈撳嚭锛氭槸鍚︾鐢ㄥ伐鍏枫€?
     */
    private boolean isToolsDisabled(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        if (isTruthy(context.get("disableTools"))) {
            return true;
        }
        ModelToolChoice choice = parseToolChoice(context.get("toolChoice"));
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
     * 鍒ゆ柇鏄惁鏄惧紡璇锋眰閾惧紡鎺ㄧ悊銆?
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
     * 鍒ゆ柇瀛楃涓叉槸鍚﹁〃绀洪摼寮忔帹鐞嗐€?
     *
     * <p>杈撳叆锛氬瓧绗︿覆鍊笺€?
     * <p>杈撳嚭锛氭槸鍚﹀尮閰嶉摼寮忔帹鐞嗗叧閿瓧銆?
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
     * <p>鐢ㄩ€旓細鎵胯浇妯″瀷瑙ｆ瀽鍑虹殑鎽樿涓庢楠ゅ垪琛ㄣ€?
     * <p>绀轰緥锛歿@code new PlanParsingResult("summary", steps)}銆?
     */
    private static class PlanParsingResult {
        private final String summary;
        private final List<StepSpec> steps;

        /**
         * 鏋勯€犺В鏋愮粨鏋溿€?
         *
         * <p>杈撳叆锛氭憳瑕佷笌姝ラ鍒楄〃銆?
         * <p>杈撳嚭锛氳В鏋愮粨鏋滃璞°€?
         * <p>绀轰緥锛?
         * <pre>{@code
         * new PlanParsingResult("summary", steps);
         * }</pre>
         *
         * @param summary 鎽樿
         * @param steps 姝ラ鍒楄〃
         */
        private PlanParsingResult(String summary, List<StepSpec> steps) {
            this.summary = summary;
            this.steps = steps;
        }
    }
}

