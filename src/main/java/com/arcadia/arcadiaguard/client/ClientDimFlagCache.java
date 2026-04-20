package com.arcadia.arcadiaguard.client;

import com.arcadia.arcadiaguard.network.gui.DimFlagsPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientDimFlagCache {

    private static volatile DimFlagsPayload cached;

    private ClientDimFlagCache() {}

    public static void update(DimFlagsPayload payload) { cached = payload; }

    public static DimFlagsPayload get() { return cached; }
}
