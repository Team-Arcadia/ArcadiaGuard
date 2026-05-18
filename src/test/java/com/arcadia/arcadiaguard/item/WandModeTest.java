package com.arcadia.arcadiaguard.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/** Tests {@link WandMode} Codec + StreamCodec round-trip. */
class WandModeTest {

    @Test
    void streamCodec_editRoundTrip() {
        var buf = Unpooled.buffer();
        WandMode.STREAM_CODEC.encode(buf, WandMode.EDIT);
        assertEquals(WandMode.EDIT, WandMode.STREAM_CODEC.decode(buf));
    }

    @Test
    void streamCodec_viewRoundTrip() {
        var buf = Unpooled.buffer();
        WandMode.STREAM_CODEC.encode(buf, WandMode.VIEW);
        assertEquals(WandMode.VIEW, WandMode.STREAM_CODEC.decode(buf));
    }

    @Test
    void values_twoModes() {
        assertEquals(2, WandMode.values().length);
    }

    @Test
    void valueOf_roundTrip() {
        assertEquals(WandMode.EDIT, WandMode.valueOf("EDIT"));
        assertEquals(WandMode.VIEW, WandMode.valueOf("VIEW"));
    }
}
