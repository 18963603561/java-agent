package com.example.agent.history.eventlog;

import java.util.List;

/**
 * 事件日志仓储接口，用于持久化与查询事件记录。
 */
public interface EventLogRepository {

    /**
     * 幂等保存事件日志。
     *
     * @param record 事件记录
     * @return 是否写入成功
     */
    boolean saveIfAbsent(EventLogRecord record);

    /**
     * 查询指定工作流的事件日志。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 事件记录列表
     */
    List<EventLogRecord> findByWorkflow(String tenantId, String workflowId);
}
