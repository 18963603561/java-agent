package com.example.agent.gateway.controller;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.common.TaskResponse;
import com.example.agent.orchestrator.TaskQueryService;
import com.example.agent.orchestrator.TaskSubmissionService;
import com.example.agent.streaming.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.api-keys.test-key.user-id=test-user",
        "auth.api-keys.test-key.roles=ROLE_USER",
        "auth.jwt.enabled=true",
        "auth.jwt.verify-signature=true",
        "auth.jwt.secret=test-secret",
        "auth.trusted-upstream.enabled=false",
        "tenant.whitelist-paths=/actuator/health,/actuator/info",
        "agent.memory.vector.enabled=false"
})
@AutoConfigureWebTestClient
class SecurityValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TaskSubmissionService taskSubmissionService;

    @MockBean
    private TaskQueryService taskQueryService;

    @MockBean
    private EventStreamService eventStreamService;

    private static final String JWT_SECRET = "test-secret";

    @Test
    void missingApiKeyReturnsUnauthorized() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-1");

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidApiKeyReturnsForbidden() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-2");

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "tenant-a")
                .header("X-API-Key", "invalid-key")
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void forgedUserHeadersAreIgnoredByDefault() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-3");
        when(taskSubmissionService.submitTask(any(), any()))
                .thenReturn(new TaskResponse("task-1", "wf-1", "SUBMITTED"));

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "tenant-a")
                .header("X-API-Key", "test-key")
                .header("X-User-Id", "evil-user")
                .header("X-Roles", "admin")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();

        ArgumentCaptor<TenantContext> captor = ArgumentCaptor.forClass(TenantContext.class);
        verify(taskSubmissionService, times(1)).submitTask(any(), captor.capture());
        TenantContext tenantContext = captor.getValue();
        assertEquals("test-user", tenantContext.getUserId());
        assertTrue(tenantContext.getRoles().contains("ROLE_USER"));
    }

    @Test
    void jwtAuthUsesClaims() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-jwt-1");
        when(taskSubmissionService.submitTask(any(), any()))
                .thenReturn(new TaskResponse("task-jwt", "wf-jwt", "SUBMITTED"));

        String token = buildJwt("jwt-user", List.of("ROLE_USER", "ROLE_TEST"), "tenant-a", 3600);

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();

        ArgumentCaptor<TenantContext> captor = ArgumentCaptor.forClass(TenantContext.class);
        verify(taskSubmissionService, times(1)).submitTask(any(), captor.capture());
        TenantContext tenantContext = captor.getValue();
        assertEquals("jwt-user", tenantContext.getUserId());
        assertTrue(tenantContext.getRoles().contains("ROLE_TEST"));
    }

    @Test
    void jwtTenantScopeMismatchReturnsForbidden() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-jwt-2");
        String token = buildJwt("jwt-user", List.of("ROLE_USER"), "tenant-a", 3600);

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "tenant-b")
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void missingTenantIdReturnsBadRequest() {
        webTestClient.get()
                .uri("/api/v1/tasks?size=1")
                .header("X-API-Key", "test-key")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_MISSING");
    }

    @Test
    void healthEndpointBypassesTenantCheck() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void sizeMissingReturnsBadRequest() {
        webTestClient.get()
                .uri("/api/v1/tasks")
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void sizeOutOfRangeReturnsBadRequest() {
        webTestClient.get()
                .uri("/api/v1/tasks?size=1000")
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    private String buildJwt(String userId, List<String> roles, String tenantId, long ttlSeconds) {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", userId);
        payload.put("roles", roles);
        payload.put("tenant", tenantId);
        payload.put("exp", Instant.now().getEpochSecond() + ttlSeconds);
        try {
            ObjectMapper mapper = new ObjectMapper();
            String headerJson = mapper.writeValueAsString(header);
            String payloadJson = mapper.writeValueAsString(payload);
            String headerEncoded = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadEncoded = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(headerEncoded + "." + payloadEncoded, JWT_SECRET);
            return headerEncoded + "." + payloadEncoded + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String base64UrlEncode(byte[] content) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(content);
    }

    private String sign(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64UrlEncode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
