package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeCommand;
import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeResult;
import org.springframework.stereotype.Component;

/**
 * 默认历史窗口策略。
 *
 * <p>用途：在三段式滑窗能力接入前提供无副作用默认实现，保持主链路稳定。</p>
 */
@Component
public class DefaultHistoryWindowPolicy implements HistoryWindowPolicy {

    @Override
    public HistoryWindowShapeResult shape(HistoryWindowShapeCommand command) {
        HistoryWindowShapeResult result = new HistoryWindowShapeResult();
        // 保持行为稳定：默认策略不改写窗口，仅透传快照与基础元信息。
        if (command == null) {
            result.setWindowShaped(false);
            result.setShapeReason("WINDOW_COMMAND_MISSING");
            return result;
        }
        result.setSnapshot(command.getSnapshot());
        result.setWindowShaped(false);
        result.setShapeReason("WINDOW_POLICY_DEFAULT_PASSTHROUGH");
        result.setPrimersRetained(0);
        result.setRecentsRetained(0);
        result.setMiddleWindowSize(0);
        return result;
    }
}
