package com.example.agent.runtime.raw;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.store.RawResultStore;
import org.junit.jupiter.api.Test;

class RawResultStoreDefaultMethodTest {

    @Test
    void loadByRefIdShouldThrowWhenImplementationNotOverride() {
        RawResultStore store = new RawResultStore() {
            @Override
            public RawRef store(String source, Object payload, String mediaType) {
                return null;
            }

            @Override
            public RawStoreType storeType() {
                return RawStoreType.MEM;
            }

            @Override
            public String loadByStoreId(String storeId) {
                return null;
            }
        };

        assertThrows(UnsupportedOperationException.class, () -> store.loadByRefId("rawref:v1:mem:demo"));
    }
}
