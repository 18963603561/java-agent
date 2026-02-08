package com.example.agent.memory;

import com.example.agent.capabilities.memory.HashEmbeddingService;
import com.example.agent.capabilities.memory.MemoryVectorProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HashEmbeddingServiceTest {

    @Test
    void embedShouldFallbackToMinimalDimensionWhenConfiguredInvalid() {
        MemoryVectorProperties properties = new MemoryVectorProperties();
        properties.setDimension(0);
        HashEmbeddingService service = new HashEmbeddingService(properties);

        List<Float> vector = service.embed("hello world");
        assertNotNull(vector);
        assertEquals(1, vector.size());
    }

    @Test
    void embedShouldHandleMinValueHashTokenWithoutOutOfBounds() {
        MemoryVectorProperties properties = new MemoryVectorProperties();
        properties.setDimension(8);
        HashEmbeddingService service = new HashEmbeddingService(properties);

        String minValueToken = "polygenelubricants";
        assertEquals(Integer.MIN_VALUE, minValueToken.hashCode());

        List<Float> vector = service.embed(minValueToken + " another");
        assertNotNull(vector);
        assertEquals(8, vector.size());
        assertFalse(vector.stream().allMatch(value -> value == 0.0f));
    }
}
