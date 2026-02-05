package com.example.agent.capabilities.tools.hook;

/**
 * Hook 处理器接口。
 */
public interface HookHandler {

    /**
     * Hook 标识，用于匹配配置。
     *
     * @return Hook 标识
     */
    String getHookId();

    /**
     * 执行 Hook。
     *
     * @param context Hook 上下文
     * @return Hook 决策结果
     */
    HookDecision handle(HookContext context);
}
