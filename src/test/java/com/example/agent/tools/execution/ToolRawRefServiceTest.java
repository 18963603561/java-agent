package com.example.agent.tools.execution;

import com.example.agent.capabilities.tools.execution.service.ToolRawRefService;
import com.example.agent.runtime.raw.ref.RawRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ToolRawRefServiceTest {

    @Test
    void resolveOutputRawRefPrefersRefId() {
        ToolRawRefService service = new ToolRawRefService();
        RawRef rawRef = new RawRef();
        rawRef.setRefId("rawref:v1:redis:1");
        rawRef.setKey("legacy-key");

        assertEquals("rawref:v1:redis:1", service.resolveOutputRawRef(rawRef));
    }

    @Test
    void resolveOutputRawRefFallsBackToKey() {
        ToolRawRefService service = new ToolRawRefService();
        RawRef rawRef = new RawRef();
        rawRef.setKey("legacy-key");

        assertEquals("legacy-key", service.resolveOutputRawRef(rawRef));
    }

    @Test
    void resolveOutputRawRefReturnsNullWhenEmpty() {
        ToolRawRefService service = new ToolRawRefService();

        assertNull(service.resolveOutputRawRef(null));
        assertNull(service.resolveOutputRawRef(new RawRef()));
    }
}

