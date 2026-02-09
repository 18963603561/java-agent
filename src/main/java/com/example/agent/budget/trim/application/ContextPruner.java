package com.example.agent.budget.trim.application;

import com.example.agent.budget.trim.model.ContextPruneRequest;
import com.example.agent.budget.trim.model.ContextPruneResult;

/**
 * 上下文裁剪器接口。
 */
public interface ContextPruner {

    /**
     * 执行裁剪逻辑。
     *
     * @param request 裁剪请求
     * @return 裁剪结果
     */
    ContextPruneResult prune(ContextPruneRequest request);
}
