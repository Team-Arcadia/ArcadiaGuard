package com.arcadia.arcadiaguard.gui;

import com.arcadia.arcadiaguard.gui.widget.CartographiaButton;
import com.arcadia.arcadiaguard.network.gui.DimFlagsPayload;
import com.arcadia.arcadiaguard.network.gui.DimFlagsPayload.FlagInfo;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Écran client : édition des flags au niveau d'une dimension. */
@OnlyIn(Dist.CLIENT)
public final class DimDetailScreen extends Screen {

    private int GUI_W, GUI_H;
    private static final int HDR_H         = 36;
    private static final int FTR_H         = 28;
    private static final int FLAG_H        = 22;
    private static final int PICKER_FLAG_H = 20;

    private final Screen parent;
    private DimFlagsPayload data;

    private int gx, gy;
    private int scroll = 0;
    private String hoveredDesc = "";

    private boolean showPicker    = false;
    private int     pickerScroll  = 0;
    private EditBox pickerSearch;

    private CartographiaButton backBtn;

    public DimDetailScreen(Screen parent, DimFlagsPayload data) {
        super(Component.translatable("arcadiaguard.gui.dim_detail.title", data.dimKey()));
        this.parent = parent;
        this.data   = data;
    }

    public void refresh(DimFlagsPayload payload) {
        this.data = payload;
    }

    @Override
    protected void init() {
        GUI_W = Math.min(480, this.width  - 8);
        GUI_H = Math.min(420, this.height - 8);
        gx = (width  - GUI_W) / 2;
        gy = (height - GUI_H) / 2;

        // pickerSearch position set in init (L6 fix: not repositioned every frame)
        // It will be re-positioned when showPicker toggles via openPicker/closePicker
        int[] pb = pickerBounds();
        int searchY = pb[1] + 24;
        pickerSearch = new com.arcadia.arcadiaguard.gui.widget.CenteredEditBox(font, pb[0] + 8, searchY + 4, pb[2] - 16, 14,
            Component.translatable("arcadiaguard.gui.dim_detail.search"));
        pickerSearch.setVisible(false);
        pickerSearch.setBordered(false);
        pickerSearch.setTextColor(Colors.TEXT);
        pickerSearch.setHint(Component.translatable("arcadiaguard.gui.dimdetail.picker_search.hint")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        addRenderableWidget(pickerSearch);

        int fy = gy + GUI_H - FTR_H;
        backBtn = CartographiaButton.neutral(
            gx + 8, fy + 6, 80, 16,
            Component.translatable("arcadiaguard.gui.common.back"),
            b -> minecraft.setScreen(parent));
        addRenderableWidget(backBtn);
    }

    @Override
    public void renderBlurredBackground(float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xC0000000);
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, Colors.BG_0);
        GuiTextures.drawFrame(g, gx, gy, GUI_W, GUI_H);

        renderHeader(g, mx, my);
        renderFlags(g, mx, my);
        renderFooter(g);

        if (showPicker) renderPicker(g, mx, my);

