package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeCommand;
import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeResult;

/**
 * 历史窗口策略。
 *
 * <p>用途：定义上下文历史整形入口，为三段式滑窗策略预留扩展点。</p>
 */
public interface HistoryWindowPolicy {

    /**
     * 执行历史窗口整形。
     *
     * @param command 历史窗口整形命令
     * @return 历史窗口整形结果
     */
    HistoryWindowShapeResult shape(HistoryWindowShapeCommand command);
}
