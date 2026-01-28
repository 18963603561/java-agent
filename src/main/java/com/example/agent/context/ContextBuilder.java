package com.example.agent.context;

/**
 * 上下文构建器接口。
 */
public interface ContextBuilder {

    /**
     * 构建上下文快照与配套信息。
     *
     * @param request 上下文构建请求
     * @return 构建结果
     */
    ContextBuildResult build(ContextBuildRequest request);
}