        super.render(g, mx, my, delta);
    }

    // ── Header ──────────────────────────────────────────────────────────────────

    private void renderHeader(GuiGraphics g, int mx, int my) {
        GuiTextures.icon(g, GuiTextures.ICO_GEAR, gx + 10, gy + 8);
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.dimdetail.header").getString(),
            gx + 28, gy + 8, Colors.ACCENT_HI, false);
        String dimKey = font.plainSubstrByWidth(data.dimKey(), GUI_W - 60);
        g.drawString(font, dimKey, gx + 28, gy + 18, Colors.TEXT_MUTE, false);

        // Bouton "+" en haut à droite
        int addBtnX = gx + GUI_W - 28;
        int addBtnY = gy + 8;
        boolean addHov = !showPicker && mx >= addBtnX && mx < addBtnX + 18
                      && my >= addBtnY && my < addBtnY + 14;
        g.fill(addBtnX, addBtnY, addBtnX + 18, addBtnY + 14,
            addHov ? Colors.accentTint(0x40) : Colors.BG_2);
        g.fill(addBtnX,      addBtnY,      addBtnX + 18, addBtnY + 1,  Colors.ACCENT_LO);
        g.fill(addBtnX,      addBtnY + 13, addBtnX + 18, addBtnY + 14, Colors.ACCENT_LO);
        g.fill(addBtnX,      addBtnY,      addBtnX + 1,  addBtnY + 14, Colors.ACCENT_LO);
        g.fill(addBtnX + 17, addBtnY,      addBtnX + 18, addBtnY + 14, Colors.ACCENT_LO);
        g.drawCenteredString(font, "+", addBtnX + 9, addBtnY + 3,
            addHov ? Colors.ACCENT_HI : Colors.VERDIGRIS);

        GuiTextures.dividerH(g, gx + 8, gy + HDR_H, GUI_W - 16);
    }

    // ── Liste des flags configurés uniquement ───────────────────────────────────

    private static final int DESC_H = 36;

    private void renderFlags(GuiGraphics g, int mx, int my) {
        int cx = gx + 8;
        int cy = gy + HDR_H + 6;
        int cw = GUI_W - 16;

        g.drawString(font,
            Component.translatable("arcadiaguard.gui.dimdetail.configured_title").getString(),
            cx, cy, Colors.ACCENT, false);
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.dimdetail.configured_hint").getString(),
            cx, cy + 11, Colors.TEXT_MUTE, false);
        cy += 24;
        GuiTextures.dividerH(g, cx, cy, cw); cy += 4;

        List<FlagInfo> flags = data.flags().stream().filter(FlagInfo::configured).toList();
        int listH  = GUI_H - HDR_H - FTR_H - 34 - DESC_H - 4;
        int maxVis = listH / FLAG_H;
        scroll = Mth.clamp(scroll, 0, Math.max(0, flags.size() - maxVis));
        int end = Math.min(scroll + maxVis + 1, flags.size());

        hoveredDesc = "";
        if (flags.isEmpty()) {
            g.drawCenteredString(font,
                Component.translatable("arcadiaguard.gui.dimdetail.no_flags").getString(),
                gx + GUI_W / 2, cy + 20, Colors.TEXT_MUTE);
            g.drawCenteredString(font,
                Component.translatable("arcadiaguard.gui.dimdetail.no_flags_hint").getString(),
                gx + GUI_W / 2, cy + 34, Colors.ACCENT_LO);
        }
        for (int i = scroll; i < end; i++) {
            FlagInfo f = flags.get(i);
            int iy   = cy + (i - scroll) * FLAG_H;
            boolean hov = mx >= cx && mx < cx + cw - 4 && my >= iy && my < iy + FLAG_H;
            if (i % 2 == 1) g.fill(cx, iy, cx + cw, iy + FLAG_H, 0x08FFFFFF);
            if (hov) {
                g.fill(cx, iy, cx + cw, iy + FLAG_H, Colors.accentTint(0x10));
                if (!f.description().isEmpty()) hoveredDesc = net.minecraft.network.chat.Component.translatable(f.description()).getString();
            }

            g.fill(cx + 2, iy + FLAG_H / 2 - 2, cx + 5, iy + FLAG_H / 2 + 2, Colors.VERDIGRIS);
            g.drawString(font, f.label(), cx + 8, iy + 7, Colors.TEXT, false);

            int bx = cx + cw - 36;
            if (f.type() == FlagInfo.TYPE_BOOL) {
                boolean protOn = !f.value();
                int badgeColor = protOn ? Colors.GOOD : Colors.DANGER;
                int badgeBg    = badgeColor & 0xFFFFFF | 0x22000000;
                g.fill(bx, iy + 4, bx + 30, iy + FLAG_H - 4, badgeBg);
                g.fill(bx, iy + 4, bx + 30, iy + 5, badgeColor);
                g.drawCenteredString(font, protOn ? "ON" : "OFF", bx + 15, iy + 8, badgeColor);
            } else {
                String preview = f.type() == FlagInfo.TYPE_INT ? f.stringValue()
                    : "[" + (f.stringValue().isEmpty() ? 0 : f.stringValue().split(",").length) + "]";
                g.drawString(font, preview, bx - 2, iy + 8, Colors.TEXT_MUTE, false);
                boolean hovArrow = mx >= bx + 20 && mx < bx + 34 && my >= iy + 4 && my < iy + FLAG_H - 4;
                g.fill(bx + 20, iy + 4, bx + 34, iy + FLAG_H - 4,
                    hovArrow ? Colors.accentTint(0x40) : Colors.BG_2);
                g.drawCenteredString(font, ">", bx + 27, iy + 8,
                    hovArrow ? Colors.ACCENT_HI : Colors.ACCENT);
            }

            int rx = bx - 18;
            boolean rhov = mx >= rx && mx < rx + 14 && my >= iy + 4 && my < iy + FLAG_H - 4;
            g.fill(rx, iy + 4, rx + 14, iy + FLAG_H - 4, rhov
                ? Colors.DANGER & 0xFFFFFF | 0x50000000 : Colors.BG_2);
            g.drawCenteredString(font, "✕", rx + 7, iy + 7,
                rhov ? Colors.DANGER : Colors.TEXT_MUTE);

            GuiTextures.dividerH(g, cx, iy + FLAG_H - 1, cw);
        }

        if (flags.size() > maxVis) {
            int trackX = cx + cw - 3;
            int thumbH = Math.max(12, listH * maxVis / flags.size());
            int thumbY = cy + (listH - thumbH) * scroll / Math.max(1, flags.size() - maxVis);
            g.fill(trackX, cy, trackX + 2, cy + listH, Colors.BG_0);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Colors.ACCENT_LO);
        }

        // Zone description
        int dy = gy + GUI_H - FTR_H - DESC_H - 2;
        GuiTextures.dividerH(g, cx, dy, cw);
        g.fill(cx, dy + 2, cx + cw, dy + DESC_H, Colors.BG_1);
        g.fill(cx, dy + 2, cx + 2, dy + DESC_H, Colors.ACCENT_LO);
        String descText = hoveredDesc.isEmpty()
            ? Component.translatable("arcadiaguard.gui.dimdetail.hover_hint").getString()
            : hoveredDesc;
        int descColor   = hoveredDesc.isEmpty() ? Colors.TEXT_MUTE : Colors.TEXT;
        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
            font.split(net.minecraft.network.chat.Component.literal(descText), cw - 8);
        int ly = dy + 6;
        int maxLines2 = Math.min(lines.size(), 2);
        for (int i = 0; i < maxLines2; i++) {
            g.drawString(font, lines.get(i), cx + 4, ly, descColor, false);
            if (i == maxLines2 - 1 && lines.size() > maxLines2) {
                int lw = font.width(lines.get(i));
                g.drawString(font, "\u2026", cx + 4 + lw, ly, descColor, false);
            }
            ly += font.lineHeight + 2;
        }
    }

    // ── Picker overlay ──────────────────────────────────────────────────────────

    private int[] pickerBounds() {
        int pw = Math.min(340, GUI_W - 20);
        int ph = Math.min(360, GUI_H - 40);
        return new int[]{ gx + (GUI_W - pw) / 2, gy + (GUI_H - ph) / 2, pw, ph };
    }

    private List<FlagInfo> pickerFiltered() {
        String s = pickerSearch == null ? "" : pickerSearch.getValue().toLowerCase().trim();
        if (s.isEmpty()) return data.flags();
        return data.flags().stream()
            .filter(f -> f.label().toLowerCase().contains(s) || f.id().toLowerCase().contains(s))
            .toList();
    }

    private void renderPicker(GuiGraphics g, int mx, int my) {
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, 0x90000000);

        int[] b = pickerBounds();
        int px = b[0], py = b[1], pw = b[2], ph = b[3];

        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Colors.ACCENT_LO);
        g.fill(px, py, px + pw, py + ph, Colors.BG_1);

        g.drawString(font,
            Component.translatable("arcadiaguard.gui.dimdetail.picker_title").getString(),
            px + 8, py + 7, Colors.ACCENT, false);
        int closeBtnX = px + pw - 20;
        boolean closeHov = mx >= closeBtnX && mx < closeBtnX + 16 && my >= py + 4 && my < py + 18;
        if (closeHov) g.fill(closeBtnX, py + 4, closeBtnX + 16, py + 18,
            Colors.DANGER & 0xFFFFFF | 0x40000000);
        g.drawCenteredString(font, "✕", closeBtnX + 8, py + 7,
            closeHov ? Colors.DANGER : Colors.TEXT_MUTE);
        GuiTextures.dividerH(g, px + 4, py + 20, pw - 8);

        int searchY = py + 24;
        g.fill(px + 4, searchY, px + pw - 4, searchY + 18, Colors.BG_0);
        g.fill(px + 4, searchY,      px + pw - 4, searchY + 1,  Colors.ACCENT_LO);
        g.fill(px + 4, searchY + 17, px + pw - 4, searchY + 18, Colors.ACCENT_LO);
        GuiTextures.dividerH(g, px + 4, searchY + 20, pw - 8);

        int listTop = searchY + 22;
        int listH   = ph - (listTop - py) - 16;
        int maxVis  = listH / PICKER_FLAG_H;
        List<FlagInfo> filtered = pickerFiltered();
        pickerScroll = Mth.clamp(pickerScroll, 0, Math.max(0, filtered.size() - maxVis));
        int end = Math.min(pickerScroll + maxVis + 1, filtered.size());

        for (int i = pickerScroll; i < end; i++) {
            FlagInfo f = filtered.get(i);
            int iy = listTop + (i - pickerScroll) * PICKER_FLAG_H;
            boolean hov = mx >= px + 4 && mx < px + pw - 4 && my >= iy && my < iy + PICKER_FLAG_H;
            if (i % 2 == 1) g.fill(px + 4, iy, px + pw - 4, iy + PICKER_FLAG_H, 0x08FFFFFF);
            if (hov)         g.fill(px + 4, iy, px + pw - 4, iy + PICKER_FLAG_H, Colors.accentTint(0x20));

            int labelColor = f.configured() ? Colors.TEXT : Colors.TEXT_MUTE;
            g.drawString(font, f.label(), px + 10, iy + 6, labelColor, false);
            if (f.configured()) {
                g.fill(px + 4, iy + 8, px + 7, iy + 12, Colors.VERDIGRIS);
            }

            String typeTag = f.type() == FlagInfo.TYPE_BOOL ? "bool"
                          : f.type() == FlagInfo.TYPE_INT  ? "int"
                          : "list";
            g.drawString(font, typeTag, px + pw - 8 - font.width(typeTag), iy + 7, Colors.TEXT_MUTE, false);
            GuiTextures.dividerH(g, px + 4, iy + PICKER_FLAG_H - 1, pw - 8);
        }

        if (filtered.isEmpty()) {
            g.drawCenteredString(font,
                Component.translatable("arcadiaguard.gui.dimdetail.picker_empty").getString(),
                px + pw / 2, listTop + 16, Colors.TEXT_MUTE);
        }

        if (filtered.size() > maxVis) {
            int trackX = px + pw - 7;
            int thumbH = Math.max(12, listH * maxVis / filtered.size());
            int thumbY = listTop + (listH - thumbH) * pickerScroll
                / Math.max(1, filtered.size() - maxVis);
            g.fill(trackX, listTop, trackX + 3, listTop + listH, Colors.BG_0);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, Colors.ACCENT_LO);
        }

        g.drawCenteredString(font,
            Component.translatable("arcadiaguard.gui.dimdetail.picker_legend").getString(),
            px + pw / 2, py + ph - 10, Colors.TEXT_MUTE);
    }

    // ── Footer ───────────────────────────────────────────────────────────────────

    private void renderFooter(GuiGraphics g) {
        int fy = gy + GUI_H - FTR_H;
        GuiTextures.dividerH(g, gx + 8, fy, GUI_W - 16);
        g.drawCenteredString(font,
            Component.translatable("arcadiaguard.gui.dimdetail.footer_legend").getString(),
            gx + GUI_W / 2, fy + 10, Colors.TEXT_MUTE);
    }

    // ── Interactions ─────────────────────────────────────────────────────────────

    private void openPicker() {
        showPicker   = true;
        pickerScroll = 0;
        if (pickerSearch != null) {
            // L6: reposition only when toggling picker open
            int[] b = pickerBounds();
            int searchY = b[1] + 24;
            pickerSearch.setX(b[0] + 8);
            pickerSearch.setY(searchY + 3);
            pickerSearch.setWidth(b[2] - 16);
            pickerSearch.setValue("");
            pickerSearch.setVisible(true);
            setFocused(pickerSearch);
        }
        backBtn.active = false;
    }

    private void closePicker() {
        showPicker = false;
        if (pickerSearch != null) {
            pickerSearch.setVisible(false);
            pickerSearch.setValue("");
        }
        backBtn.active = true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;

        // Picker ouvert : absorbe les clics
        if (showPicker) {
            int[] b = pickerBounds();
            int px = b[0], py = b[1], pw = b[2], ph = b[3];

            int closeBtnX = px + pw - 20;
            if (imx >= closeBtnX && imx < closeBtnX + 16 && imy >= py + 4 && imy < py + 18) {
                closePicker(); return true;
            }

            int searchY = py + 24;
            int listTop = searchY + 22;
            int listH   = ph - (listTop - py) - 16;
            List<FlagInfo> filtered = pickerFiltered();
            if (imx >= px + 4 && imx < px + pw - 4 && imy >= listTop && imy < listTop + listH) {
                int idx = (imy - listTop) / PICKER_FLAG_H + pickerScroll;
                if (idx >= 0 && idx < filtered.size()) {
                    FlagInfo f = filtered.get(idx);
                    if (f.type() == FlagInfo.TYPE_BOOL) {
                        boolean newVal = f.configured() ? !f.value() : false;
                        PacketDistributor.sendToServer(
                            GuiActionPayload.setDimFlag(data.dimKey(), f.id(), newVal));
                    } else {
                        closePicker();
                        FlagConfigScreen.FlagType t = f.type() == FlagInfo.TYPE_INT
                            ? FlagConfigScreen.FlagType.INT : FlagConfigScreen.FlagType.LIST;
                        minecraft.setScreen(new FlagConfigScreen(
                            this, t, FlagConfigScreen.Target.DIM,
                            data.dimKey(), f.id(), f.label(), f.description(), f.stringValue()));
                    }
                    return true;
                }
            }

            if (imx < px || imx >= px + pw || imy < py || imy >= py + ph) {
                closePicker(); return true;
            }
            if (pickerSearch != null && pickerSearch.isVisible()) pickerSearch.mouseClicked(mx, my, btn);
            return true;
        }

        // Bouton "+" header
        int addBtnX = gx + GUI_W - 28;
        int addBtnY = gy + 8;
        if (imx >= addBtnX && imx < addBtnX + 18 && imy >= addBtnY && imy < addBtnY + 14) {
            openPicker(); return true;
        }

        // Clics sur les flags configurés
        int cx = gx + 8;
        int cy = gy + HDR_H + 34;
        int cw = GUI_W - 16;
        int listH  = GUI_H - HDR_H - FTR_H - 34 - DESC_H - 4;
        List<FlagInfo> flags = data.flags().stream().filter(FlagInfo::configured).toList();

        if (imx >= cx && imx < cx + cw && imy >= cy && imy < cy + listH) {
            int idx = (imy - cy) / FLAG_H + scroll;
            if (idx >= 0 && idx < flags.size()) {
                FlagInfo f = flags.get(idx);
                int iy  = cy + (idx - scroll) * FLAG_H;
                int bx  = cx + cw - 36;
                int rx  = bx - 18;

                if (imx >= rx && imx < rx + 14 && imy >= iy + 4 && imy < iy + FLAG_H - 4) {
                    PacketDistributor.sendToServer(
                        GuiActionPayload.resetDimFlag(data.dimKey(), f.id()));
                    return true;
                }

                if (f.type() == FlagInfo.TYPE_BOOL) {
                    if (imx >= bx && imx < bx + 30) {
                        PacketDistributor.sendToServer(
                            GuiActionPayload.setDimFlag(data.dimKey(), f.id(), !f.value()));
                        return true;
                    }
                } else {
                    if (imx >= bx + 20 && imx < bx + 34) {
                        FlagConfigScreen.FlagType t = f.type() == FlagInfo.TYPE_INT
                            ? FlagConfigScreen.FlagType.INT : FlagConfigScreen.FlagType.LIST;
                        minecraft.setScreen(new FlagConfigScreen(
                            this, t, FlagConfigScreen.Target.DIM,
                            data.dimKey(), f.id(), f.label(), f.description(), f.stringValue()));
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (showPicker) {
            int[] b = pickerBounds();
            int searchY = b[1] + 24;
            int listTop = searchY + 22;
            int listH   = b[3] - (listTop - b[1]) - 16;
            int maxVis  = listH / PICKER_FLAG_H;
            pickerScroll = Mth.clamp((int)(pickerScroll - dy),
                0, Math.max(0, pickerFiltered().size() - maxVis));
            return true;
        }
        int listH  = GUI_H - HDR_H - FTR_H - 34 - DESC_H - 4;
        int maxVis = listH / FLAG_H;
        int configuredCount = (int) data.flags().stream().filter(FlagInfo::configured).count();
        scroll = Mth.clamp((int)(scroll - dy), 0, Math.max(0, configuredCount - maxVis));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            if (getFocused() instanceof EditBox eb && !showPicker) {
                eb.setFocused(false);
                setFocused(null);
                return true;
            }
            if (showPicker) {
                closePicker();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
