package com.example.agent.tools.execution;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.execution.mapping.ToolExecutionMapper;
import com.example.agent.capabilities.tools.execution.mapping.ToolFieldKeys;
import com.example.agent.capabilities.tools.execution.model.ToolExecutionResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionMapperTest {

    @Test
    void toInvocationArgumentsMergesRequestAndOverride() {
        ToolExecutionMapper mapper = new ToolExecutionMapper();
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("fromContext", "ctx"));

        Map<String, Object> values = mapper.toInvocationArguments(request, Map.of("fromOverride", "ext")).getValues();

        assertEquals("ping", values.get(ToolFieldKeys.QUERY));
        assertEquals("ctx", values.get("fromContext"));
        assertEquals("ext", values.get("fromOverride"));
    }

    @Test
    void toResultMapUsesStableFieldKeys() {
        ToolExecutionMapper mapper = new ToolExecutionMapper();
        ToolExecutionResult result = mapper.toExecutionResult(
                "demo_tool",
                mapper.toInvocationPayload(Map.of("value", "ok")),
                Map.of("totalTokens", 12),
                false,
                "rawref:v1:mem:1",
                "digest");

        Map<String, Object> map = mapper.toResultMap(result);

        assertEquals("demo_tool", map.get(ToolFieldKeys.TOOL));
        assertTrue(map.containsKey(ToolFieldKeys.RESULT));
        assertTrue(map.containsKey(ToolFieldKeys.TOKEN_USAGE));
        assertEquals("rawref:v1:mem:1", map.get(ToolFieldKeys.RAW_REF));
        assertFalse((Boolean) map.get(ToolFieldKeys.CACHE_HIT));
        assertEquals("digest", map.get(ToolFieldKeys.RESULT_DIGEST));
    }
}

