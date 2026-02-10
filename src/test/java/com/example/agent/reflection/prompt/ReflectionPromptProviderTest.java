package com.example.agent.reflection.prompt;

import com.example.agent.reflection.ReflectionProperties;
import com.example.agent.reflection.model.ReflectionContext;
import com.example.agent.reflection.model.ReflectionOutputDigest;
import com.example.agent.reflection.model.ReflectionOutputSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionPromptProviderTest {

    @Test
    void buildPromptUsesExternalTemplate() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setPromptVersion("v1");
        properties.setPromptStrict(true);
        ReflectionPromptProvider provider = new ReflectionPromptProvider(
                properties,
                new DefaultResourceLoader(),
                new ObjectMapper(),
                new ReflectionPromptTemplateEngine()
        );

        ReflectionContext context = ReflectionContext.builder()
                .stepType("TOOL")
                .attempt(1)
                .outputSummary(new ReflectionOutputSummary("ok"))
                .outputDigest(new ReflectionOutputDigest(2, List.of("a", "b"), 120, false))
                .build();

        String prompt = provider.buildPrompt(context);
        assertNotNull(prompt);
        assertTrue(prompt.contains("REFLECTION_CONTEXT_JSON:"));
        assertTrue(prompt.contains("stepType"));
    }

    @Test
    void validateTemplateExistsThrowsWhenMissingAndStrict() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setPromptVersion("v999");
        properties.setPromptStrict(true);
        ReflectionPromptProvider provider = new ReflectionPromptProvider(
                properties,
                new DefaultResourceLoader(),
                new ObjectMapper(),
                new ReflectionPromptTemplateEngine()
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, provider::validateTemplateExists);
        assertTrue(exception.getMessage().startsWith("reflection_prompt_template_missing:"));
    }

    @Test
    void buildPromptFallsBackWhenMissingAndNotStrict() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setPromptVersion("v999");
        properties.setPromptStrict(false);
        ReflectionPromptProvider provider = new ReflectionPromptProvider(
                properties,
                new DefaultResourceLoader(),
                new ObjectMapper(),
                new ReflectionPromptTemplateEngine()
        );

        String prompt = provider.buildPrompt(ReflectionContext.builder().stepType("TOOL").attempt(1).build());
        assertNotNull(prompt);
        assertTrue(prompt.contains("REFLECTION_CONTEXT_JSON:"));
    }
}

