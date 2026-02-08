package com.example.agent.capabilities.memory.recall;

import com.example.agent.capabilities.memory.MemoryQuery;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.MemorySearchResult;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.RetrievalPriority;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 召回执行规划器，负责构建检索请求并执行主检索流程。
 */
@Component
public class RecallExecutionPlanner {

    private final MemoryStore memoryStore;

    public RecallExecutionPlanner(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    /**
     * 执行记忆检索。
     *
     * @param sessionId 会话标识
     * @param query 查询语句
     * @param limit 召回上限
     * @param tenantContext 租户上下文
     * @param retrievalPriority 检索优先级
     * @return 检索结果列表
     */
    public List<MemoryRecord> executeSearch(String sessionId,
                                            String query,
                                            int limit,
                                            TenantContext tenantContext,
                                            List<RetrievalPriority> retrievalPriority) {
        MemoryQuery memoryQuery = new MemoryQuery();
        memoryQuery.setSessionId(sessionId);
        memoryQuery.setQuery(query);
        memoryQuery.setLimit(limit);
        MemorySearchResult result = memoryStore.search(memoryQuery, tenantContext, retrievalPriority);
        return result != null ? result.getRecords() : List.of();
    }
}
