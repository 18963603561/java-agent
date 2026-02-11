package com.example.agent.orchestration.multiagent.dag.actor.distributed;

/**
 * DAG 分片归属结果。
 *
 * <p>用途：表达节点被路由到的实例及分片键。</p>
 */
public class DagShardAssignment {

    private final String instanceId;
    private final String shardKey;

    public DagShardAssignment(String instanceId, String shardKey) {
        this.instanceId = instanceId;
        this.shardKey = shardKey;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getShardKey() {
        return shardKey;
    }
}

