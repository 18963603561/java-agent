package com.example.agent.runtime.engine;

/**
 * 步骤状态机，定义合法的状态迁移规则。
 */
public class StepStateMachine {

    /**
     * 校验并返回状态迁移结果。
     *
     * @param current 当前状态
     * @param target 目标状态
     * @return 目标状态
     */
    public StepState transition(StepState current, StepState target) {
        if (current == null) {
            return target;
        }
        if (current == target) {
            return target;
        }
        return switch (current) {
            case PENDING -> switch (target) {
                case STARTED, SKIPPED, WAITING -> target;
                default -> throw new IllegalStateException("不允许从 PENDING 迁移到 " + target);
            };
            case STARTED -> switch (target) {
                case COMPLETED, FAILED, WAITING -> target;
                default -> throw new IllegalStateException("不允许从 STARTED 迁移到 " + target);
            };
            case WAITING -> switch (target) {
                case STARTED, FAILED, SKIPPED -> target;
                default -> throw new IllegalStateException("不允许从 WAITING 迁移到 " + target);
            };
            case COMPLETED, FAILED, SKIPPED -> throw new IllegalStateException("终态不可迁移");
        };
    }
}
