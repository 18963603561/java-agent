package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeCommand;
import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeResult;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 三段式历史窗口策略。
 *
 * <p>用途：按 Shannon 风格执行 Primers + Middle + Recents 切分，并将中段聚合到压缩窗口。</p>
 */
@Primary
@Component
public class ThreeSegmentHistoryWindowPolicy implements HistoryWindowPolicy {

    @Override
    public HistoryWindowShapeResult shape(HistoryWindowShapeCommand command) {
        HistoryWindowShapeResult result = new HistoryWindowShapeResult();
        // 参数守卫：命令为空时直接返回未整形结果，避免下游空指针。
        if (command == null || command.getSnapshot() == null) {
            result.setWindowShaped(false);
            result.setShapeReason("WINDOW_COMMAND_MISSING");
            return result;
        }

        ContextSnapshot snapshot = command.getSnapshot();
        result.setSnapshot(snapshot);
        LongTermMemory longTermMemory = snapshot.getLongTermMemory();
        List<MemoryRef> originalRefs = longTermMemory != null ? longTermMemory.getMemoryRefs() : null;
        // 空历史守卫：无历史引用时无需整形。
        if (originalRefs == null || originalRefs.isEmpty()) {
            result.setWindowShaped(false);
            result.setShapeReason("WINDOW_EMPTY_HISTORY");
            return result;
        }

        int total = originalRefs.size();
        int primersCount = Math.max(0, command.getPrimersCount());
        int recentsCount = Math.max(0, command.getRecentsCount());
        // 边界判定：首尾保留覆盖总长度时不进行整形，保持原始历史。
        if (primersCount + recentsCount >= total) {
            result.setWindowShaped(false);
            result.setShapeReason("WINDOW_TOO_SMALL");
            result.setPrimersRetained(Math.min(primersCount, total));
            result.setRecentsRetained(Math.max(0, total - result.getPrimersRetained()));
            result.setMiddleWindowSize(0);
            return result;
        }

        int primerSize = Math.min(primersCount, total);
        int recentsSize = Math.min(recentsCount, total - primerSize);
        int middleStart = primerSize;
        int middleEnd = total - recentsSize;
        // 中段计算：中段长度小于等于零时无需整形。
        if (middleEnd <= middleStart) {
            result.setWindowShaped(false);
            result.setShapeReason("WINDOW_MIDDLE_EMPTY");
            result.setPrimersRetained(primerSize);
            result.setRecentsRetained(recentsSize);
            result.setMiddleWindowSize(0);
            return result;
        }

        List<MemoryRef> primers = new ArrayList<>(originalRefs.subList(0, primerSize));
        List<MemoryRef> middle = new ArrayList<>(originalRefs.subList(middleStart, middleEnd));
        List<MemoryRef> recents = new ArrayList<>(originalRefs.subList(middleEnd, total));
        MemoryRef bridgeRef = buildMiddleBridgeRef(middle, middleStart, middleEnd - 1);
        List<MemoryRef> shapedRefs = new ArrayList<>(primerSize + recentsSize + 1);
        shapedRefs.addAll(primers);
        // 中段聚合：将中段历史压缩为桥接引用，减少后续提示词体积。
        shapedRefs.add(bridgeRef);
        shapedRefs.addAll(recents);

        // 快照回写：将整形后的历史引用写回长期记忆区域。
        if (longTermMemory == null) {
            longTermMemory = new LongTermMemory();
            snapshot.setLongTermMemory(longTermMemory);
        }
        longTermMemory.setMemoryRefs(shapedRefs);

        result.setSnapshot(snapshot);
        result.setWindowShaped(true);
        result.setShapeReason("WINDOW_SHAPED");
        result.setPrimersRetained(primerSize);
        result.setRecentsRetained(recentsSize);
        result.setMiddleWindowSize(middle.size());
        return result;
    }

    /**
     * 构建中段桥接引用。
     */
    private MemoryRef buildMiddleBridgeRef(List<MemoryRef> middle, int startIndex, int endIndex) {
        MemoryRef bridge = new MemoryRef();
        bridge.setMemoryId("window-middle-" + startIndex + "-" + endIndex);
        bridge.setMemoryType("window_middle");
        bridge.setSource("three_segment_window");
        bridge.setSnippet(buildMiddleSnippet(middle));
        return bridge;
    }

    /**
     * 构建中段摘要片段。
     */
    private String buildMiddleSnippet(List<MemoryRef> middle) {
        if (middle == null || middle.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("[MIDDLE_WINDOW]");
        // 循环拼接：按时间顺序拼接中段片段，保留窗口语义与来源。
        for (int index = 0; index < middle.size(); index++) {
            MemoryRef ref = middle.get(index);
            // 空值过滤：忽略空引用，避免桥接片段出现噪声标记。
            if (ref == null || ref.getSnippet() == null || ref.getSnippet().isBlank()) {
                continue;
            }
            builder.append('\n').append(index + 1).append('.').append(ref.getSnippet().trim());
            // 长度保护：片段过长时提前截断，避免桥接引用过大。
            if (builder.length() > 1800) {
                builder.setLength(1800);
                break;
            }
        }
        return builder.toString();
    }
}

