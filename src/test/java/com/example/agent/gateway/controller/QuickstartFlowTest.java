package com.example.agent.gateway.controller;

import com.example.agent.common.TaskRequest;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.governance.ReplayRequest;
import com.example.agent.policy.PolicyRequest;
import com.example.agent.tools.McpToolCallRequest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quickstart 链路集成测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.api-keys.test-key.user-id=test-user",
        "auth.api-keys.test-key.roles=ROLE_USER",
        "auth.trusted-upstream.enabled=false",
        "tenant.whitelist-paths=/actuator/health,/actuator/info",
        "agent.sse.timeoutSeconds=0",
        "agent.mcp.servers[0].id=mcp-default",
        "agent.mcp.servers[0].available=true",
        "agent.mcp.servers[0].base-url=http://localhost:9999",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
})
@AutoConfigureWebTestClient
class QuickstartFlowTest {

    private static final String API_KEY = "test-key";
    private static final String TENANT_ID = "tenant-a";
    private static final String TRACE_ID = "trace-qs";
    private static final String REQUEST_ID = "req-qs";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void quickstartFlowCoversCoreEndpoints() {
        TaskRequest request = new TaskRequest();
        request.setQuery("call_tool_and_fail");
        request.setSessionId("s-001");
        request.setContext(Map.of("tool", "demo_tool"));
        request.setIdempotencyKey("idem-quickstart");

        AtomicReference<String> workflowIdRef = new AtomicReference<>();
        AtomicReference<String> taskIdRef = new AtomicReference<>();

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Trace-Id", TRACE_ID)
                .header("X-Request-Id", REQUEST_ID)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workflowId").value(workflowIdRef::set)
                .jsonPath("$.data.taskId").value(taskIdRef::set);

        String workflowId = workflowIdRef.get();
        String taskId = taskIdRef.get();
        assertNotNull(workflowId);
        assertNotNull(taskId);

        FluxExchangeResult<ServerSentEvent<StreamEvent>> sseResult = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/stream/sse")
                        .queryParam("workflow_id", workflowId)
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Trace-Id", TRACE_ID)
                .header("X-Request-Id", REQUEST_ID)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<StreamEvent>>() {
                });

        ServerSentEvent<StreamEvent> event = sseResult.getResponseBody()
                .take(1)
                .blockFirst(Duration.ofSeconds(2));
        assertNotNull(event);
        assertNotNull(event.id());
        assertTrue(event.id().startsWith(workflowId + ":"));

        webTestClient.get()
                .uri("/api/v1/timeline/steps?workflowId={workflowId}&size=20", workflowId)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Trace-Id", TRACE_ID)
                .header("X-Request-Id", REQUEST_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workflowId").isEqualTo(workflowId)
                .jsonPath("$.data.steps.length()").value(value -> {
                    int count = (Integer) value;
                    assertTrue(count > 0);
                });

        webTestClient.get()
                .uri("/api/v1/timeline/steps?workflowId={workflowId}&size=20", workflowId)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", "tenant-b")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND");

        McpToolCallRequest callRequest = new McpToolCallRequest();
        callRequest.setCallId("call-001");
        callRequest.setServerId("mcp-default");
        callRequest.setToolName("demo_tool");
        callRequest.setArguments(Map.of("text", "ping"));
        callRequest.setTimeoutMs(3000);

        webTestClient.post()
                .uri("/api/v1/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Trace-Id", TRACE_ID)
                .header("X-Request-Id", REQUEST_ID)
                .bodyValue(callRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.callId").isEqualTo("call-001")
                .jsonPath("$.data.status").isEqualTo("SUCCESS");

        PolicyRequest policyRequest = new PolicyRequest();
        policyRequest.setPolicyId("default");
        policyRequest.setAction("submit");
        policyRequest.setResource("task");
        policyRequest.setInput(Map.of("risk", "high"));

        webTestClient.post()
                .uri("/api/v1/policy/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Trace-Id", TRACE_ID)
                .header("X-Request-Id", REQUEST_ID)
                .bodyValue(policyRequest)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("POLICY_DENIED")
                .jsonPath("$.traceId").isEqualTo(TRACE_ID)
                .jsonPath("$.requestId").isEqualTo(REQUEST_ID)
                .jsonPath("$.details.path").exists();

        ReplayRequest replayRequest = new ReplayRequest();
        replayRequest.setTaskId(taskId);
        replayRequest.setMode("full");

        webTestClient.post()
                .uri("/api/v1/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Trace-Id", TRACE_ID)
                .header("X-Request-Id", REQUEST_ID)
                .bodyValue(replayRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.replayId").isNotEmpty()
                .jsonPath("$.data.status").isEqualTo("COMPLETED");

        ReplayRequest missingReplay = new ReplayRequest();
        missingReplay.setTaskId("task-not-found");
        missingReplay.setMode("full");

        webTestClient.post()
                .uri("/api/v1/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Trace-Id", TRACE_ID)
                .header("X-Request-Id", REQUEST_ID)
                .bodyValue(missingReplay)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("REPLAY_NOT_FOUND");
    }
}
