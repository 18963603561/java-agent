package com.example.agent.tools;

import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.governance.CircuitBreakerManager;
import com.example.agent.governance.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpToolClientTest {

    @Test
    void rejectsHostNotInAllowList() {
        McpServerProperties serverProperties = new McpServerProperties();
        McpServerProperties.McpServer server = new McpServerProperties.McpServer();
        server.setId("mcp-default");
        server.setBaseUrl("http://example.com");
        server.setAllowedHosts(List.of("allowed.com"));
        serverProperties.setServers(List.of(server));

        ExchangeFunction exchangeFunction = request -> Mono.error(new IllegalStateException("unexpected call"));
        McpToolClient client = buildClient(serverProperties, exchangeFunction);
        ReflectionTestUtils.setField(client, "remoteEnabled", true);
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5L);

        McpToolListRequest request = new McpToolListRequest();
        request.setServerId("mcp-default");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> client.listTools(request, new TenantContext("t1", "u1", List.of(), "req", "trace")));

        assertEquals("MCP_FORBIDDEN_HOST", ex.getErrorCode());
    }

    @Test
    void allowsHostInAllowList() {
        McpServerProperties serverProperties = new McpServerProperties();
        McpServerProperties.McpServer server = new McpServerProperties.McpServer();
        server.setId("mcp-default");
        server.setBaseUrl("http://example.com");
        server.setAllowedHosts(List.of("example.com"));
        serverProperties.setServers(List.of(server));

        byte[] body = toJsonBytes(Map.of("tools", List.of(), "hasMore", false));
        ExchangeFunction exchangeFunction = okResponse(body);
        McpToolClient client = buildClient(serverProperties, exchangeFunction);
        ReflectionTestUtils.setField(client, "remoteEnabled", true);
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5L);

        McpToolListRequest request = new McpToolListRequest();
        request.setServerId("mcp-default");

        McpToolListResponse response = client.listTools(request,
                new TenantContext("t1", "u1", List.of(), "req", "trace"));

        assertEquals(0, response.getTools().size());
        assertEquals(false, response.isHasMore());
    }

    @Test
    void listToolsUsesJsonRpcWhenConfigured() {
        McpServerProperties serverProperties = new McpServerProperties();
        McpServerProperties.McpServer server = new McpServerProperties.McpServer();
        server.setId("mcp-default");
        server.setBaseUrl("http://example.com/mcp/test");
        server.setAllowedHosts(List.of("example.com"));
        server.setProtocol("jsonrpc");
        serverProperties.setServers(List.of(server));

        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            int index = calls.incrementAndGet();
            Map<String, Object> payload;
            if (index == 1) {
                payload = Map.of("jsonrpc", "2.0", "id", "init", "result", Map.of());
            } else {
                payload = Map.of("jsonrpc", "2.0", "id", "list",
                        "result", Map.of("tools", List.of(), "hasMore", false));
            }
            return okResponse(toJsonBytes(payload)).exchange(request);
        };

        McpToolClient client = buildClient(serverProperties, exchangeFunction);
        ReflectionTestUtils.setField(client, "remoteEnabled", true);
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5L);

        McpToolListRequest request = new McpToolListRequest();
        request.setServerId("mcp-default");

        McpToolListResponse response = client.listTools(request,
                new TenantContext("t1", "u1", List.of(), "req", "trace"));

        assertEquals(2, calls.get());
        assertEquals(0, response.getTools().size());
        assertEquals(false, response.isHasMore());
    }

    @Test
    void rejectsResponseWhenTooLarge() {
        McpServerProperties serverProperties = new McpServerProperties();
        McpServerProperties.McpServer server = new McpServerProperties.McpServer();
        server.setId("mcp-default");
        server.setBaseUrl("http://example.com");
        server.setAllowedHosts(List.of("example.com"));
        server.setMaxResponseBytes(10L);
        serverProperties.setServers(List.of(server));

        byte[] body = "{\"tools\":[],\"hasMore\":false}".getBytes(StandardCharsets.UTF_8);
        ExchangeFunction exchangeFunction = okResponse(body);
        McpToolClient client = buildClient(serverProperties, exchangeFunction);
        ReflectionTestUtils.setField(client, "remoteEnabled", true);
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5L);

        McpToolListRequest request = new McpToolListRequest();
        request.setServerId("mcp-default");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> client.listTools(request, new TenantContext("t1", "u1", List.of(), "req", "trace")));

        assertEquals("MCP_RESPONSE_TOO_LARGE", ex.getErrorCode());
    }

    private McpToolClient buildClient(McpServerProperties properties, ExchangeFunction exchangeFunction) {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        RateLimitService rateLimitService = Mockito.mock(RateLimitService.class);
        CircuitBreakerManager circuitBreakerManager = Mockito.mock(CircuitBreakerManager.class);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        ObjectMapper objectMapper = new ObjectMapper();
        return new McpToolClient(properties, toolRegistry, rateLimitService, circuitBreakerManager, builder,
                objectMapper);
    }

    private ExchangeFunction okResponse(byte[] body) {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .body(Flux.just(new DefaultDataBufferFactory().wrap(body)))
                .build());
    }

    private byte[] toJsonBytes(Map<String, Object> payload) {
        try {
            return new ObjectMapper().writeValueAsBytes(payload);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
