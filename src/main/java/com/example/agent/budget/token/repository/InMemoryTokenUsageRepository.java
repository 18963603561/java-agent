package com.example.agent.budget.token.repository;

import com.example.agent.budget.token.model.TokenUsageRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存预算记录仓储，用于无持久化依赖时的兜底实现。
 *
 * <p>设计说明：
 * <ul>
 *     <li>写路径采用并发容器，保证多线程下幂等写入与索引更新安全。</li>
 *     <li>读路径返回不可变快照，并复制记录对象，避免内部可变状态泄漏。</li>
 * </ul>
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenUsageRepository implements TokenUsageRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTokenUsageRepository.class);

    private final ConcurrentHashMap<String, TokenUsageRecord> usageIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<TokenUsageRecord>> taskIndex = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(TokenUsageRecord record) {
        if (record == null || record.getUsageId() == null) {
            log.warn("预算记录写入忽略, 原因=record为空或usageId为空");
            return false;
        }
        TokenUsageRecord storedRecord = copyRecord(record);
        String usageKey = buildUsageKey(storedRecord.getTenantId(), storedRecord.getUsageId());
        TokenUsageRecord existing = usageIndex.putIfAbsent(usageKey, storedRecord);
        if (existing != null) {
            log.debug("预算记录幂等命中, tenantId={}, usageId={}",
                    storedRecord.getTenantId(), storedRecord.getUsageId());
            return false;
        }
        String taskKey = buildTaskKey(storedRecord.getTenantId(), storedRecord.getTaskId());
        taskIndex.computeIfAbsent(taskKey, key -> new ConcurrentLinkedQueue<>()).add(storedRecord);
        log.debug("预算记录写入成功, tenantId={}, taskId={}, usageId={}",
                storedRecord.getTenantId(), storedRecord.getTaskId(), storedRecord.getUsageId());
        return true;
    }

    @Override
    public List<TokenUsageRecord> findByTask(String tenantId, String taskId) {
        String taskKey = buildTaskKey(tenantId, taskId);
        Queue<TokenUsageRecord> records = taskIndex.get(taskKey);
        if (records == null || records.isEmpty()) {
            log.debug("预算记录查询为空, tenantId={}, taskId={}", tenantId, taskId);
            return List.of();
        }
        List<TokenUsageRecord> snapshot = new ArrayList<>(records.size());
        for (TokenUsageRecord record : records) {
            snapshot.add(copyRecord(record));
        }
        log.debug("预算记录查询完成, tenantId={}, taskId={}, size={}", tenantId, taskId, snapshot.size());
        return List.copyOf(snapshot);
    }

    /**
     * 复制预算记录，确保仓储内部状态不被外部对象修改影响。
     */
    private TokenUsageRecord copyRecord(TokenUsageRecord source) {
        TokenUsageRecord target = new TokenUsageRecord();
        target.setRecordId(source.getRecordId());
        target.setUsageId(source.getUsageId());
        target.setTaskId(source.getTaskId());
        target.setAgentId(source.getAgentId());
        target.setModel(source.getModel());
        target.setProvider(source.getProvider());
        target.setInputTokens(source.getInputTokens());
        target.setOutputTokens(source.getOutputTokens());
        target.setTotalTokens(source.getTotalTokens());
        target.setCostUsd(source.getCostUsd());
        target.setCreatedAt(source.getCreatedAt());
        target.setTenantId(source.getTenantId());
        return target;
    }

    /**
     * 构造 usage 索引键。
     */
    private String buildUsageKey(String tenantId, String usageId) {
        return tenantId + ":" + usageId;
    }

    /**
     * 构造任务索引键。
     */
    private String buildTaskKey(String tenantId, String taskId) {
        return tenantId + ":" + taskId;
    }
}
