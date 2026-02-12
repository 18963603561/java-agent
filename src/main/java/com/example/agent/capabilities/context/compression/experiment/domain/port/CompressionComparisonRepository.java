package com.example.agent.capabilities.context.compression.experiment.domain.port;

import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionComparisonRecord;

/**
 * 压缩对比记录仓储端口。
 */
public interface CompressionComparisonRepository {

    /**
     * 保存对比记录。
     *
     * @param record 对比记录
     */
    void save(CompressionComparisonRecord record);
}
