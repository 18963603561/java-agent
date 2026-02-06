package com.example.agent.runtime;

import com.example.agent.runtime.raw.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.summary.StepSummaryProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RawOutputEnvelopeBuilderTest {

    @Test
    void resolveRawRefPrefersTopLevelThenRawResultThenResultThenRaw() {
        StepSummaryProperties properties = new StepSummaryProperties();
        RawOutputEnvelopeBuilder builder = new RawOutputEnvelopeBuilder(properties);

        Map<String, Object> payload = new HashMap<>();
        payload.put("rawRef", "top");
        payload.put("rawResult", Map.of("rawRef", "rawResult"));
        payload.put("result", Map.of("rawRef", "result"));
        payload.put("raw", Map.of("rawRef", "raw"));

        assertEquals("top", builder.resolveRawRef(payload));

        payload.remove("rawRef");
        assertEquals("rawResult", builder.resolveRawRef(payload));

        payload.remove("rawResult");
        assertEquals("result", builder.resolveRawRef(payload));

        payload.remove("result");
        assertEquals("raw", builder.resolveRawRef(payload));
    }

    @Test
    void resolveRawRefIgnoresBlankTopLevelValue() {
        StepSummaryProperties properties = new StepSummaryProperties();
        RawOutputEnvelopeBuilder builder = new RawOutputEnvelopeBuilder(properties);

        Map<String, Object> payload = new HashMap<>();
        payload.put("rawRef", "   ");
        payload.put("rawResult", Map.of("rawRef", "rawResult"));

        assertEquals("rawResult", builder.resolveRawRef(payload));
    }
}

