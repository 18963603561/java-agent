package com.example.agent.capabilities.memory.write;

import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.security.auth.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 记忆写入持久化网关，负责安全保存并处理异常日志。
 */
@Component
public class MemoryWritePersistenceGateway {

    private static final Logger log = LoggerFactory.getLogger(MemoryWritePersistenceGateway.class);

    private final MemoryStore memoryStore;

    public MemoryWritePersistenceGateway(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    /**
     * 安全保存记忆记录。
     *
     * @param record 记忆记录
     * @param tenantContext 租户上下文
     * @return 是否保存成功
     */
    public boolean saveSafely(MemoryRecord record, TenantContext tenantContext) {
        try {
            memoryStore.save(record, tenantContext);
            return true;
        } catch (Exception ex) {
            log.error("记忆写入失败, tenantId={}, sessionId={}, taskId={}",
                    tenantContext.getTenantId(), record.getSessionId(), record.getTaskId(), ex);
            return false;
        }
    }
}


