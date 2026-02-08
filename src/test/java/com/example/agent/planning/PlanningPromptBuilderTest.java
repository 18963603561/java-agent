package com.example.agent.planning;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.agent.api.http.dto.TaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * PlanningPromptBuilder 单元测试。
 */
class PlanningPromptBuilderTest {

    @Test
    void buildPromptRendersTemplateWithContextJson() {
        String template = "PLAN_CONTEXT_JSON:%s";
        PlanningPromptBuilder builder = new PlanningPromptBuilder(
                new ObjectMapper(),
                new InMemoryResourceLoader(template),
                "memory:planner-template");

        TaskRequest request = new TaskRequest();
        request.setQuery("what is java agent");
        String prompt = builder.buildPrompt(request, Map.of("tools", java.util.List.of("demo_tool")));

        assertTrue(prompt.startsWith("PLAN_CONTEXT_JSON:"));
        assertTrue(prompt.contains("\"query\":\"what is java agent\""));
        assertTrue(prompt.contains("\"contextSummary\""));
        assertTrue(prompt.contains("\"demo_tool\""));
    }

    @Test
    void buildPromptFallsBackToEmptyContextSummaryWhenNull() {
        String template = "CTX=%s";
        PlanningPromptBuilder builder = new PlanningPromptBuilder(
                new ObjectMapper(),
                new InMemoryResourceLoader(template),
                "memory:planner-template");

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        String prompt = builder.buildPrompt(request, null);

        assertTrue(prompt.contains("\"contextSummary\":{}"));
    }

    @Test
    void buildPromptShouldReuseTemplateCache() {
        String template = "CACHE=%s";
        CountingResourceLoader loader = new CountingResourceLoader(template);
        PlanningPromptBuilder builder = new PlanningPromptBuilder(
                new ObjectMapper(),
                loader,
                "memory:planner-template");

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        builder.buildPrompt(request, Map.of("k", "v"));
        builder.buildPrompt(request, Map.of("k", "v2"));

        assertTrue(loader.getLoadCount() == 1);
    }

    @Test
    void buildPromptThrowsWhenTemplateMissing() {
        PlanningPromptBuilder builder = new PlanningPromptBuilder(
                new ObjectMapper(),
                new MissingResourceLoader(),
                "memory:planner-template");

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        assertThrows(IllegalStateException.class, () -> builder.buildPrompt(request, Map.of()));
    }

    private static class InMemoryResourceLoader extends DefaultResourceLoader {

        private final String template;

        private InMemoryResourceLoader(String template) {
            this.template = template;
        }

        @Override
        public Resource getResource(String location) {
            return new ByteArrayResource(template.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static class MissingResourceLoader extends DefaultResourceLoader {

        @Override
        public Resource getResource(String location) {
            return new ByteArrayResource(new byte[0]) {
                @Override
                public boolean exists() {
                    return false;
                }
            };
        }
    }

    private static class CountingResourceLoader extends DefaultResourceLoader {

        private final String template;
        private int loadCount;

        private CountingResourceLoader(String template) {
            this.template = template;
        }

        @Override
        public Resource getResource(String location) {
            loadCount++;
            return new ByteArrayResource(template.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        private int getLoadCount() {
            return loadCount;
        }
    }
}
