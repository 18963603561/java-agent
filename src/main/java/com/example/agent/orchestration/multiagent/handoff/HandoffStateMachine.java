package com.example.agent.orchestration.multiagent.handoff;

import com.example.agent.common.error.ErrorCodeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 交接状态机。
 *
 * <p>用途：集中维护交接状态迁移规则，保证生命周期推进一致性。</p>
 */
@Component
public class HandoffStateMachine {

    /**
     * 校验并返回迁移后的状态。
     *
     * @param current 当前状态
     * @param target 目标状态
     * @return 目标状态
     */
    public HandoffStatus transition(HandoffStatus current, HandoffStatus target) {
        HandoffStatus source = current == null ? HandoffStatus.PENDING : current;
        if (source == target) {
            return target;
        }
        // 关键逻辑：按当前状态分发可达目标，非法迁移直接拒绝
        return switch (source) {
            case PENDING -> switch (target) {
                case WAITING, RUNNING, FAILED, CANCELLED -> target;
                default -> throw invalid(source, target);
            };
            case WAITING -> switch (target) {
                case RUNNING, FAILED, CANCELLED -> target;
                default -> throw invalid(source, target);
            };
            case RUNNING -> switch (target) {
                case SUCCEEDED, FAILED, CANCELLED, WAITING -> target;
                default -> throw invalid(source, target);
            };
            case SUCCEEDED, FAILED, CANCELLED -> throw invalid(source, target);
        };
    }

    private ErrorCodeException invalid(HandoffStatus current, HandoffStatus target) {
        return new ErrorCodeException(
                HttpStatus.CONFLICT,
                "HANDOFF_INVALID_TRANSITION",
                "非法交接状态迁移: " + current + " -> " + target);
    }
}

