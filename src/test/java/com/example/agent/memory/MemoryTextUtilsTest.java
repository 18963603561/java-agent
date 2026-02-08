package com.example.agent.memory;

import com.example.agent.capabilities.memory.support.MemoryTextUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTextUtilsTest {

    @Test
    void firstNonBlankShouldReturnFirstNonBlankValue() {
        assertEquals("alpha", MemoryTextUtils.firstNonBlank("alpha", "beta"));
        assertEquals("beta", MemoryTextUtils.firstNonBlank("  ", "beta"));
        assertEquals(null, MemoryTextUtils.firstNonBlank(null, "  "));
    }

    @Test
    void trimTextShouldApplyMaxCharsWhenNeeded() {
        assertEquals("abcd", MemoryTextUtils.trimText("  abcdef ", 4));
        assertEquals("abc", MemoryTextUtils.trimText(" abc ", 10));
        assertEquals("  abc ", MemoryTextUtils.trimText("  abc ", 0));
    }

    @Test
    void safeLowercaseContainsShouldIgnoreCaseAndHandleBlank() {
        assertTrue(MemoryTextUtils.safeLowercaseContains("Hello Agent", "agent"));
        assertFalse(MemoryTextUtils.safeLowercaseContains("Hello", ""));
        assertFalse(MemoryTextUtils.safeLowercaseContains(null, "he"));
    }
}
