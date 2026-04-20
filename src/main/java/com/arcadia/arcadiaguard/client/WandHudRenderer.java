package com.arcadia.arcadiaguard.client;

import com.arcadia.arcadiaguard.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@OnlyIn(Dist.CLIENT)
public final class WandHudRenderer {

    private WandHudRenderer() {}

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.ZONE_EDITOR.get())) return;

        BlockPos p1 = ClientZoneCache.wandPos1();
        BlockPos p2 = ClientZoneCache.wandPos2();
        String s1 = p1 != null ? p1.getX() + " " + p1.getY() + " " + p1.getZ() : "—";
        String s2 = p2 != null ? p2.getX() + " " + p2.getY() + " " + p2.getZ() : "—";
        int viewCount = ClientZoneCache.zones().size();

        MutableComponent line = Component.translatable("arcadiaguard.wand.hud.main", s1, s2)
            .withStyle(ChatFormatting.GOLD);
        if (viewCount > 0) {
            line = line.append(
                Component.translatable("arcadiaguard.wand.hud.view_count", viewCount)
                    .withStyle(ChatFormatting.AQUA)
            );
        }

        String text = line.getString();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        var font = mc.font;
        int tw = font.width(text);
        // Positioned at sh - 80 to stay above XP bar (XP bar ~= sh - 29..sh-49, level ~= sh-60)
        event.getGuiGraphics().drawString(font, line, (sw - tw) / 2, sh - 80, 0xFFFFFFFF, true);
    }
}
