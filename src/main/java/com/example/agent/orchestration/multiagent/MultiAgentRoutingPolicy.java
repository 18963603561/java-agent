package com.example.agent.orchestration.multiagent;

import com.example.agent.runtime.model.StepSpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 多智能体路由策略。
 *
 * <p>用途：根据步骤依赖与显式模式，选择 DAG 或 Supervisor 执行路径。</p>
 */
@Component
public class MultiAgentRoutingPolicy {

    /**
     * 解析执行模式。
     *
     * @param step 步骤定义
     * @return 执行模式
     */
    public MultiAgentExecutionMode resolveMode(StepSpec step) {
        // 关键逻辑：新方案默认优先走 DAG Actor，仅在显式指定时进入 Supervisor。
        if (step == null) {
            return MultiAgentExecutionMode.DAG;
        }
        String explicitMode = resolveStringArg(step, "mode");
        if (StringUtils.hasText(explicitMode)) {
            if ("supervisor".equalsIgnoreCase(explicitMode) || "p2p".equalsIgnoreCase(explicitMode)) {
                return MultiAgentExecutionMode.SUPERVISOR;
            }
            if ("dag".equalsIgnoreCase(explicitMode)) {
                return MultiAgentExecutionMode.DAG;
            }
        }
        return MultiAgentExecutionMode.DAG;
    }

    private String resolveStringArg(StepSpec step, String key) {
        if (step == null || step.getArguments() == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = step.getArguments().get(key);
        return value == null ? null : String.valueOf(value);
    }

}
