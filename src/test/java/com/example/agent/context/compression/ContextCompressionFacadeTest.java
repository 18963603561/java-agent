package com.example.agent.context.compression;

import com.example.agent.budget.trim.application.ContextCompressionService;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.budget.trim.model.ContextCompressionResult;
import com.example.agent.capabilities.context.compression.DefaultContextCompressionFacade;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上下文压缩门面测试。
 */
class ContextCompressionFacadeTest {

    @Test
    void shouldDelegateToCompressionService() {
        ContextCompressionService compressionService = Mockito.mock(ContextCompressionService.class);
        DefaultContextCompressionFacade facade = new DefaultContextCompressionFacade(compressionService);

        ContextCompressionResult expected = new ContextCompressionResult();
        expected.setTriggered(true);
        when(compressionService.compressIfNeeded(any(ContextCompressionRequest.class))).thenReturn(expected);

        ContextCompressionResult actual = facade.compressIfNeeded(new ContextCompressionRequest());

        assertSame(expected, actual);
        verify(compressionService).compressIfNeeded(any(ContextCompressionRequest.class));
    }

    @Test
    void shouldReturnEmptyResultWhenServiceMissing() {
        DefaultContextCompressionFacade facade = new DefaultContextCompressionFacade(null);

        ContextCompressionResult result = facade.compressIfNeeded(new ContextCompressionRequest());

        assertNotNull(result);
    }
}

