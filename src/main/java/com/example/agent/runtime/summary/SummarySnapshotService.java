package com.example.agent.runtime.summary;

import com.example.agent.runtime.summary.SummaryComputationModels.BoundedSnapshot;
import com.example.agent.runtime.summary.SummaryComputationModels.KeySnapshot;
import com.example.agent.runtime.summary.SummaryComputationModels.OutputSnapshot;
import com.example.agent.runtime.summary.SummaryComputationModels.SnapshotWriter;
import com.example.agent.runtime.summary.SummaryComputationModels.SummaryLimits;
import com.example.agent.runtime.summary.SummaryComputationModels.TruncationState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 摘要快照服务。
 *
 * <p>用途：负责输出键快照与文本快照构建，统一处理复杂结构遍历与截断。
 * <p>输入：任意输出对象与摘要限制。
 * <p>输出：输出快照结构。
 * <p>边界：循环结构会写入占位并标记截断。
 */
@Service
public class SummarySnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SummarySnapshotService.class);

    /**
     * 构建输出快照。
     *
     * @param output 输出
     * @param limits 限制
     * @param truncation 截断状态
     * @return 快照
     */
    public OutputSnapshot buildSnapshot(Object output,
                                        SummaryLimits limits,
                                        TruncationState truncation) {
        KeySnapshot keySnapshot = resolveKeys(output, limits, truncation);
        BoundedSnapshot snapshot = buildBoundedSnapshot(output, limits, truncation);
        String sample = snapshot.getText() != null
                ? SummaryDigestService.trimText(snapshot.getText(), limits, truncation)
                : null;
        return new OutputSnapshot(
                keySnapshot.getKeyCount(),
                keySnapshot.getKeys(),
                snapshot.getCharCount(),
                sample
        );
    }

    /**
     * 解析键快照。
     *
     * @param output 输出
     * @param limits 限制
     * @param truncation 截断状态
     * @return 键快照
     */
    public KeySnapshot resolveKeys(Object output,
                                   SummaryLimits limits,
                                   TruncationState truncation) {
        if (!(output instanceof Map<?, ?> map) || map.isEmpty()) {
            return new KeySnapshot(0, Collections.emptyList());
        }
        List<String> keys = new ArrayList<>();
        int index = 0;
        int maxItems = limits.getMaxListItems();
        for (Object key : map.keySet()) {
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            String keyText = key == null ? "null" : safeToString(key);
            keys.add(SummaryDigestService.trimText(keyText, limits, truncation));
            index++;
        }
        return new KeySnapshot(map.size(), keys);
    }

    /**
     * 构建有界文本快照。
     *
     * @param output 输出
     * @param limits 限制
     * @param truncation 截断状态
     * @return 快照
     */
    public BoundedSnapshot buildBoundedSnapshot(Object output,
                                                SummaryLimits limits,
                                                TruncationState truncation) {
        if (output == null) {
            return new BoundedSnapshot("", 0);
        }
        SnapshotWriter writer = new SnapshotWriter(limits.getMaxChars(), truncation);
        java.util.IdentityHashMap<Object, Boolean> visited = new java.util.IdentityHashMap<>();
        appendValue(output, writer, limits, truncation, visited);
        return new BoundedSnapshot(writer.toString(), writer.length());
    }

    /**
     * 追加值。
     *
     * @param value 值
     * @param writer 写入器
     * @param limits 限制
     * @param truncation 截断状态
     * @param visited 访问记录
     */
    public void appendValue(Object value,
                            SnapshotWriter writer,
                            SummaryLimits limits,
                            TruncationState truncation,
                            java.util.IdentityHashMap<Object, Boolean> visited) {
        if (writer.isStopped()) {
            return;
        }
        if (value == null) {
            writer.append("null");
            return;
        }
        if (value instanceof String text) {
            writer.append(SummaryDigestService.trimText(text, limits, truncation));
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            writer.append(SummaryDigestService.trimText(safeToString(value), limits, truncation));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendMap(map, writer, limits, truncation, visited);
            return;
        }
        if (value instanceof List<?> list) {
            appendList(list, writer, limits, truncation, visited);
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(value, writer, limits, truncation, visited);
            return;
        }
        writer.append(SummaryDigestService.trimText(safeToString(value), limits, truncation));
    }

    /**
     * 追加映射。
     *
     * @param map 映射
     * @param writer 写入器
     * @param limits 限制
     * @param truncation 截断状态
     * @param visited 访问记录
     */
    public void appendMap(Map<?, ?> map,
                          SnapshotWriter writer,
                          SummaryLimits limits,
                          TruncationState truncation,
                          java.util.IdentityHashMap<Object, Boolean> visited) {
        if (writer.isStopped()) {
            return;
        }
        if (visited.put(map, Boolean.TRUE) != null) {
            writer.append("<circular>");
            truncation.markTruncated();
            return;
        }
        writer.append("{");
        int index = 0;
        int maxItems = limits.getMaxListItems();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            if (index > 0) {
                writer.append(",");
            }
            String keyText = entry.getKey() == null ? "null" : safeToString(entry.getKey());
            writer.append(SummaryDigestService.trimText(keyText, limits, truncation));
            writer.append(":");
            appendValue(entry.getValue(), writer, limits, truncation, visited);
            index++;
        }
        writer.append("}");
        visited.remove(map);
    }

    /**
     * 追加列表。
     *
     * @param list 列表
     * @param writer 写入器
     * @param limits 限制
     * @param truncation 截断状态
     * @param visited 访问记录
     */
    public void appendList(List<?> list,
                           SnapshotWriter writer,
                           SummaryLimits limits,
                           TruncationState truncation,
                           java.util.IdentityHashMap<Object, Boolean> visited) {
        if (writer.isStopped()) {
            return;
        }
        if (visited.put(list, Boolean.TRUE) != null) {
            writer.append("<circular>");
            truncation.markTruncated();
            return;
        }
        writer.append("[");
        int index = 0;
        int maxItems = limits.getMaxListItems();
        for (Object item : list) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            if (index > 0) {
                writer.append(",");
            }
            appendValue(item, writer, limits, truncation, visited);
            index++;
        }
        writer.append("]");
        visited.remove(list);
    }

    /**
     * 追加数组。
     *
     * @param array 数组
     * @param writer 写入器
     * @param limits 限制
     * @param truncation 截断状态
     * @param visited 访问记录
     */
    public void appendArray(Object array,
                            SnapshotWriter writer,
                            SummaryLimits limits,
                            TruncationState truncation,
                            java.util.IdentityHashMap<Object, Boolean> visited) {
        if (writer.isStopped()) {
            return;
        }
        if (visited.put(array, Boolean.TRUE) != null) {
            writer.append("<cycle>");
            truncation.markTruncated();
            return;
        }
        writer.append("[");
        int length = java.lang.reflect.Array.getLength(array);
        int maxItems = limits.getMaxListItems();
        for (int i = 0; i < length; i++) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && i >= maxItems) {
                truncation.markTruncated();
                break;
            }
            if (i > 0) {
                writer.append(",");
            }
            Object item = java.lang.reflect.Array.get(array, i);
            appendValue(item, writer, limits, truncation, visited);
        }
        writer.append("]");
        visited.remove(array);
    }

    private String safeToString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return String.valueOf(value);
        } catch (Exception ex) {
            String className = value.getClass().getSimpleName();
            if (!StringUtils.hasText(className)) {
                className = value.getClass().getName();
            }
            if (log.isDebugEnabled()) {
                log.debug("toString 失败, className={}", value.getClass().getName(), ex);
            }
            return "<toString_error:" + className + ">";
        }
    }
}
