package com.example.agent.orchestration.multiagent.dag.actor;

/**
 * DAG Actor 消息类型。
 *
 * <p>用途：统一节点间消息语义，避免使用字符串常量导致分支漂移。</p>
 */
public enum DagMessageType {
    /**
     * 依赖满足消息。
     */
    DEPENDENCY_SATISFIED,
    /**
     * 节点启动消息。
     */
    NODE_START,
    /**
     * 节点完成消息。
     */
    NODE_COMPLETE,
    /**
     * 节点失败消息。
     */
    NODE_FAIL
}

