package com.example.agent.capabilities.memory.write;

import com.example.agent.capabilities.memory.model.MemoryLayer;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.config.MemoryWriteProperties;
import com.example.agent.capabilities.memory.support.MemoryTextUtils;
import org.springframework.stereotype.Component;

/**
 * 记忆写入记录工厂，负责构造统一的 recent 层记录对象。
 */
@Component
public class MemoryWriteRecordFactory {

    private final MemoryWriteProperties properties;

    public MemoryWriteRecordFactory(MemoryWriteProperties properties) {
        this.properties = properties;
    }

    /**
     * 构建最近层记忆记录。
     *
     * @param sessionId 会话标识
     * @param taskId 任务标识
     * @param content 记忆内容
     * @param summary 记忆摘要
     * @return 记忆记录
     */
    public MemoryRecord buildRecentRecord(String sessionId, String taskId, String content, String summary) {
        MemoryRecord record = new MemoryRecord();
        record.setSessionId(sessionId);
        record.setTaskId(taskId);
        record.setContent(MemoryTextUtils.trimText(content, properties.getMaxRecordChars()));
        record.setSummary(MemoryTextUtils.trimText(summary, properties.getMaxSummaryChars()));
        record.setLayer(MemoryLayer.RECENT.value());
        return record;
    }
}


