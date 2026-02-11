package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DAG 分片路由器。
 * <p>用途：根据 tenant/workflow/node 计算稳定归属实例，支持跨进程消息路由。</p>
 */
@Component
public class DagShardRouter {

    /**
     * 计算节点归属实例。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param nodeId 节点标识
     * @param activeInstances 当前可用实例集合
     * @return 分片归属
     */
    public DagShardAssignment assign(String tenantId,
                                     String workflowId,
                                     String nodeId,
                                     List<String> activeInstances) {
        // 关键逻辑：实例列表为空时直接失败，避免错误回落到默认实例。
        if (activeInstances == null || activeInstances.isEmpty()) {
            throw new IllegalArgumentException("activeInstances 不能为空");
        }
        // 关键逻辑：为保证确定性，先按字典序排序实例列表。
        List<String> sortedInstances = new ArrayList<>(activeInstances);
        sortedInstances.sort(Comparator.naturalOrder());
        String shardKey = buildShardKey(tenantId, workflowId, nodeId);
        // 关键逻辑：使用稳定 hash 映射到实例索引。
        int index = Math.floorMod(shardKey.hashCode(), sortedInstances.size());
        String instanceId = sortedInstances.get(index);
        return new DagShardAssignment(instanceId, shardKey);
    }

    /**
     * 构建分片键。
     */
    public String buildShardKey(String tenantId, String workflowId, String nodeId) {
        String safeTenantId = StringUtils.hasText(tenantId) ? tenantId : "tenant";
        String safeWorkflowId = StringUtils.hasText(workflowId) ? workflowId : "workflow";
        String safeNodeId = StringUtils.hasText(nodeId) ? nodeId : "node";
        return safeTenantId + ":" + safeWorkflowId + ":" + safeNodeId;
    }
}

