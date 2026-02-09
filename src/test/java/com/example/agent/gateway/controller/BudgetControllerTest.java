package com.example.agent.gateway.controller;

import com.example.agent.budget.token.model.TokenUsageInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 预算接口测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.api-keys.test-key.user-id=test-user",
        "auth.api-keys.test-key.roles=ROLE_USER",
        "auth.trusted-upstream.enabled=false",
        "tenant.whitelist-paths=/actuator/health,/actuator/info"
})
@AutoConfigureWebTestClient
class BudgetControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void usageIdIsDeduplicated() {
        TokenUsageInput input = new TokenUsageInput();
        input.setUsageId("usage-1");
        input.setTaskId("task-budget-1");
        input.setAgentId("agent-1");
        input.setModel("cheap");
        input.setProvider("local");
        input.setInputTokens(10);
        input.setOutputTokens(5);
        input.setTotalTokens(15);
        input.setCostUsd(0.0);

        for (int i = 0; i < 2; i++) {
            webTestClient.post()
                    .uri("/api/v1/budget/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", "test-key")
                    .header("X-Tenant-Id", "tenant-a")
                    .bodyValue(input)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.usageId").isEqualTo("usage-1");
        }

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/budget/summary")
                        .queryParam("taskId", "task-budget-1")
                        .build())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.taskId").isEqualTo("task-budget-1")
                .jsonPath("$.data.totalTokens").isEqualTo(15)
                .jsonPath("$.data.totalCostUsd").exists();
    }
}

