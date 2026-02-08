package com.example.agent.capabilities.tools.execution.service;

import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.store.RawResultStore;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工具原始结果引用服务。
 *
 * <p>用途：封装 rawRef 存储与输出解析规则。</p>
 */
@Component
public class ToolRawRefService {

    /**
     * 存储原始结果并返回引用对象。
     *
     * @param rawResultStore 原始结果存储组件
     * @param toolName 工具名称
     * @param result 结果数据
     * @return 原始引用
     */
    public RawRef store(RawResultStore rawResultStore, String toolName, Map<String, Object> result) {
        if (rawResultStore == null || result == null || result.isEmpty()) {
            return null;
        }
        return rawResultStore.store(toolName, result, "application/json");
    }

    /**
     * 解析输出 rawRef。
     *
     * @param rawRef 原始引用对象
     * @return 输出引用
     */
    public String resolveOutputRawRef(RawRef rawRef) {
        if (rawRef == null) {
            return null;
        }
        if (rawRef.getRefId() != null && !rawRef.getRefId().isBlank()) {
            return rawRef.getRefId();
        }
        if (rawRef.getKey() != null && !rawRef.getKey().isBlank()) {
            return rawRef.getKey();
        }
        return null;
    }
}

