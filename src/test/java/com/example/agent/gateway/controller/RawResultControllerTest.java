package com.example.agent.gateway.controller;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.runtime.raw.RawResultResolveResult;
import com.example.agent.runtime.raw.RawResultResolveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.api-keys.test-key.user-id=test-user",
        "auth.api-keys.test-key.roles=ROLE_USER",
        "auth.trusted-upstream.enabled=false",
        "tenant.whitelist-paths=/actuator/health,/actuator/info",
        "agent.memory.vector.enabled=false",
        "agent.runtime.raw.store-mode=mem"
})
@AutoConfigureWebTestClient
class RawResultControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RawResultResolveService rawResultResolveService;

    @Test
    void resolveShouldReturnDataWhenRefValid() {
        RawResultResolveResult result = new RawResultResolveResult();
        result.setInputRef("rawref:v1:mem:raw:1");
        result.setResolvedRefId("rawref:v1:mem:raw:1");
        result.setStoreType("mem");
        result.setStoreReason("resolved_from_mem");
        result.setPayload("{\"k\":1}");
        result.setSize(7L);
        when(rawResultResolveService.resolve("rawref:v1:mem:raw:1")).thenReturn(result);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/raw/resolve")
                        .queryParam("ref", "rawref:v1:mem:raw:1")
                        .build())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.storeType").isEqualTo("mem")
                .jsonPath("$.data.resolvedRefId").isEqualTo("rawref:v1:mem:raw:1")
                .jsonPath("$.data.payload").isEqualTo("{\"k\":1}");
    }

    @Test
    void resolveShouldReturnBadRequestWhenRefInvalid() {
        when(rawResultResolveService.resolve(anyString()))
                .thenThrow(new ErrorCodeException(HttpStatus.BAD_REQUEST, "RAW_REF_INVALID", "非法引用"));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/raw/resolve")
                        .queryParam("ref", "invalid")
                        .build())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("RAW_REF_INVALID");
    }

    @Test
    void downloadShouldReturnFileContent() {
        when(rawResultResolveService.downloadText("demo.txt")).thenReturn("hello");

        webTestClient.get()
                .uri("/api/v1/raw/files/demo.txt")
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Type", "text/plain")
                .expectBody(String.class).isEqualTo("hello");
    }

    @Test
    void downloadByRefShouldReturnResolvedPayload() {
        RawResultResolveResult result = new RawResultResolveResult();
        result.setResolvedRefId("rawref:v1:mem:raw:1");
        result.setStoreType("mem");
        result.setPayload("{\"x\":1}");
        when(rawResultResolveService.resolve("rawref:v1:mem:raw:1")).thenReturn(result);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/raw/download")
                        .queryParam("ref", "rawref:v1:mem:raw:1")
                        .build())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Type", "text/plain")
                .expectBody(String.class).isEqualTo("{\"x\":1}");
    }
}
