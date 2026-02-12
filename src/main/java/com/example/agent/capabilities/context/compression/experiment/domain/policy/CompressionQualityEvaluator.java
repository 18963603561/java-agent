package com.example.agent.capabilities.context.compression.experiment.domain.policy;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionQualityScore;

/**
 * 压缩质量评估端口。
 */
public interface CompressionQualityEvaluator {

    /**
     * 评估主轨与影子轨质量。
     *
     * @param primaryResult 主轨结果
     * @param shadowResult 影子轨结果
     * @return 质量评分
     */
    CompressionQualityScore evaluate(CompressionExecutionResult primaryResult,
                                     CompressionExecutionResult shadowResult);
}

