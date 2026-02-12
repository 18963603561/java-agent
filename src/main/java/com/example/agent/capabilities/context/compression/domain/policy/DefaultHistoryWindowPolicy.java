package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.capabilities.context.model.ContextSnapshot;
import org.springframework.stereotype.Component;

/**
 * 默认历史窗口策略。
 *
 * <p>用途：在三段式滑窗能力接入前提供无副作用默认实现，保持主链路稳定。</p>
 */
@Component
public class DefaultHistoryWindowPolicy implements HistoryWindowPolicy {

    @Override
    public ContextSnapshot shape(ContextSnapshot snapshot) {
        // 保持行为稳定：P0 阶段不改写历史窗口，直接返回原快照。
        return snapshot;
    }
}

