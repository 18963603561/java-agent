package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.capabilities.context.model.ContextSnapshot;

/**
 * 历史窗口策略。
 *
 * <p>用途：定义上下文历史整形入口，为三段式滑窗策略预留扩展点。</p>
 */
public interface HistoryWindowPolicy {

    /**
     * 对上下文快照执行历史窗口整形。
     *
     * @param snapshot 上下文快照
     * @return 整形后的上下文快照
     */
    ContextSnapshot shape(ContextSnapshot snapshot);
}

