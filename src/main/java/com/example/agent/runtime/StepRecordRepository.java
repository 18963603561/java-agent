package com.example.agent.runtime;

import java.util.List;

/**
 * 步骤记录仓储接口，用于持久化步骤执行信息。
 */
public interface StepRecordRepository {

    void save(StepRecord record);

    List<StepRecord> findByWorkflow(String tenantId, String workflowId);
}
