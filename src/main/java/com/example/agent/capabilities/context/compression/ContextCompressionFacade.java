package com.example.agent.capabilities.context.compression;

import com.example.agent.capabilities.context.compression.contract.ContextCompressionRequest;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;

/**
 * 上下文压缩门面。
 *
 * <p>用途：为上下文构建流程提供稳定压缩入口，隔离底层执行策略与实现细节。</p>
 */
public interface ContextCompressionFacade {

    /**
     * 在需要时执行上下文压缩。
     *
     * @param request 压缩请求
     * @return 压缩结果
     */
    ContextCompressionResult compressIfNeeded(ContextCompressionRequest request);
}


