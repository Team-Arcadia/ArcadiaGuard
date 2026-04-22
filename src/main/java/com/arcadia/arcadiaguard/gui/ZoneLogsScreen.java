package com.arcadia.arcadiaguard.gui;

import com.arcadia.arcadiaguard.gui.widget.CartographiaButton;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneLogsPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneLogsPayload.LogLine;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Liste filtrable des logs d'une zone. */
@OnlyIn(Dist.CLIENT)
public final class ZoneLogsScreen extends Screen {

    private int GUI_W, GUI_H;
    private static final int HDR_H = 36;
    private static final int FTR_H = 24;
    private static final int ROW_H = 18;

    private final Screen parent;
    private ZoneLogsPayload data;

    private int gx, gy;
    private int scroll = 0;
    private EditBox playerFilter;
    private EditBox actionFilter;

    private CartographiaButton filterBtn;
    private CartographiaButton resetBtn;
    private CartographiaButton refreshBtn;
    private CartographiaButton backBtn;

    public ZoneLogsScreen(Screen parent, ZoneLogsPayload data) {
        super(Component.translatable("arcadiaguard.gui.zone_logs.title", data.zoneName()));
        this.parent = parent;
        this.data = data;
    }

    public void refresh(ZoneLogsPayload payload) {
        this.data = payload;
        this.scroll = 0;
    }

    @Override
    protected void init() {
        GUI_W = Math.min(560, this.width  - 8);
        GUI_H = Math.min(440, this.height - 8);
        gx = (width - GUI_W) / 2;
        gy = (height - GUI_H) / 2;

        int filterY = gy + HDR_H + 6;

        // Buttons right-aligned; EditBoxes fill available space to the left
        int editW = Math.max(20, Math.min(140, (GUI_W - 250) / 2));
        playerFilter = new com.arcadia.arcadiaguard.gui.widget.CenteredEditBox(font, gx + 8, filterY, editW, 14, Component.translatable("arcadiaguard.gui.zone_logs.player_filter"));
        playerFilter.setMaxLength(40);
        playerFilter.setBordered(false);
        playerFilter.setTextColor(Colors.TEXT);
        playerFilter.setHint(Component.translatable("arcadiaguard.gui.zonelogs.player_filter.hint")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        addRenderableWidget(playerFilter);

        actionFilter = new com.arcadia.arcadiaguard.gui.widget.CenteredEditBox(font, gx + 8 + editW + 12, filterY, editW, 14, Component.translatable("arcadiaguard.gui.zone_logs.action_filter"));
        actionFilter.setMaxLength(40);
        actionFilter.setBordered(false);
        actionFilter.setTextColor(Colors.TEXT);
        actionFilter.setHint(Component.translatable("arcadiaguard.gui.zonelogs.action_filter.hint")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        addRenderableWidget(actionFilter);

        filterBtn = CartographiaButton.neutral(
            gx + GUI_W - 222, filterY - 2, 64, 18,
            Component.translatable("arcadiaguard.gui.zonelogs.filter"),
            b -> applyFilters());
        addRenderableWidget(filterBtn);

        resetBtn = CartographiaButton.danger(
            gx + GUI_W - 150, filterY - 2, 64, 18,
            Component.translatable("arcadiaguard.gui.zonelogs.reset"),
            b -> { playerFilter.setValue(""); actionFilter.setValue(""); applyFilters(); });
        addRenderableWidget(resetBtn);

        refreshBtn = CartographiaButton.neutral(
            gx + GUI_W - 78, filterY - 2, 70, 18,
            Component.translatable("arcadiaguard.gui.zonelogs.refresh"),
            b -> applyFilters());
        addRenderableWidget(refreshBtn);

        int fy = gy + GUI_H - FTR_H;
        backBtn = CartographiaButton.neutral(
            gx + 8, fy + 4, 80, 16,
            Component.translatable("arcadiaguard.gui.common.back"),
            b -> minecraft.setScreen(parent));
        addRenderableWidget(backBtn);
    }

    private void applyFilters() {
        PacketDistributor.sendToServer(GuiActionPayload.requestZoneLogs(
            data.zoneName(),
            playerFilter.getValue().trim(),
            actionFilter.getValue().trim()));
    }

    @Override
    public void renderBlurredBackground(float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xC0000000);
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, Colors.BG_0);
        GuiTextures.drawFrame(g, gx, gy, GUI_W, GUI_H);

        renderHeader(g);
        renderFilterBackgrounds(g);
        renderTable(g, mx, my);
        renderFooterDivider(g);

        super.render(g, mx, my, delta);
    }

