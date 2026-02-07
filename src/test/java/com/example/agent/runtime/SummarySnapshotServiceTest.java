package com.example.agent.runtime;

import com.example.agent.runtime.summary.SummaryComputationModels.OutputSnapshot;
import com.example.agent.runtime.summary.SummaryComputationModels.SummaryLimits;
import com.example.agent.runtime.summary.SummaryComputationModels.TruncationState;
import com.example.agent.runtime.summary.SummarySnapshotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummarySnapshotServiceTest {

    @Test
    void buildSnapshotTruncatesListAndMarksTruncated() {
        SummarySnapshotService service = new SummarySnapshotService();
        SummaryLimits limits = new SummaryLimits(50, 2, 10);
        TruncationState truncation = new TruncationState();

        Map<String, Object> output = Map.of(
                "items", List.of("a", "b", "c", "d"),
                "name", "snapshot-test"
        );

        OutputSnapshot snapshot = service.buildSnapshot(output, limits, truncation);

        assertTrue(snapshot.getKeyCount() >= 1);
        assertTrue(snapshot.getCharCount() <= limits.getMaxChars());
        assertTrue(truncation.isTruncated());
    }

    @Test
    void buildSnapshotHandlesArrayCycle() {
        SummarySnapshotService service = new SummarySnapshotService();
        SummaryLimits limits = new SummaryLimits(200, 1, 30);
        TruncationState truncation = new TruncationState();

        Object[] array = new Object[2];
        array[0] = array;
        array[1] = "tail";

        OutputSnapshot snapshot = service.buildSnapshot(array, limits, truncation);

        assertTrue(snapshot.getSample().contains("<cycle>") || snapshot.getSample().contains("<circular>"));
        assertTrue(truncation.isTruncated());
        assertEquals(0, snapshot.getKeyCount());
    }
}
