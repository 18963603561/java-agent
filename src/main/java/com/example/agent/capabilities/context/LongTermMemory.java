package com.example.agent.capabilities.context;

import java.util.List;

/**
 * 长期记忆引用。
 */
public class LongTermMemory {

    /**
     * 记忆引用列表。
     */
    private List<MemoryRef> memoryRefs;

    public List<MemoryRef> getMemoryRefs() {
        return memoryRefs;
    }

    public void setMemoryRefs(List<MemoryRef> memoryRefs) {
        this.memoryRefs = memoryRefs;
    }
}