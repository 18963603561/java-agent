package com.example.agent.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OpenAPI 契约漂移校验测试，比较运行时导出与合同文件。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "tenant.whitelist-paths=/actuator/health,/actuator/info"
})
@AutoConfigureWebTestClient
class OpenApiContractDriftTest {

    @Autowired
    private WebTestClient webTestClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openApiContractShouldMatchRuntimeDoc() throws Exception {
        String runtimeDoc = webTestClient.get()
                .uri("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        JsonNode runtimeNode = objectMapper.readTree(runtimeDoc);
        JsonNode contractNode = loadContract();

        removeDescriptions(runtimeNode);
        removeDescriptions(contractNode);

        assertEquals(contractNode, runtimeNode, "OpenAPI drift detected");
    }

    private JsonNode loadContract() throws Exception {
        Path path = Path.of("specs/001-agent-core-spec/contracts/openapi.yaml");
        try (InputStream inputStream = Files.newInputStream(path)) {
            Object document = new Yaml().load(inputStream);
            String json = objectMapper.writeValueAsString(document);
            return objectMapper.readTree(json);
        }
    }

    /**
     * 递归移除 description 字段，避免文档描述差异导致误报。
     *
     * @param node 需要处理的节点
     */
    private void removeDescriptions(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("description");
            node.fields().forEachRemaining(entry -> removeDescriptions(entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(this::removeDescriptions);
        }
    }
}
