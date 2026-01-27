package com.example.agent.gateway.controller;

import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.tools.McpToolCallRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.api-keys.test-key.user-id=test-user",
        "auth.api-keys.test-key.roles=ROLE_USER",
        "auth.trusted-upstream.enabled=false",
        "tenant.whitelist-paths=/actuator/health,/actuator/info",
        "agent.sse.timeoutSeconds=0",
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
class McpToolEventTest {

    private static final String API_KEY = "test-key";
    private static final String TENANT_ID = "tenant-a";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void mcpToolCallEmitsToolEvents() {
        String callId = "call-evt-001";
        String workflowId = "mcp-" + callId;

        FluxExchangeResult<ServerSentEvent<StreamEvent>> sseResult = webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/stream/sse")
                        .queryParam("workflow_id", workflowId)
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<StreamEvent>>() {
                });

        McpToolCallRequest callRequest = new McpToolCallRequest();
        callRequest.setCallId(callId);
        callRequest.setServerId("mcp-default");
        callRequest.setToolName("demo_tool");
        callRequest.setArguments(Map.of("text", "ping"));
        callRequest.setTimeoutMs(3000);

        webTestClient.post()
                .uri("/api/v1/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", API_KEY)
                .header("X-Tenant-Id", TENANT_ID)
                .bodyValue(callRequest)
                .exchange()
                .expectStatus().isOk();

        List<ServerSentEvent<StreamEvent>> events = sseResult.getResponseBody()
                .take(5)
                .collectList()
                .block(Duration.ofSeconds(2));
        assertNotNull(events);
        assertTrue(events.stream()
                .map(ServerSentEvent::data)
                .filter(java.util.Objects::nonNull)
                .anyMatch(data -> data.getType() == EventType.TOOL_INVOKED));
        assertTrue(events.stream()
                .map(ServerSentEvent::data)
                .filter(java.util.Objects::nonNull)
                .anyMatch(data -> data.getType() == EventType.TOOL_OBSERVATION));
    }
}
