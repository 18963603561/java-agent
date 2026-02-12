package com.example.agent.capabilities.context.compression.application.port;

import com.example.agent.capabilities.context.compression.contract.ContextCompressionRequest;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;

/**
 * 上下文压缩编排端口。
 *
 * <p>用途：向压缩门面暴露稳定编排入口，避免门面层依赖预算实现细节。</p>
 */
public interface ContextCompressionOrchestrationPort {

    /**
     * 在满足触发条件时执行压缩。
     *
     * @param request 压缩请求
     * @return 压缩结果
     */
    ContextCompressionResult compressIfNeeded(ContextCompressionRequest request);
}

