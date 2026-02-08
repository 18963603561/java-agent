package com.example.agent.capabilities.memory.store;

import com.example.agent.capabilities.memory.policy.MemoryExpirationService;
import com.example.agent.capabilities.memory.model.ConversationSummary;
import com.example.agent.capabilities.memory.model.MemoryLayer;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.model.WorkingMemorySummary;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.memory.repository.MemoryRepository;
import com.example.agent.capabilities.memory.support.MemoryTextUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 鍘嬬缉璁板繂瀛樺彇灞傦紝璐熻矗鐢熸垚缁撴瀯鍖栨憳瑕佸苟鎸佷箙鍖栥€?
 * <p>
 * 璇存槑锛氭憳瑕佸唴瀹逛細琚鍓互鎺у埗闀垮害銆?
 */
@Service
public class CompressedMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(CompressedMemoryStore.class);
    private static final String SUMMARY_VERSION = "v1";
    private static final int MAX_BULLET_COUNT = 8;
    private static final int MAX_ITEM_COUNT = 6;
    private static final int MAX_ITEM_CHARS = 120;
    private static final int MAX_SUMMARY_CHARS = 800;

    private final MemoryRepository memoryRepository;
    private final MemoryExpirationService expirationService;

    public CompressedMemoryStore(MemoryRepository memoryRepository, MemoryExpirationService expirationService) {
        this.memoryRepository = memoryRepository;
        this.expirationService = expirationService;
    }

    /**
     * 鏍规嵁浼氳瘽璁板綍鐢熸垚鍘嬬缉璁板繂銆?
     *
     * @param sessionId 浼氳瘽鏍囪瘑
     * @param records 浼氳瘽璁板綍
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @return 鍘嬬缉鍚庣殑璁板繂璁板綍
     */
    public MemoryRecord compress(String sessionId, List<MemoryRecord> records, TenantContext tenantContext) {
        return compress(sessionId, records, tenantContext, null);
    }

    /**
     * 鐢熸垚缁撴瀯鍖栨憳瑕佸苟鍐欏叆鍘嬬缉璁板綍銆?
     *
     * @param sessionId 浼氳瘽鏍囪瘑
     * @param records 浼氳瘽璁板綍
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param workflowId 宸ヤ綔娴佹爣璇?
     * @return 鍘嬬缉鍚庣殑璁板繂璁板綍
     */
    public MemoryRecord compress(String sessionId,
                                 List<MemoryRecord> records,
                                 TenantContext tenantContext,
                                 String workflowId) {
        ConversationSummary conversationSummary = buildConversationSummary(records);
        WorkingMemorySummary workingMemorySummary = buildWorkingMemorySummary(records);
        String summary = resolveLegacySummary(conversationSummary, workingMemorySummary, records);
        if (!StringUtils.hasText(summary)) {
            return null;
        }
        MemoryRecord compressed = new MemoryRecord();
        compressed.setMemoryId(UUID.randomUUID().toString());
        compressed.setSessionId(sessionId);
        compressed.setSummary(summary);
        compressed.setConversationSummary(conversationSummary);
        compressed.setWorkingMemorySummary(workingMemorySummary);
        compressed.setLayer(MemoryLayer.COMPRESSED.value());
        compressed.setTenantId(tenantContext.getTenantId());
        Instant now = Instant.now();
        compressed.setCreatedAt(now);
        if (expirationService != null) {
            expirationService.applyExpiration(compressed, now);
        }
        MemoryRecord saved = memoryRepository.save(compressed);
        logStructuredSummary(saved, workflowId);
        log.info("璁板繂鍘嬬缉鐢熸垚, tenantId={}, sessionId={}, memoryId={}",
                tenantContext.getTenantId(), sessionId, compressed.getMemoryId());
        return saved;
    }

    /**
     * 鎼滅储 compressed 璁板綍銆?
     *
     * @param tenantId 绉熸埛鏍囪瘑
     * @param sessionId 浼氳瘽鏍囪瘑
     * @param query 鏌ヨ鏉′欢
     * @param limit 杩斿洖鏁伴噺
     * @return 鍘嬬缉璁板綍
     */
    public List<MemoryRecord> search(String tenantId, String sessionId, String query, int limit) {
        List<MemoryRecord> records = memoryRepository.search(tenantId, sessionId, query, limit);
        return filterCompressed(records);
    }

    /**
     * 鎸変細璇濇煡璇?compressed 璁板綍銆?
     *
     * @param tenantId 绉熸埛鏍囪瘑
     * @param sessionId 浼氳瘽鏍囪瘑
     * @return 鍘嬬缉璁板綍鍒楄〃
     */
    public List<MemoryRecord> listBySession(String tenantId, String sessionId) {
        List<MemoryRecord> records = memoryRepository.findBySession(tenantId, sessionId);
        return filterCompressed(records);
    }

    private String resolveLegacySummary(ConversationSummary conversationSummary,
                                        WorkingMemorySummary workingMemorySummary,
                                        List<MemoryRecord> records) {
        String summary = null;
        if (conversationSummary != null) {
            summary = conversationSummary.toLegacyText();
        }
        if (!StringUtils.hasText(summary) && workingMemorySummary != null) {
            summary = workingMemorySummary.toLegacyText();
        }
        if (!StringUtils.hasText(summary)) {
            summary = buildLegacySummary(records);
        }
        if (!StringUtils.hasText(summary)) {
            return null;
        }
        if (summary.length() > MAX_SUMMARY_CHARS) {
            return MemoryTextUtils.trimText(summary, MAX_SUMMARY_CHARS);
        }
        return summary;
    }

    private String buildLegacySummary(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        StringBuilder summary = new StringBuilder();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            if (MemoryLayer.isCompressed(record.getLayer())) {
                continue;
            }
            if (StringUtils.hasText(record.getContent())) {
                summary.append(record.getContent()).append(' ');
            } else if (StringUtils.hasText(record.getSummary())) {
                summary.append(record.getSummary()).append(' ');
            }
        }
        return summary.toString().trim();
    }

    private ConversationSummary buildConversationSummary(List<MemoryRecord> records) {
        List<String> bullets = collectItems(records, MAX_BULLET_COUNT);
        if (bullets.isEmpty()) {
            return null;
        }
        String summary = MemoryTextUtils.trimText(String.join("；", bullets), MAX_SUMMARY_CHARS);
        ConversationSummary conversationSummary = new ConversationSummary();
        conversationSummary.setVersion(SUMMARY_VERSION);
        conversationSummary.setSummary(summary);
        conversationSummary.setBullets(bullets);
        conversationSummary.setSummaryChars(summary != null ? summary.length() : 0);
        conversationSummary.setBulletCount(bullets.size());
        return conversationSummary;
    }

    private WorkingMemorySummary buildWorkingMemorySummary(List<MemoryRecord> records) {
        List<String> items = collectTailItems(records, MAX_ITEM_COUNT);
        if (items.isEmpty()) {
            return null;
        }
        String summary = MemoryTextUtils.trimText(String.join(" | ", items), MAX_SUMMARY_CHARS);
        WorkingMemorySummary workingSummary = new WorkingMemorySummary();
        workingSummary.setVersion(SUMMARY_VERSION);
        workingSummary.setSummary(summary);
        workingSummary.setItems(items);
        workingSummary.setSummaryChars(summary != null ? summary.length() : 0);
        workingSummary.setItemCount(items.size());
        return workingSummary;
    }

    private List<String> collectItems(List<MemoryRecord> records, int maxCount) {
        List<String> items = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return items;
        }
        for (MemoryRecord record : records) {
            if (record == null || MemoryLayer.isCompressed(record.getLayer())) {
                continue;
            }
            String text = resolveRecordText(record);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            items.add(MemoryTextUtils.trimText(text, MAX_ITEM_CHARS));
            if (items.size() >= maxCount) {
                break;
            }
        }
        return items;
    }

    private List<String> collectTailItems(List<MemoryRecord> records, int maxCount) {
        List<String> items = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return items;
        }
        int startIndex = Math.max(0, records.size() - maxCount);
        for (int i = startIndex; i < records.size(); i++) {
            MemoryRecord record = records.get(i);
            if (record == null || MemoryLayer.isCompressed(record.getLayer())) {
                continue;
            }
            String text = resolveRecordText(record);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            items.add(MemoryTextUtils.trimText(text, MAX_ITEM_CHARS));
        }
        return items;
    }

    private String resolveRecordText(MemoryRecord record) {
        if (record == null) {
            return null;
        }
        if (StringUtils.hasText(record.getSummary())) {
            return record.getSummary();
        }
        return record.getContent();
    }

    private void logStructuredSummary(MemoryRecord record, String workflowId) {
        if (record == null) {
            return;
        }
        ConversationSummary conversationSummary = record.getConversationSummary();
        WorkingMemorySummary workingMemorySummary = record.getWorkingMemorySummary();
        String version = conversationSummary != null ? conversationSummary.getVersion() : SUMMARY_VERSION;
        int summaryChars = conversationSummary != null && conversationSummary.getSummaryChars() != null
                ? conversationSummary.getSummaryChars()
                : record.getSummary() != null ? record.getSummary().length() : 0;
        int workingMemoryItems = workingMemorySummary != null && workingMemorySummary.getItemCount() != null
                ? workingMemorySummary.getItemCount()
                : 0;
        log.info("鍘嬬缉鎽樿浜у嚭, tenantId={}, workflowId={}, version={}, summaryChars={}, workingMemoryItems={}",
                record.getTenantId(), workflowId, version, summaryChars, workingMemoryItems);
    }

    private List<MemoryRecord> filterCompressed(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<MemoryRecord> filtered = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            if (MemoryLayer.isCompressed(record.getLayer())) {
                filtered.add(record);
            }
        }
        return filtered;
    }
}
