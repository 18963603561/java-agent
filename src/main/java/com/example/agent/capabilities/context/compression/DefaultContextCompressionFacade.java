package com.example.agent.capabilities.context.compression;

import com.example.agent.budget.trim.application.ContextCompressionService;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.budget.trim.model.ContextCompressionResult;
import org.springframework.stereotype.Component;

/**
 * 默认上下文压缩门面实现。
 *
 * <p>用途：将上下文层的压缩调用统一委派到压缩编排服务，隔离调用方与底层实现细节。</p>
 */
@Component
public class DefaultContextCompressionFacade implements ContextCompressionFacade {

    /**
     * 压缩编排服务。
     */
    private final ContextCompressionService compressionService;

    public DefaultContextCompressionFacade(ContextCompressionService compressionService) {
        this.compressionService = compressionService;
    }

    @Override
    public ContextCompressionResult compressIfNeeded(ContextCompressionRequest request) {
        // 兜底处理：当压缩服务不可用时返回空结果，避免构建链路发生空指针中断。
        if (compressionService == null) {
            return new ContextCompressionResult();
        }
        // 统一委派：由压缩编排服务执行触发判定、执行与回填。
        return compressionService.compressIfNeeded(request);
    }
}
