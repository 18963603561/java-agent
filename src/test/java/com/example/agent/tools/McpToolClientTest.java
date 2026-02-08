package com.example.agent.tools;

import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.circuitbreaker.CircuitBreakerManager;
import com.example.agent.governance.ratelimit.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
import com.example.agent.capabilities.tools.mcp.McpServerProperties;
import com.example.agent.capabilities.tools.mcp.McpToolClient;
import com.example.agent.capabilities.tools.mcp.McpToolCallRequest;
import com.example.agent.capabilities.tools.mcp.McpToolCallResponse;
import com.example.agent.capabilities.tools.mcp.McpToolListRequest;
import com.example.agent.capabilities.tools.mcp.McpToolListResponse;
import com.example.agent.capabilities.tools.mcp.protocol.McpJsonRpcAdapter;
import com.example.agent.capabilities.tools.mcp.protocol.McpRestAdapter;
import com.example.agent.capabilities.tools.mcp.session.McpSseSessionManager;
import com.example.agent.capabilities.tools.mcp.strategy.McpCallStrategyResolver;
import com.example.agent.capabilities.tools.mcp.transport.McpHttpTransport;

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

    @Test
    void fallsBackToJdkClientWhenWebClientTimeout() throws Exception {
        HttpServer fakeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeServer.createContext("/mcp/test/tools/list", exchange -> {
            byte[] body = "{\"tools\":[],\"hasMore\":false}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        fakeServer.start();
        try {
            int port = fakeServer.getAddress().getPort();
            McpServerProperties serverProperties = new McpServerProperties();
            McpServerProperties.McpServer server = new McpServerProperties.McpServer();
            server.setId("mcp-default");
            server.setBaseUrl("http://127.0.0.1:" + port + "/mcp/test");
            server.setAllowedHosts(List.of("127.0.0.1"));
            serverProperties.setServers(List.of(server));

            ExchangeFunction timeoutExchange = request -> Mono.error(new java.util.concurrent.TimeoutException("simulated timeout"));
            McpToolClient client = buildClient(serverProperties, timeoutExchange);
            ReflectionTestUtils.setField(client, "remoteEnabled", true);
            ReflectionTestUtils.setField(client, "timeoutSeconds", 2L);

            McpToolListRequest request = new McpToolListRequest();
            request.setServerId("mcp-default");

            McpToolListResponse response = client.listTools(request,
                    new TenantContext("t1", "u1", List.of(), "req", "trace"));

            assertEquals(0, response.getTools().size());
            assertEquals(false, response.isHasMore());
        } finally {
            fakeServer.stop(0);
        }
    }

    @Test
    void listToolsRejectsNullRequest() {
        McpServerProperties serverProperties = new McpServerProperties();
        McpToolClient client = buildClient(serverProperties, okResponse(toJsonBytes(Map.of("tools", List.of(), "hasMore", false))));

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> client.listTools(null, new TenantContext("t1", "u1", List.of(), "req", "trace")));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("request 不能为空", ex.getReason());
    }

    @Test
    void listToolsRejectsBlankTenantId() {
        McpServerProperties serverProperties = new McpServerProperties();
        McpToolClient client = buildClient(serverProperties, okResponse(toJsonBytes(Map.of("tools", List.of(), "hasMore", false))));
        McpToolListRequest request = new McpToolListRequest();

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> client.listTools(request, new TenantContext(" ", "u1", List.of(), "req", "trace")));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("tenantContext.tenantId 不能为空", ex.getReason());
    }

    @Test
    void callToolRejectsBlankToolName() {
        McpServerProperties serverProperties = new McpServerProperties();
        McpServerProperties.McpServer server = new McpServerProperties.McpServer();
        server.setId("mcp-default");
        server.setBaseUrl("http://example.com");
        server.setAllowedHosts(List.of("example.com"));
        serverProperties.setServers(List.of(server));

        McpToolClient client = buildClient(serverProperties, okResponse(toJsonBytes(Map.of("result", Map.of(), "status", "SUCCESS"))));
        McpToolCallRequest request = new McpToolCallRequest();
        request.setToolName("  ");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> client.callTool(request, new TenantContext("t1", "u1", List.of(), "req", "trace")));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("request.toolName 不能为空", ex.getReason());
    }

    private McpToolClient buildClient(McpServerProperties properties, ExchangeFunction exchangeFunction) {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        RateLimitService rateLimitService = Mockito.mock(RateLimitService.class);
        CircuitBreakerManager circuitBreakerManager = Mockito.mock(CircuitBreakerManager.class);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        ObjectMapper objectMapper = new ObjectMapper();
        McpCallStrategyResolver strategyResolver = new McpCallStrategyResolver();
        McpSseSessionManager sseSessionManager = new McpSseSessionManager();
        McpHttpTransport httpTransport = new McpHttpTransport(builder, objectMapper);
        McpJsonRpcAdapter jsonRpcAdapter = new McpJsonRpcAdapter(httpTransport, sseSessionManager);
        McpRestAdapter restAdapter = new McpRestAdapter(httpTransport, sseSessionManager, objectMapper);
        return new McpToolClient(properties, toolRegistry, rateLimitService, circuitBreakerManager, builder,
                objectMapper, strategyResolver, jsonRpcAdapter, restAdapter);
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
