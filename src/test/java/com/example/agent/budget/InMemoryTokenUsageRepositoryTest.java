package com.example.agent.budget;

import com.example.agent.budget.token.repository.InMemoryTokenUsageRepository;
import com.example.agent.budget.token.model.TokenUsageRecord;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InMemoryTokenUsageRepository 并发与快照语义测试。
 */
class InMemoryTokenUsageRepositoryTest {

    @Test
    void saveIfAbsentShouldBeIdempotentUnderConcurrency() throws Exception {
        InMemoryTokenUsageRepository repository = new InMemoryTokenUsageRepository();
        int threads = 24;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successCount = new AtomicInteger();
        try {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int index = 0; index < threads; index++) {
                tasks.add(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    TokenUsageRecord record = buildRecord("tenant-1", "task-1", "usage-fixed", 100, 20, 120);
                    if (repository.saveIfAbsent(record)) {
                        successCount.incrementAndGet();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<TokenUsageRecord> records = repository.findByTask("tenant-1", "task-1");
        assertEquals(1, successCount.get());
        assertEquals(1, records.size());
        assertEquals("usage-fixed", records.get(0).getUsageId());
    }

    @Test
    void saveIfAbsentShouldSupportConcurrentWritesWithDifferentUsageIds() throws Exception {
        InMemoryTokenUsageRepository repository = new InMemoryTokenUsageRepository();
        int threads = 60;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = new java.util.ArrayList<>();
            for (int index = 0; index < threads; index++) {
                int current = index;
                tasks.add(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    TokenUsageRecord record = buildRecord(
                            "tenant-2",
                            "task-2",
                            "usage-" + current,
                            current + 1,
                            current + 2,
                            current + 3);
                    return repository.saveIfAbsent(record);
                });
            }
            List<Future<Boolean>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        List<TokenUsageRecord> records = repository.findByTask("tenant-2", "task-2");
        assertEquals(threads, records.size());
        Set<String> usageIds = new HashSet<>();
        for (TokenUsageRecord record : records) {
            usageIds.add(record.getUsageId());
        }
        assertEquals(threads, usageIds.size());
    }

    @Test
    void findByTaskShouldReturnImmutableSnapshotAndDefensiveCopy() {
        InMemoryTokenUsageRepository repository = new InMemoryTokenUsageRepository();
        TokenUsageRecord original = buildRecord("tenant-3", "task-3", "usage-1", 10, 20, 30);

        assertTrue(repository.saveIfAbsent(original));

        original.setTotalTokens(9999);
        original.setProvider("changed-provider");

        List<TokenUsageRecord> firstView = repository.findByTask("tenant-3", "task-3");
        assertEquals(1, firstView.size());
        assertEquals(30, firstView.get(0).getTotalTokens());
        assertThrows(UnsupportedOperationException.class, () -> firstView.add(new TokenUsageRecord()));

        firstView.get(0).setTotalTokens(7777);

        List<TokenUsageRecord> secondView = repository.findByTask("tenant-3", "task-3");
        assertEquals(30, secondView.get(0).getTotalTokens());
    }

    /**
     * 构造预算记录，避免测试中重复样板代码。
     */
    private TokenUsageRecord buildRecord(String tenantId,
                                         String taskId,
                                         String usageId,
                                         int inputTokens,
                                         int outputTokens,
                                         int totalTokens) {
        TokenUsageRecord record = new TokenUsageRecord();
        record.setRecordId("record-" + usageId);
        record.setTenantId(tenantId);
        record.setTaskId(taskId);
        record.setUsageId(usageId);
        record.setAgentId("agent-test");
        record.setModel("model-test");
        record.setProvider("provider-test");
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setTotalTokens(totalTokens);
        record.setCostUsd(0.123d);
        record.setCreatedAt(Instant.now());
        return record;
    }
}



