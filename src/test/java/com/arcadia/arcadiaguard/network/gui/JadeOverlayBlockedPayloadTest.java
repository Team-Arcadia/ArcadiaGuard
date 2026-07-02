package com.arcadia.arcadiaguard.network.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class JadeOverlayBlockedPayloadTest {

    @Test
    void codec_preservesBlockedState() {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        JadeOverlayBlockedPayload.STREAM_CODEC.encode(buf, new JadeOverlayBlockedPayload(true));
        assertTrue(JadeOverlayBlockedPayload.STREAM_CODEC.decode(buf).blocked());
    }

    @Test
    void codec_preservesUnblockedState() {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        JadeOverlayBlockedPayload.STREAM_CODEC.encode(buf, new JadeOverlayBlockedPayload(false));
        assertFalse(JadeOverlayBlockedPayload.STREAM_CODEC.decode(buf).blocked());
    }
}
