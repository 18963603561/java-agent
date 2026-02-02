package com.example.agent.model;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultPromptTemplateTest {

    @Test
    void renderUsesDefaultDeveloperWhenConfigBlank() {
        DefaultPromptTemplate template = new DefaultPromptTemplate();
        ReflectionTestUtils.setField(template, "systemMessage", "系统提示");
        ReflectionTestUtils.setField(template, "developerMessage", "   ");

        List<PromptMessage> messages = template.render(new PromptRenderContext());

        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertEquals(DefaultPromptTemplate.DEFAULT_DEVELOPER_MESSAGE, messages.get(1).getContent());
    }
}
