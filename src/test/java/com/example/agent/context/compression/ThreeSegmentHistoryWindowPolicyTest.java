package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeCommand;
import com.example.agent.capabilities.context.compression.domain.model.HistoryWindowShapeResult;
import com.example.agent.capabilities.context.compression.domain.policy.ThreeSegmentHistoryWindowPolicy;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三段式历史窗口策略测试。
 */
class ThreeSegmentHistoryWindowPolicyTest {

    @Test
    void shouldShapeHistoryWhenMiddleSegmentExists() {
        ThreeSegmentHistoryWindowPolicy policy = new ThreeSegmentHistoryWindowPolicy();
        ContextSnapshot snapshot = buildSnapshotWithRefs(10);
        HistoryWindowShapeCommand command = new HistoryWindowShapeCommand();
        command.setSnapshot(snapshot);
        command.setPrimersCount(2);
        command.setRecentsCount(3);

        HistoryWindowShapeResult result = policy.shape(command);

        assertTrue(result.isWindowShaped());
        assertEquals("WINDOW_SHAPED", result.getShapeReason());
        assertEquals(2, result.getPrimersRetained());
        assertEquals(3, result.getRecentsRetained());
        assertEquals(5, result.getMiddleWindowSize());
        assertNotNull(result.getSnapshot());
        assertNotNull(result.getSnapshot().getLongTermMemory());
        assertNotNull(result.getSnapshot().getLongTermMemory().getMemoryRefs());
        assertEquals(6, result.getSnapshot().getLongTermMemory().getMemoryRefs().size());
        assertEquals("window_middle",
                result.getSnapshot().getLongTermMemory().getMemoryRefs().get(2).getMemoryType());
    }

    @Test
    void shouldSkipShapeWhenWindowTooSmall() {
        ThreeSegmentHistoryWindowPolicy policy = new ThreeSegmentHistoryWindowPolicy();
        ContextSnapshot snapshot = buildSnapshotWithRefs(5);
        HistoryWindowShapeCommand command = new HistoryWindowShapeCommand();
        command.setSnapshot(snapshot);
        command.setPrimersCount(3);
        command.setRecentsCount(2);

        HistoryWindowShapeResult result = policy.shape(command);

        assertFalse(result.isWindowShaped());
        assertEquals("WINDOW_TOO_SMALL", result.getShapeReason());
        assertEquals(3, result.getPrimersRetained());
        assertEquals(2, result.getRecentsRetained());
        assertEquals(0, result.getMiddleWindowSize());
        assertEquals(5, result.getSnapshot().getLongTermMemory().getMemoryRefs().size());
    }

    @Test
    void shouldSkipShapeWhenHistoryEmpty() {
        ThreeSegmentHistoryWindowPolicy policy = new ThreeSegmentHistoryWindowPolicy();
        ContextSnapshot snapshot = new ContextSnapshot();
        LongTermMemory longTermMemory = new LongTermMemory();
        longTermMemory.setMemoryRefs(List.of());
        snapshot.setLongTermMemory(longTermMemory);
        HistoryWindowShapeCommand command = new HistoryWindowShapeCommand();
        command.setSnapshot(snapshot);
        command.setPrimersCount(1);
        command.setRecentsCount(1);

        HistoryWindowShapeResult result = policy.shape(command);

        assertFalse(result.isWindowShaped());
        assertEquals("WINDOW_EMPTY_HISTORY", result.getShapeReason());
        assertNotNull(result.getSnapshot());
    }

    /**
     * 构建带有固定条数记忆引用的快照。
     */
    private ContextSnapshot buildSnapshotWithRefs(int count) {
        ContextSnapshot snapshot = new ContextSnapshot();
        LongTermMemory longTermMemory = new LongTermMemory();
        List<MemoryRef> refs = new ArrayList<>();
        // 循环构造：按顺序创建历史引用，模拟真实会话历史序列。
        for (int index = 0; index < count; index++) {
            MemoryRef ref = new MemoryRef();
            ref.setMemoryId("m-" + index);
            ref.setMemoryType("history");
            ref.setSnippet("历史片段-" + index);
            refs.add(ref);
        }
        longTermMemory.setMemoryRefs(refs);
        snapshot.setLongTermMemory(longTermMemory);
        return snapshot;
    }
}

