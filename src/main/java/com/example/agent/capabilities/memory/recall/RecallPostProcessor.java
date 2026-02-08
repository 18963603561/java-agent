package com.example.agent.capabilities.memory.recall;

import com.example.agent.capabilities.memory.MemoryLayer;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.support.MemoryTextUtils;
import com.example.agent.security.redaction.RedactionResult;
import com.example.agent.security.redaction.RedactionService;
import com.example.agent.security.redaction.RedactionStage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 召回后处理器，负责记录裁剪、脱敏和摘要生成。
 */
@Component
public class RecallPostProcessor {

    private final RedactionService redactionService;

    public RecallPostProcessor(RedactionService redactionService) {
        this.redactionService = redactionService;
    }

    /**
     * 执行召回后处理。
     *
     * @param records 原始记录
     * @param includeCompressed 是否包含压缩层
     * @param maxRecordChars 单条记录最大字符数
     * @param maxSummaryChars 摘要最大字符数
     * @param enableSensitiveMask 是否启用敏感信息遮罩
     * @return 后处理结果
     */
    public RecallPostProcessResult process(List<MemoryRecord> records,
                                           boolean includeCompressed,
                                           int maxRecordChars,
                                           int maxSummaryChars,
                                           boolean enableSensitiveMask) {
        List<MemoryRecord> filtered = filterCompressed(records, includeCompressed);
        List<MemoryRecord> trimmed = trimRecords(filtered, maxRecordChars);
        int redactionsAppliedCount = applyRedactionToRecords(trimmed, enableSensitiveMask);
        String summary = buildSummary(trimmed, maxSummaryChars);
        RedactionResult summaryRedaction = applyRedactionToSummary(summary, enableSensitiveMask);
        String redactedSummary = summaryRedaction.getRedactedText();
        redactionsAppliedCount += summaryRedaction.getRedactedCount();
        return new RecallPostProcessResult(trimmed, redactedSummary, redactionsAppliedCount);
    }

    /**
     * 过滤压缩层记录。
     */
    private List<MemoryRecord> filterCompressed(List<MemoryRecord> records, boolean includeCompressed) {
        if (includeCompressed || records == null || records.isEmpty()) {
            return records == null ? List.of() : records;
        }
        List<MemoryRecord> filtered = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            if (!MemoryLayer.isCompressed(record.getLayer())) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    /**
     * 裁剪记录字段长度并复制副本，避免修改原始对象。
     */
    private List<MemoryRecord> trimRecords(List<MemoryRecord> records, int maxChars) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        if (maxChars <= 0) {
            return new ArrayList<>(records);
        }
        List<MemoryRecord> trimmed = new ArrayList<>(records.size());
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            MemoryRecord copy = new MemoryRecord();
            copy.setMemoryId(record.getMemoryId());
            copy.setSessionId(record.getSessionId());
            copy.setTaskId(record.getTaskId());
            copy.setTenantId(record.getTenantId());
            copy.setLayer(record.getLayer());
            copy.setCreatedAt(record.getCreatedAt());
            copy.setExpiresAt(record.getExpiresAt());
            copy.setConversationSummary(record.getConversationSummary());
            copy.setWorkingMemorySummary(record.getWorkingMemorySummary());
            copy.setContent(MemoryTextUtils.trimText(record.getContent(), maxChars));
            copy.setSummary(MemoryTextUtils.trimText(record.getSummary(), maxChars));
            trimmed.add(copy);
        }
        return trimmed;
    }

    /**
     * 对记录执行脱敏处理。
     */
    private int applyRedactionToRecords(List<MemoryRecord> records, boolean enableSensitiveMask) {
        if (!enableSensitiveMask || redactionService == null || records == null || records.isEmpty()) {
            return 0;
        }
        int redactedCount = 0;
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            RedactionResult contentResult = redactionService.apply(
                    record.getContent(), RedactionStage.RECALL, "memoryContent");
            record.setContent(contentResult.getRedactedText());
            redactedCount += contentResult.getRedactedCount();

            RedactionResult summaryResult = redactionService.apply(
                    record.getSummary(), RedactionStage.RECALL, "memorySummary");
            record.setSummary(summaryResult.getRedactedText());
            redactedCount += summaryResult.getRedactedCount();
        }
        return redactedCount;
    }

    /**
     * 对聚合摘要执行脱敏处理。
     */
    private RedactionResult applyRedactionToSummary(String summary, boolean enableSensitiveMask) {
        if (!enableSensitiveMask || redactionService == null) {
            RedactionResult result = new RedactionResult();
            result.setRedactedText(summary);
            return result;
        }
        return redactionService.apply(summary, RedactionStage.RECALL, "memorySummaryAggregate");
    }

    /**
     * 构建召回摘要。
     */
    private String buildSummary(List<MemoryRecord> records, int maxSummaryChars) {
        if (records == null || records.isEmpty() || maxSummaryChars <= 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (MemoryRecord record : records) {
            String text = MemoryTextUtils.firstNonBlank(record.getSummary(), record.getContent());
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(index).append(". ").append(text.trim());
            if (builder.length() >= maxSummaryChars) {
                builder.setLength(Math.min(builder.length(), maxSummaryChars));
                break;
            }
            index++;
        }
        String summary = builder.toString().trim();
        return summary.isEmpty() ? null : summary;
    }
}
