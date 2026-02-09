package com.example.agent.budget.trim.application;

import com.example.agent.budget.trim.model.CompressionSummaryApplyResult;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.capabilities.memory.model.ConversationSummary;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.model.WorkingMemorySummary;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认压缩摘要回填器，负责将压缩结果映射到工作记忆与长期记忆引用。
 */
@Component
public class DefaultCompressionSummaryApplier implements CompressionSummaryApplier {

    @Override
    public CompressionSummaryApplyResult apply(ContextSnapshot snapshot, MemoryRecord compressed) {
        CompressionSummaryApplyResult result = new CompressionSummaryApplyResult();
        if (snapshot == null || compressed == null) {
            return result;
        }

        WorkingMemory memory = snapshot.getWorkingMemory();
        if (memory == null) {
            memory = new WorkingMemory();
            snapshot.setWorkingMemory(memory);
        }

        ConversationSummary conversationSummary = compressed.getConversationSummary();
        WorkingMemorySummary workingSummary = compressed.getWorkingMemorySummary();
        String summaryText = firstNonBlank(
                conversationSummary != null ? conversationSummary.getSummary() : null,
                workingSummary != null ? workingSummary.getSummary() : null,
                conversationSummary != null ? conversationSummary.toLegacyText() : null,
                workingSummary != null ? workingSummary.toLegacyText() : null,
                compressed.getSummary()
        );
        if (StringUtils.hasText(summaryText)) {
            memory.setSummary(summaryText);
            memory.setSummaryChars(summaryText.length());
        }

        List<String> keyFacts = null;
        if (workingSummary != null && workingSummary.getItems() != null && !workingSummary.getItems().isEmpty()) {
            keyFacts = new ArrayList<>(workingSummary.getItems());
        } else if (conversationSummary != null
                && conversationSummary.getBullets() != null
                && !conversationSummary.getBullets().isEmpty()) {
            keyFacts = new ArrayList<>(conversationSummary.getBullets());
        }
        if (keyFacts != null) {
            memory.setKeyFacts(keyFacts);
            memory.setWorkingMemoryItems(keyFacts.size());
        }
        if (conversationSummary != null || workingSummary != null) {
            memory.setUsedStructuredSummary(true);
        }

        String summaryVersion = firstNonBlank(
                conversationSummary != null ? conversationSummary.getVersion() : null,
                workingSummary != null ? workingSummary.getVersion() : null
        );
        if (StringUtils.hasText(summaryVersion)) {
            memory.setSummaryVersion(summaryVersion);
            result.setSummaryVersion(summaryVersion);
        }

        Integer summaryChars = firstNonNull(
                conversationSummary != null ? conversationSummary.getSummaryChars() : null,
                workingSummary != null ? workingSummary.getSummaryChars() : null
        );
        if (summaryChars != null) {
            memory.setSummaryChars(summaryChars);
        }

        Integer itemsCount = firstNonNull(
                workingSummary != null ? workingSummary.getItemCount() : null,
                conversationSummary != null ? conversationSummary.getBulletCount() : null
        );
        if (itemsCount != null) {
            memory.setWorkingMemoryItems(itemsCount);
        }

        LongTermMemory longTermMemory = snapshot.getLongTermMemory();
        if (longTermMemory == null) {
            longTermMemory = new LongTermMemory();
            snapshot.setLongTermMemory(longTermMemory);
        }
        MemoryRef ref = new MemoryRef();
        ref.setMemoryId(compressed.getMemoryId());
        ref.setMemoryType("compressed");
        ref.setSnippet(StringUtils.hasText(summaryText) ? summaryText : compressed.getSummary());
        ref.setExpiresAt(compressed.getExpiresAt());
        ref.setSource("budget_compress");
        longTermMemory.setMemoryRefs(List.of(ref));
        return result;
    }

    /**
     * 返回首个非空白字符串。
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 返回首个非空整数。
     */
    private Integer firstNonNull(Integer first, Integer second) {
        if (first != null) {
            return first;
        }
        return second;
    }
}
