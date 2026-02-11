package com.example.agent.orchestration.multiagent.usecase;

import com.example.agent.orchestration.multiagent.MultiAgentExecutionMode;
import com.example.agent.orchestration.multiagent.MultiAgentRoutingPolicy;
import com.example.agent.orchestration.multiagent.support.StepArgumentReader;
import com.example.agent.runtime.model.StepSpec;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 执行路由决策器。
 *
 * <p>用途：集中处理模式判定与输入主题解析，避免入口协调器承担细节逻辑。</p>
 */
@Component
public class ExecutionRouteDecider {

    private final MultiAgentRoutingPolicy routingPolicy;
    private final StepArgumentReader stepArgumentReader;

    /**
     * 构造路由决策器。
     */
    public ExecutionRouteDecider(MultiAgentRoutingPolicy routingPolicy,
                                 StepArgumentReader stepArgumentReader) {
        this.routingPolicy = routingPolicy;
        this.stepArgumentReader = stepArgumentReader;
    }

    /**
     * 解析执行模式。
     */
    public MultiAgentExecutionMode resolveMode(StepSpec step) {
        return routingPolicy.resolveMode(step);
    }

    /**
     * 解析主题列表参数。
     */
    public List<String> resolveTopicList(StepSpec step, String key) {
        return stepArgumentReader.readStringList(step, key);
    }
}
