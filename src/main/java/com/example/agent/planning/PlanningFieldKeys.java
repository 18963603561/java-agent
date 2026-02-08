package com.example.agent.planning;

/**
 * 规划通用字段常量。
 *
 * <p>用途：集中维护规划输入输出结构中的字段名，避免在解析与构建流程中散落魔法字符串。
 */
public final class PlanningFieldKeys {

    public static final String SUMMARY = "summary";
    public static final String STEPS = "steps";
    public static final String TYPE = "type";
    public static final String INPUT = "input";
    public static final String QUERY = "query";
    public static final String QUESTION = "question";
    public static final String PROMPT = "prompt";
    public static final String CONTEXT = "context";
    public static final String CONTEXT_SUMMARY = "contextSummary";
    public static final String STEP_KEY = "stepKey";
    public static final String DEPENDS_ON = "dependsOn";
    public static final String CRITICAL = "critical";
    public static final String ARGUMENTS = "arguments";
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String PLAN_ID = "planId";
    public static final String PROMPT_SCENE = "promptScene";
    public static final String TOKEN_BUDGET = "tokenBudget";
    public static final String MEMORY_ITEMS = "memoryItems";
    public static final String EVIDENCE_COUNT = "evidenceCount";
    public static final String PROMPT_ASSEMBLY_INPUT = "promptAssemblyInput";
    public static final String TOTAL = "total";
    public static final String SCENE_PLANNER = "planner";

    private PlanningFieldKeys() {
    }
}
