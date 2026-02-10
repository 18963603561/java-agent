package com.example.agent.orchestrator;

import com.example.agent.orchestration.task.InMemoryTaskRepository;
import com.example.agent.orchestration.task.TaskListCursorCodec;
import com.example.agent.orchestration.task.TaskPageQuery;
import com.example.agent.orchestration.task.TaskPageResult;
import com.example.agent.orchestration.task.TaskRecord;
import com.example.agent.orchestration.task.TaskStatus;
import com.example.agent.orchestration.task.TaskStatusMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTaskRepositoryPagingTest {

    @Test
    void pageQueryFollowsSeekCursorAndStableSort() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository(new TaskStatusMapper());
        TaskRecord first = buildRecord("tenant-a", "task-1", Instant.parse("2026-02-10T10:00:00Z"));
        TaskRecord second = buildRecord("tenant-a", "task-2", Instant.parse("2026-02-10T10:00:00Z"));
        TaskRecord third = buildRecord("tenant-a", "task-3", Instant.parse("2026-02-10T09:00:00Z"));
        repository.save(first);
        repository.save(second);
        repository.save(third);

        TaskPageResult firstPage = repository.listPage(new TaskPageQuery("tenant-a", null, null, 2));
        assertEquals(2, firstPage.getTasks().size());
        assertTrue(firstPage.isHasMore());
        assertNotNull(firstPage.getNextCursor());

        List<String> firstIds = firstPage.getTasks().stream().map(TaskRecord::getTaskId).toList();
        assertEquals(List.of("task-2", "task-1"), firstIds);

        TaskPageResult secondPage = repository.listPage(new TaskPageQuery("tenant-a", null,
                firstPage.getNextCursor(), 2));
        assertEquals(1, secondPage.getTasks().size());
        assertFalse(secondPage.isHasMore());
        assertEquals("task-3", secondPage.getTasks().get(0).getTaskId());
    }

    @Test
    void pageQueryFiltersByStatus() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository(new TaskStatusMapper());
        TaskRecord completed = buildRecord("tenant-a", "task-1", Instant.parse("2026-02-10T10:00:00Z"));
        completed.setStatus(TaskStatus.COMPLETED);
        TaskRecord failed = buildRecord("tenant-a", "task-2", Instant.parse("2026-02-10T09:00:00Z"));
        failed.setStatus(TaskStatus.FAILED);
        repository.save(completed);
        repository.save(failed);

        TaskPageResult result = repository.listPage(new TaskPageQuery("tenant-a", TaskStatus.COMPLETED.value(),
                null, 10));
        assertEquals(1, result.getTasks().size());
        assertEquals(TaskStatus.COMPLETED, result.getTasks().get(0).getStatus());
    }

    @Test
    void pageQueryRejectsInvalidCursor() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository(new TaskStatusMapper());
        TaskRecord record = buildRecord("tenant-a", "task-1", Instant.parse("2026-02-10T10:00:00Z"));
        repository.save(record);

        org.springframework.web.server.ResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                        () -> repository.listPage(new TaskPageQuery("tenant-a", null, "broken", 10)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void cursorCodecUsesUpdatedAtAndTaskId() {
        TaskRecord record = buildRecord("tenant-a", "task-9", Instant.parse("2026-02-10T11:00:00Z"));
        String cursor = TaskListCursorCodec.encode(record);
        assertTrue(cursor.contains("|"));
        TaskListCursorCodec.TaskCursor parsed = TaskListCursorCodec.parse(cursor);
        assertEquals(record.getTaskId(), parsed.taskId());
        assertEquals(record.getUpdatedAt(), parsed.updatedAt());
    }

    private TaskRecord buildRecord(String tenantId, String taskId, Instant updatedAt) {
        TaskRecord record = new TaskRecord();
        record.setTenantId(tenantId);
        record.setTaskId(taskId);
        record.setWorkflowId("wf-" + taskId);
        record.setStatus(TaskStatus.COMPLETED);
        record.setCreatedAt(updatedAt.minusSeconds(30));
        record.setUpdatedAt(updatedAt);
        return record;
    }
}
