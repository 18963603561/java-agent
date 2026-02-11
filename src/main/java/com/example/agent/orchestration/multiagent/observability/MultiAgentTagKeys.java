package com.example.agent.orchestration.multiagent.observability;

/**
 * 多智能体标签键字典。
 * <p>用途：统一指标标签键，减少同义键并存导致的统计口径漂移。</p>
 */
public final class MultiAgentTagKeys {

    /**
     * 节点标识。
     */
    public static final String NODE_ID = "nodeId";

    /**
     * 控制命令。
     */
    public static final String COMMAND = "command";

    /**
     * 原因。
     */
    public static final String REASON = "reason";

    private MultiAgentTagKeys() {
    }
}

