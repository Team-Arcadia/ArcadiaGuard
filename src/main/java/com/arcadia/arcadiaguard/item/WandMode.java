package com.arcadia.arcadiaguard.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum WandMode {
    EDIT, VIEW;

    public static final Codec<WandMode> CODEC = Codec.BOOL.xmap(
        view -> view ? VIEW : EDIT,
        mode -> mode == VIEW
    );

    public static final StreamCodec<ByteBuf, WandMode> STREAM_CODEC = ByteBufCodecs.BOOL.map(
        view -> view ? VIEW : EDIT,
        mode -> mode == VIEW
    );
}
