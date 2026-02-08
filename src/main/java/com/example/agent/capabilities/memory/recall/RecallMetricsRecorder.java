package com.example.agent.capabilities.memory.recall;

import com.example.agent.capabilities.memory.model.RetrievalPriority;
import com.example.agent.capabilities.memory.support.RetrievalPriorityUtils;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 召回指标记录器，负责统一封装召回相关指标打点逻辑。
 */
@Component
public class RecallMetricsRecorder {

    private final MetricsPublisher metricsPublisher;

    public RecallMetricsRecorder(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 记录检索优先级使用情况。
     *
     * @param retrievalPriority 实际生效的检索优先级顺序
     */
    public void recordRetrievalPriority(List<RetrievalPriority> retrievalPriority) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.incrementWithTags("context_retrieval_priority_used_total",
                "priorityName", RetrievalPriorityUtils.formatPriorityTag(retrievalPriority));
    }
}

