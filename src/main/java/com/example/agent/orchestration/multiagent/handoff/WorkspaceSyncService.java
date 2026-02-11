package com.example.agent.orchestration.multiagent.handoff;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 工作区同步服务。
 *
 * <p>用途：维护 workflow 维度的 topic 工作区，支持生产写入与消费读取。</p>
 */
@Service
public class WorkspaceSyncService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSyncService.class);

    private final Map<String, Map<String, List<Map<String, Object>>>> workspace = new ConcurrentHashMap<>();

    /**
     * 写入 topic 数据。
     *
     * @param workflowId 工作流标识
     * @param topic topic 名称
     * @param entry 条目
     */
    public void append(String workflowId, String topic, Map<String, Object> entry) {
        if (!StringUtils.hasText(workflowId) || !StringUtils.hasText(topic) || entry == null) {
            return;
        }
        Map<String, List<Map<String, Object>>> byTopic = workspace.computeIfAbsent(workflowId,
                ignore -> new ConcurrentHashMap<>());
        List<Map<String, Object>> entries = byTopic.computeIfAbsent(topic, ignore -> new CopyOnWriteArrayList<>());
        Map<String, Object> copied = new HashMap<>(entry);
        copied.putIfAbsent("timestamp", Instant.now().toString());
        entries.add(copied);
        log.info("工作区写入, workflowId={}, topic={}, entrySize={}", workflowId, topic, entries.size());
    }

    /**
     * 判断 topic 是否已有数据。
     *
     * @param workflowId 工作流标识
     * @param topic topic 名称
     * @return 是否存在
     */
    public boolean hasTopic(String workflowId, String topic) {
        if (!StringUtils.hasText(workflowId) || !StringUtils.hasText(topic)) {
            return false;
        }
        Map<String, List<Map<String, Object>>> byTopic = workspace.get(workflowId);
        if (byTopic == null) {
            return false;
        }
        List<Map<String, Object>> entries = byTopic.get(topic);
        return entries != null && !entries.isEmpty();
    }

    /**
     * 读取 topic 下的所有记录。
     *
     * @param workflowId 工作流标识
     * @param topic topic 名称
     * @return 只读快照
     */
    public List<Map<String, Object>> list(String workflowId, String topic) {
        if (!StringUtils.hasText(workflowId) || !StringUtils.hasText(topic)) {
            return List.of();
        }
        Map<String, List<Map<String, Object>>> byTopic = workspace.get(workflowId);
        if (byTopic == null) {
            return List.of();
        }
        List<Map<String, Object>> entries = byTopic.get(topic);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> item : entries) {
            copied.add(new HashMap<>(item));
        }
        return copied;
    }

    /**
     * 读取 workflow 下所有 topic 快照。
     *
     * @param workflowId 工作流标识
     * @return topic 到条目列表的快照
     */
    public Map<String, List<Map<String, Object>>> snapshot(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return Map.of();
        }
        Map<String, List<Map<String, Object>>> byTopic = workspace.get(workflowId);
        if (byTopic == null || byTopic.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Map<String, Object>>> copied = new HashMap<>();
        byTopic.forEach((topic, entries) -> {
            List<Map<String, Object>> entryCopies = new ArrayList<>();
            if (entries != null) {
                // 关键逻辑：逐条复制，防止调用方修改内部存储造成并发问题。
                for (Map<String, Object> item : entries) {
                    entryCopies.add(new HashMap<>(item));
                }
            }
            copied.put(topic, List.copyOf(entryCopies));
        });
        return Map.copyOf(copied);
    }

    /**
     * 读取 topic 最近一条记录。
     *
     * @param workflowId 工作流标识
     * @param topic topic 名称
     * @return 最近条目，不存在时返回空
     */
    public Map<String, Object> latest(String workflowId, String topic) {
        if (!StringUtils.hasText(workflowId) || !StringUtils.hasText(topic)) {
            return Map.of();
        }
        Map<String, List<Map<String, Object>>> byTopic = workspace.get(workflowId);
        if (byTopic == null) {
            return Map.of();
        }
        List<Map<String, Object>> entries = byTopic.get(topic);
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(entries.get(entries.size() - 1));
    }
}
