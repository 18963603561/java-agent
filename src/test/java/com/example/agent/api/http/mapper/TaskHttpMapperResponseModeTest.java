package com.example.agent.api.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.api.http.config.ApiResponseProperties;
import com.example.agent.api.http.dto.TaskListResponse;
import com.example.agent.api.http.dto.TaskResponse;
import com.example.agent.api.http.dto.TaskStatusResponse;
import com.example.agent.api.http.response.FinalOutputDedupPolicy;
import com.example.agent.api.http.response.ResponseMode;
import com.example.agent.api.http.response.ResultPayloadCompactor;
import com.example.agent.api.http.response.StepSummaryCompactionPolicy;
import com.example.agent.orchestration.task.TaskStatus;
import com.example.agent.orchestration.task.contract.TaskListView;
import com.example.agent.orchestration.task.contract.TaskStatusView;
import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskHttpMapperResponseModeTest {

    @Test
    void shouldUseCompactModeByDefault() {
        TaskHttpMapper mapper = newMapper(ResponseMode.COMPACT);
        TaskSubmissionResult submissionResult = new TaskSubmissionResult("task-1", "wf-1", "COMPLETED");
        submissionResult.setResult(buildPayload());

        TaskResponse response = mapper.toTaskResponse(submissionResult);

        assertThat(response.getResult()).doesNotContainKey("planSummary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) response.getResult().get("steps");
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0)).containsEntry("stepIndex", 1);
        assertThat(steps.get(0)).doesNotContainKey("raw");

        @SuppressWarnings("unchecked")
        Map<String, Object> finalOutput = (Map<String, Object>) response.getResult().get("finalOutput");
        assertThat(finalOutput).doesNotContainKey("summary");
    }

    @Test
    void shouldKeepFullStructureWhenModeIsFull() {
        TaskHttpMapper mapper = newMapper(ResponseMode.COMPACT);
        TaskSubmissionResult submissionResult = new TaskSubmissionResult("task-1", "wf-1", "COMPLETED");
        submissionResult.setResult(buildPayload());

        TaskResponse response = mapper.toTaskResponse(submissionResult, "full");

        assertThat(response.getResult()).containsEntry("planSummary", "plan-summary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) response.getResult().get("steps");
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0)).containsKey("raw");
        assertThat(steps.get(0).get("raw")).isNull();
        assertThat(steps.get(0)).containsEntry("stepIndex", 1);

        @SuppressWarnings("unchecked")
        Map<String, Object> finalOutput = (Map<String, Object>) response.getResult().get("finalOutput");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) finalOutput.get("summary");
        assertThat(summary).containsEntry("text", "final-answer");
    }

    @Test
    void shouldFallbackToConfiguredDefaultModeWhenModeIsInvalid() {
        TaskHttpMapper mapper = newMapper(ResponseMode.FULL);
        TaskSubmissionResult submissionResult = new TaskSubmissionResult("task-1", "wf-1", "COMPLETED");
        submissionResult.setResult(buildPayload());

        TaskResponse response = mapper.toTaskResponse(submissionResult, "unknown-mode");

        assertThat(response.getResult()).containsEntry("planSummary", "plan-summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> finalOutput = (Map<String, Object>) response.getResult().get("finalOutput");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) finalOutput.get("summary");
        assertThat(summary).containsEntry("text", "final-answer");
    }

    @Test
    void shouldPropagateResponseModeToStatusAndListMapping() {
        TaskHttpMapper mapper = newMapper(ResponseMode.COMPACT);
        TaskStatusView statusView = new TaskStatusView(
                "task-1",
                "wf-1",
                TaskStatus.COMPLETED,
                Instant.now(),
                buildPayload()
        );

        TaskStatusResponse compactStatus = mapper.toTaskStatusResponse(statusView, "compact");
        assertThat(compactStatus.getResult()).doesNotContainKey("planSummary");

        TaskListView listView = new TaskListView(List.of(statusView), null, false, 1);
        TaskListResponse fullList = mapper.toTaskListResponse(listView, "full");
        assertThat(fullList.getTasks()).hasSize(1);
        assertThat(fullList.getTasks().get(0).getResult()).containsEntry("planSummary", "plan-summary");
    }

    private TaskHttpMapper newMapper(ResponseMode defaultMode) {
        ApiResponseProperties properties = new ApiResponseProperties();
        properties.setDefaultMode(defaultMode);
        ResultPayloadCompactor compactor = new ResultPayloadCompactor(
                new FinalOutputDedupPolicy(),
                new StepSummaryCompactionPolicy()
        );
        return new TaskHttpMapper(new ObjectMapper(), compactor, properties);
    }

    private Map<String, Object> buildPayload() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("raw", null);
        step.put("meta", Map.of("seq", 14));
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step);

        Map<String, Object> finalMeta = new LinkedHashMap<>();
        finalMeta.put("planSummary", "plan-summary");

        Map<String, Object> finalResult = new LinkedHashMap<>();
        finalResult.put("answer", "final-answer");

        Map<String, Object> finalSummary = new LinkedHashMap<>();
        finalSummary.put("text", "final-answer");
        finalSummary.put("highlights", new ArrayList<>());

        Map<String, Object> finalOutput = new LinkedHashMap<>();
        finalOutput.put("meta", finalMeta);
        finalOutput.put("result", finalResult);
        finalOutput.put("summary", finalSummary);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", "plan-id-1");
        payload.put("planSummary", "plan-summary");
        payload.put("steps", steps);
        payload.put("finalOutput", finalOutput);
        payload.put("nullableField", null);
        return payload;
    }
}
