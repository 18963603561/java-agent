package com.example.agent.capabilities.context.compression.application.port;

/**
 * 压缩模式解析端口。
 *
 * <p>用途：统一解析当前有效压缩模式，避免调用方散落模式判断。</p>
 */
public interface CompressionModeResolver {

    /**
     * 解析当前有效压缩模式。
     *
     * @return 压缩模式
     */
    String resolveMode();
}

