package com.example.agent.runtime;

import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepState;
import com.example.agent.runtime.step.repository.InMemoryStepRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存步骤仓储测试。
 *
 * <p>用途：验证内存仓储与数据库仓储在“同 stepId 覆盖更新”语义上保持一致。</p>
 */
class InMemoryStepRecordRepositoryTest {

    @Test
    void saveShouldOverwriteWhenStepIdSame() {
        InMemoryStepRecordRepository repository = new InMemoryStepRecordRepository();

        StepRecord started = buildRecord("step-1", "wf-1", "tenant-1", 1L, StepState.STARTED, 1,
                Instant.parse("2026-02-07T05:00:00Z"), null);
        StepRecord completed = buildRecord("step-1", "wf-1", "tenant-1", 1L, StepState.COMPLETED, 1,
                Instant.parse("2026-02-07T05:00:00Z"), Instant.parse("2026-02-07T05:00:02Z"));

        repository.save(started);
        repository.save(completed);

        List<StepRecord> records = repository.findByWorkflow("tenant-1", "wf-1");
        assertEquals(1, records.size());
        assertEquals(StepState.COMPLETED, records.get(0).getStatus());
        assertNotNull(records.get(0).getCompletedAt());
    }

    @Test
    void findByWorkflowShouldReturnSortedByStepSeq() {
        InMemoryStepRecordRepository repository = new InMemoryStepRecordRepository();

        repository.save(buildRecord("step-3", "wf-2", "tenant-2", 30L, StepState.STARTED, 1,
                Instant.parse("2026-02-07T06:00:03Z"), null));
        repository.save(buildRecord("step-1", "wf-2", "tenant-2", 10L, StepState.STARTED, 1,
                Instant.parse("2026-02-07T06:00:01Z"), null));
        repository.save(buildRecord("step-2", "wf-2", "tenant-2", 20L, StepState.STARTED, 1,
                Instant.parse("2026-02-07T06:00:02Z"), null));

        List<StepRecord> records = repository.findByWorkflow("tenant-2", "wf-2");

        assertEquals(3, records.size());
        assertEquals(10L, records.get(0).getStepSeq());
        assertEquals(20L, records.get(1).getStepSeq());
        assertEquals(30L, records.get(2).getStepSeq());
    }

    @Test
    void concurrentSaveWithSameStepIdShouldKeepSingleRecord() throws Exception {
        InMemoryStepRecordRepository repository = new InMemoryStepRecordRepository();
        int concurrency = 12;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        AtomicInteger seqCounter = new AtomicInteger(0);

        for (int index = 0; index < concurrency; index++) {
            executor.submit(() -> {
                try {
                    startLatch.await(2, TimeUnit.SECONDS);
                    int seq = seqCounter.incrementAndGet();
                    StepRecord record = buildRecord("step-concurrent", "wf-3", "tenant-3", 100L,
                            StepState.COMPLETED, seq,
                            Instant.parse("2026-02-07T07:00:00Z"),
                            Instant.parse("2026-02-07T07:00:01Z"));
                    repository.save(record);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        List<StepRecord> records = repository.findByWorkflow("tenant-3", "wf-3");
        assertEquals(1, records.size());
        assertEquals("step-concurrent", records.get(0).getStepId());
        assertEquals(100L, records.get(0).getStepSeq());
    }

    private StepRecord buildRecord(String stepId,
                                   String workflowId,
                                   String tenantId,
                                   long stepSeq,
                                   StepState status,
                                   int attempt,
                                   Instant startedAt,
                                   Instant completedAt) {
        StepRecord record = new StepRecord();
        record.setStepId(stepId);
        record.setWorkflowId(workflowId);
        record.setTenantId(tenantId);
        record.setStepSeq(stepSeq);
        record.setType("TOOL");
        record.setStatus(status);
        record.setAttempt(attempt);
        record.setStartedAt(startedAt);
        record.setCompletedAt(completedAt);
        return record;
    }
}

