package com.example.agent.budget;

/**
 * 上下文裁剪器接口。
 */
public interface ContextTrimmer {

    /**
     * 按预算策略裁剪上下文。
     *
     * @param request 裁剪请求
     * @return 裁剪结果
     */
    ContextTrimResult trim(ContextTrimRequest request);
}
