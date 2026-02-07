package com.example.agent.runtime.step.repository;

import com.example.agent.runtime.step.StepRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 内存步骤记录仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryStepRecordRepository implements StepRecordRepository {

    /**
     * 内存分区存储。
     *
     * <p>一级 key：tenantId:workflowId。</p>
     * <p>二级 key：stepId（缺失时回退为 seq 维度标识）。</p>
     *
     * <p>设计意图：保持与数据库仓储一致的“同 stepId 覆盖更新”语义。</p>
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, StepRecord>> stepStore = new ConcurrentHashMap<>();

    /**
     * 保存步骤记录到内存存储。
     *
     * @param record 步骤记录
     */
    @Override
    public void save(StepRecord record) {
        if (record == null) {
            return;
        }
        String indexKey = buildIndexKey(record.getTenantId(), record.getWorkflowId());
        String recordKey = buildRecordKey(record);
        stepStore.computeIfAbsent(indexKey, key -> new ConcurrentHashMap<>())
                .put(recordKey, record);
    }

    /**
     * 按租户与工作流查询步骤记录。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 步骤记录列表
     */
    @Override
    public List<StepRecord> findByWorkflow(String tenantId, String workflowId) {
        String indexKey = buildIndexKey(tenantId, workflowId);
        Map<String, StepRecord> records = stepStore.get(indexKey);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        ArrayList<StepRecord> result = new ArrayList<>(records.values());
        result.sort(Comparator.comparingLong(StepRecord::getStepSeq));
        return result;
    }

    private String buildIndexKey(String tenantId, String workflowId) {
        return tenantId + ":" + workflowId;
    }

    /**
     * 构建内存记录主键。
     *
     * <p>优先使用 stepId 保证与数据库主键语义一致；当 stepId 缺失时，回退到 stepSeq，
     * 以避免空键导致的覆盖异常。</p>
     *
     * @param record 步骤记录
     * @return 内存记录主键
     */
    private String buildRecordKey(StepRecord record) {
        if (record == null) {
            return "unknown";
        }
        if (StringUtils.hasText(record.getStepId())) {
            return record.getStepId();
        }
        return "seq:" + record.getStepSeq();
    }
}
