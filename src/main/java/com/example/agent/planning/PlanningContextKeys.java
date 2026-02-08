package com.example.agent.planning;

/**
 * 规划上下文键常量。
 *
 * <p>用途：集中维护规划阶段使用的上下文键，避免散落的魔法字符串。
 */
public final class PlanningContextKeys {

    public static final String TOOL = "tool";
    public static final String TOOL_NAME = "toolName";
    public static final String TOOLS = "tools";
    public static final String TOOL_CHOICE = "toolChoice";
    public static final String FALLBACK_TOOL = "fallbackTool";
    public static final String DISABLE_TOOLS = "disableTools";

    public static final String MODE = "mode";
    public static final String STRATEGY = "strategy";
    public static final String EXECUTION_STRATEGY = "executionStrategy";
    public static final String COGNITIVE_STRATEGY = "cognitiveStrategy";
    /**
     * 历史兼容键，建议逐步迁移到 {@link #COGNITIVE_STRATEGY}。
     */
    @Deprecated(since = "2026-02-08", forRemoval = false)
    public static final String COGNITIVE_STRATEGY_LEGACY = "cognitive_strategy";
    public static final String REACT = "react";
    public static final String REACT_ENABLED = "reactEnabled";
    public static final String STRATEGY_MULTI_AGENT = "multi_agent";
    public static final String STRATEGY_MULTI_AGENT_ALIAS = "multi-agent";
    public static final String STRATEGY_DEBATE = "debate";
    public static final String STRATEGY_RESEARCH = "research";
    public static final String MODE_DEEP_RESEARCH = "deep_research";
    public static final String MODE_REACT = "react";
    public static final String MODE_COT = "cot";
    public static final String STRATEGY_CHAIN_OF_THOUGHT = "chain_of_thought";
    public static final String STRATEGY_CHAIN_OF_THOUGHT_ALIAS = "chain-of-thought";
    public static final String STRATEGY_TREE_OF_THOUGHTS = "tree_of_thoughts";
    public static final String STRATEGY_TOT = "tot";
    public static final String STRATEGY_REFLECTION = "reflection";
    public static final String STRATEGY_SIMPLE = "simple";
    public static final String EXECUTION_SEQUENTIAL = "sequential";

    public static final String REQUIRES_APPROVAL = "requiresApproval";
    public static final String APPROVAL_SOURCE = "approvalSource";

    public static final String PLAN_SUMMARY = "planSummary";
    public static final String PLAN_STEPS = "planSteps";
    public static final String PLAN_DEPENDENCIES = "planDependencies";

    public static final String CAPABILITY_SCORE = "capabilityScore";
    public static final String CAPABILITY_RISK = "capabilityRisk";

    public static final String BUDGET_THRESHOLD_TOKENS = "budgetThresholdTokens";
    public static final String FAILURE_TYPES = "failureTypes";

    public static final String SNAPSHOT_ID = "snapshotId";
    public static final String CONTEXT_SNAPSHOT = "contextSnapshot";
    public static final String CONTEXT_BUDGET = "contextBudget";
    public static final String CONTEXT_PRUNE = "contextPrune";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String TENANT_ID = "tenantId";

    public static final String MEMORY = "memory";
    public static final String COUNT = "count";
    public static final String EVIDENCE_PACK = "evidencePack";

    private PlanningContextKeys() {
    }
}