    private void renderHeader(GuiGraphics g) {
        GuiTextures.icon(g, GuiTextures.ICO_CLOCK, gx + 10, gy + 10);
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.zonelogs.header", data.zoneName()).getString(),
            gx + 26, gy + 8, Colors.ACCENT_HI, false);
        int entryCount = data.entries().size();
        String entryCountKey = "arcadiaguard.gui.zonelogs.entry_count." + (entryCount == 1 ? "one" : "other");
        g.drawString(font, Component.translatable(entryCountKey, entryCount).getString(),
            gx + 26, gy + 18, Colors.TEXT_MUTE, false);
        GuiTextures.dividerH(g, gx + 8, gy + HDR_H, GUI_W - 16);
    }

    private void renderFilterBackgrounds(GuiGraphics g) {
        int filterY = gy + HDR_H + 6;
        int pX = playerFilter.getX(), pW = playerFilter.getWidth();
        int aX = actionFilter.getX(), aW = actionFilter.getWidth();
        g.fill(pX, filterY - 2, pX + pW, filterY + 16, Colors.BG_1);
        g.fill(pX, filterY - 2, pX + pW, filterY - 1, Colors.ACCENT_LO);
        g.fill(pX, filterY + 15, pX + pW, filterY + 16, Colors.ACCENT_LO);
        g.fill(aX, filterY - 2, aX + aW, filterY + 16, Colors.BG_1);
        g.fill(aX, filterY - 2, aX + aW, filterY - 1, Colors.ACCENT_LO);
        g.fill(aX, filterY + 15, aX + aW, filterY + 16, Colors.ACCENT_LO);
    }

    private void renderTable(GuiGraphics g, int mx, int my) {
        int tx = gx + 8;
        int ty = gy + HDR_H + 28;
        int tw = GUI_W - 16;
        int th = GUI_H - HDR_H - FTR_H - 34;

        // En-tête de tableau
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonelogs.col_timestamp").getString(), tx + 4,   ty, Colors.TEXT_MUTE, false);
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonelogs.col_player").getString(),    tx + 124, ty, Colors.TEXT_MUTE, false);
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonelogs.col_action").getString(),    tx + 224, ty, Colors.TEXT_MUTE, false);
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonelogs.col_position").getString(),  tx + 384, ty, Colors.TEXT_MUTE, false);
        GuiTextures.dividerH(g, tx, ty + 10, tw);

        int listTop = ty + 14;
        int listH = th - 14;
        int maxVis = listH / ROW_H;
        List<LogLine> lines = data.entries();
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - maxVis));
        int end = Math.min(scroll + maxVis + 1, lines.size());

        if (lines.isEmpty()) {
            g.drawCenteredString(font,
                Component.translatable("arcadiaguard.gui.zonelogs.empty").getString(),
                gx + GUI_W / 2, listTop + listH / 2 - 4, Colors.TEXT_MUTE);
            return;
        }

        for (int i = scroll; i < end; i++) {
            LogLine l = lines.get(i);
            int iy = listTop + (i - scroll) * ROW_H;
            if (i % 2 == 1) g.fill(tx, iy, tx + tw, iy + ROW_H, 0x08FFFFFF);
            drawTrunc(g, l.timestamp(), tx + 4,   iy + 5, 116, Colors.TEXT);
            drawTrunc(g, l.player(),    tx + 124, iy + 5, 96,  Colors.TEXT);
            drawTrunc(g, l.action(),    tx + 224, iy + 5, 156, Colors.ACCENT);
            drawTrunc(g, l.pos(),       tx + 384, iy + 5, 160, Colors.TEXT_MUTE);
        }

        // Scrollbar
        if (lines.size() > maxVis) {
            int trackX = tx + tw - 3;
            int thumbH = Math.max(12, listH * maxVis / lines.size());
            int thumbY = listTop + (listH - thumbH) * scroll / Math.max(1, lines.size() - maxVis);
            g.fill(trackX, listTop, trackX + 2, listTop + listH, Colors.BG_0);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Colors.ACCENT_LO);
        }
    }

    private void drawTrunc(GuiGraphics g, String s, int x, int y, int maxW, int color) {
        String t = font.plainSubstrByWidth(s, maxW);
        g.drawString(font, t, x, y, color, false);
    }

    private void renderFooterDivider(GuiGraphics g) {
        int fy = gy + GUI_H - FTR_H;
        GuiTextures.dividerH(g, gx + 8, fy, GUI_W - 16);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && getFocused() instanceof EditBox eb) {
            eb.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int listH = GUI_H - HDR_H - FTR_H - 48;
        int maxVis = listH / ROW_H;
        scroll = Mth.clamp((int)(scroll - dy), 0, Math.max(0, data.entries().size() - maxVis));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
