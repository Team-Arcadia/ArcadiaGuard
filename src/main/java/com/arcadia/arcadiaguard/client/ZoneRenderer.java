package com.arcadia.arcadiaguard.client;

import com.arcadia.arcadiaguard.network.ZoneDataPayload.ClientZoneInfo;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;


@OnlyIn(Dist.CLIENT)
public final class ZoneRenderer {

    private ZoneRenderer() {}

    /** RenderType for root zones (thick line, no depth test). */
    private static final RenderType AG_LINES_ROOT = RenderType.create(
        "arcadiaguard_lines_root",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        256,
        false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(4.0)))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false)
    );

    /** RenderType for sub-zones (thinner line, no depth test). */
    private static final RenderType AG_LINES_SUB = RenderType.create(
        "arcadiaguard_lines_sub",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        256,
        false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.5)))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false)
    );

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        List<ClientZoneInfo> zones = ClientZoneCache.zones();
        if (zones.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        var camera = event.getCamera();
        double cx = camera.getPosition().x;
        double cy = camera.getPosition().y;
        double cz = camera.getPosition().z;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Root zones (thick line)
        var consumerRoot = bufferSource.getBuffer(AG_LINES_ROOT);
        for (ClientZoneInfo zone : zones) {
            if (zone.dimensional()) continue;
            if (zone.hasParent()) continue;
            AABB aabb = new AABB(
                zone.minX() - cx,     zone.minY() - cy,     zone.minZ() - cz,
                zone.maxX() + 1 - cx, zone.maxY() + 1 - cy, zone.maxZ() + 1 - cz
            );
            LevelRenderer.renderLineBox(poseStack, consumerRoot, aabb, 1f, 1f, 1f, 0.8f);
        }
        bufferSource.endBatch(AG_LINES_ROOT);

        // Sub-zones (thinner line)
        var consumerSub = bufferSource.getBuffer(AG_LINES_SUB);
        for (ClientZoneInfo zone : zones) {
            if (zone.dimensional()) continue;
            if (!zone.hasParent()) continue;
            AABB aabb = new AABB(
                zone.minX() - cx,     zone.minY() - cy,     zone.minZ() - cz,
                zone.maxX() + 1 - cx, zone.maxY() + 1 - cy, zone.maxZ() + 1 - cz
            );
            LevelRenderer.renderLineBox(poseStack, consumerSub, aabb, 0.7f, 0.3f, 1f, 0.8f);
        }
        bufferSource.endBatch(AG_LINES_SUB);
    }
}
