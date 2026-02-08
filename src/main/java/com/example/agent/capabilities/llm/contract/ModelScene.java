package com.example.agent.capabilities.llm.contract;

/**
 * 模型场景枚举，用于模型路由选择与调用分流。
 *
 * <p>用途：统一标识不同业务阶段的模型调用场景。</p>
 * <p>注意：新增场景后需确保模型路由配置覆盖对应键值。</p>
 */
public enum ModelScene {
    // 规划场景：生成任务步骤与计划
    PLANNER,
    // 反思场景：评估步骤输出与是否重试
    REFLECT,
    // LLM 步骤场景：单步 LLM 决策与总结
    LLM_STEP,
    // 研究场景：检索/引用/摘要型任务
    RESEARCH,
    // 低成本场景：预算敏感型调用
    CHEAP
}
