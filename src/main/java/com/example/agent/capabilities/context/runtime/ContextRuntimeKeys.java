package com.example.agent.capabilities.context.runtime;

/**
 * 上下文运行时键定义。
 *
 * <p>用途：统一维护上下文读写键，避免魔法字符串散落。
 */
public final class ContextRuntimeKeys {

    private ContextRuntimeKeys() {
    }

    public static final String LOCALE = "locale";
    public static final String OUTPUT_FORMAT = "outputFormat";
    public static final String ALLOWED_TOOLS = "allowedTools";

    public static final String SYSTEM_POLICY_ID = "systemPolicyId";
    public static final String DEVELOPER_POLICY_ID = "developerPolicyId";
    public static final String FORBIDDEN_ACTIONS = "forbiddenActions";
    public static final String DATA_SCOPES = "dataScopes";
    public static final String RISK_LEVEL = "riskLevel";
    public static final String REQUIRES_APPROVAL = "requiresApproval";

    public static final String SUCCESS_CRITERIA = "successCriteria";
    public static final String FAILURE_POLICY = "failurePolicy";
    public static final String REQUIRED_OUTPUT = "requiredOutput";
    public static final String CONSTRAINTS = "constraints";

    public static final String PLAN_STEPS = "planSteps";
    public static final String NEXT_STEP = "nextStep";

    public static final String EVIDENCE_PACK = "evidencePack";
    public static final String CITATIONS = "citations";
    public static final String RESEARCH_CITATIONS = "researchCitations";
    public static final String LONG_TERM_MEMORY_REFS = "longTermMemoryRefs";

    public static final String SELECTED_TOOLS = "selectedTools";
    public static final String LAST_TOOL_ERROR = "lastToolError";

    public static final String TOKEN_BUDGET = "tokenBudget";

    public static final String TOOL_CHOICE = "toolChoice";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String MEMORY = "memory";

    public static final String CONTEXT_SNAPSHOT = "contextSnapshot";
    public static final String SNAPSHOT_ID = "snapshotId";
    public static final String CONTEXT_BUDGET = "contextBudget";
    public static final String CONTEXT_PRUNE = "contextPrune";
    public static final String CONTEXT_COMPRESSION = "contextCompression";
    public static final String PROMPT_ASSEMBLY_INPUT = "promptAssemblyInput";
}
