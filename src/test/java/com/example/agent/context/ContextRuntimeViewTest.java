package com.example.agent.context;

import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.runtime.ContextRuntimeView;
import com.example.agent.capabilities.context.runtime.ContextRuntimeViews;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextRuntimeViewTest {

    private static final Logger LOG = LoggerFactory.getLogger(ContextRuntimeViewTest.class);

    @Test
    void readTypedValuesShouldRespectConversions() {
        ContextRuntimeView view = ContextRuntimeViews.readOnly(
                Map.of(
                        "s", "value",
                        "i", "123",
                        "b", "true",
                        "list", List.of("a", "b")
                ),
                LOG,
                new MetricsPublisher(new SimpleMeterRegistry()));

        assertEquals("value", view.getString("s"));
        assertEquals(123, view.getInteger("i"));
        assertEquals(Boolean.TRUE, view.getBoolean("b"));
        assertEquals(List.of("a", "b"), view.getStringList("list"));
    }

    @Test
    void readInvalidValuesShouldReturnNull() {
        ContextRuntimeView view = ContextRuntimeViews.readOnly(
                Map.of(
                        "i", "abc",
                        "b", "not_boolean",
                        "list", Map.of("k", "v")
                ),
                LOG,
                new MetricsPublisher(new SimpleMeterRegistry()));

        assertNull(view.getInteger("i"));
        assertNull(view.getBoolean("b"));
        assertNull(view.getStringList("list"));
    }

    @Test
    void readEvidencePackShouldReturnTypedObject() {
        EvidencePack evidencePack = new EvidencePack();
        ContextRuntimeView view = ContextRuntimeViews.readOnly(
                Map.of("evidencePack", evidencePack),
                LOG,
                null);

        assertEquals(evidencePack, view.getEvidencePack());
    }

    @Test
    void readContextCompressionShouldReturnTypedObject() {
        ContextCompressionResult compressionResult = new ContextCompressionResult();
        compressionResult.setTriggered(true);
        ContextRuntimeView view = ContextRuntimeViews.readOnly(
                Map.of("contextCompression", compressionResult),
                LOG,
                null);

        assertEquals(compressionResult, view.getContextCompression());
    }
}

