package com.arcadia.arcadiaguard.gui;

import com.arcadia.arcadiaguard.client.ClientZoneCache;
import com.arcadia.arcadiaguard.gui.widget.CartographiaButton;
import com.arcadia.arcadiaguard.network.ZoneDataPayload.ClientZoneInfo;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload.Detail;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload.FlagEntry;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload.MemberEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class ZoneDetailScreen extends Screen {

    // ── Inner types ──────────────────────────────────────────────────────────────

    private sealed interface PopupState
        permits PopupState.None, PopupState.ConfirmDelete,
                PopupState.WhitelistInput, PopupState.ParentInput,
                PopupState.FlagPicker, PopupState.CoordsEditor,
                PopupState.ItemBlocksPicker {
        record None()          implements PopupState {}
        record ConfirmDelete() implements PopupState {}
        record WhitelistInput() implements PopupState {}
        record ParentInput()   implements PopupState {}
        record FlagPicker()    implements PopupState {}
        record CoordsEditor()  implements PopupState {}
        record ItemBlocksPicker() implements PopupState {}
    }

    private record Hitbox(int x, int y, int w, int h, Runnable action) {
        boolean hit(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    // ── Constants ────────────────────────────────────────────────────────────────

    private int GUI_W, GUI_H, COL1_W, COL2_W;
    private static final int HEADER_H      = 36;
    private static final int FOOTER_H      = 28;
    private static final int FLAG_H        = 20;
    private static final int MBR_H         = 22;
    private static final int PICKER_FLAG_H = 20;

    // ── Instance fields ──────────────────────────────────────────────────────────

    private final Screen parent;
    private Detail detail;

    private int gx, gy;
    private int flagScroll   = 0;
    private int memberScroll = 0;
    private String hoveredFlagDesc = "";

    private PopupState popup = new PopupState.None();
    private final List<Hitbox> hitboxes = new ArrayList<>();

    private int flagPickerScroll = 0;
    private boolean viewZone     = false;

    // S-H20 Item blocks picker state
    private int itemPickerScroll = 0;
    private EditBox itemSearchBox;

    private int btX1, btX2, btX3, btX4, btX5;
    private int btW1, btW2, btW3, btW4, btW5;

    private EditBox whitelistBox;
    private EditBox parentBox;
    private EditBox flagSearchBox;
    private EditBox[] coordBoxes;

    // Footer buttons (recreated each init)
    private CartographiaButton footerBackBtn;
    private CartographiaButton footerPlayerBtn;
    private CartographiaButton footerParentBtn;
    private CartographiaButton footerItemBlocksBtn;
    private CartographiaButton footerDeleteBtn;

    // ── Constructor ──────────────────────────────────────────────────────────────

    public ZoneDetailScreen(Screen parent, Detail detail) {
        super(Component.translatable("arcadiaguard.gui.zone_detail.title", detail.name()));
        this.parent = parent;
        this.detail = detail;
    }

    public void updateDetail(Detail d) {
        this.detail = d;
    }

    // ── Init ─────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        GUI_W = Math.min(860, width  - 4);
        GUI_H = Math.min(520, height - 4);
        float scale = Math.min(GUI_W / 860f, GUI_H / 520f);
        COL1_W = Math.max(80, Math.round(240 * scale));
        COL2_W = Math.max(80, Math.round(320 * scale));
        gx = (width  - GUI_W) / 2;
        gy = (height - GUI_H) / 2;

        int col3X = gx + COL1_W + COL2_W + 4;
        int col3W  = GUI_W - COL1_W - COL2_W - 8;

        whitelistBox = new EditBox(font, col3X + 4, gy + HEADER_H + 28, col3W - 10, 14,
            Component.translatable("arcadiaguard.gui.zone_detail.player_hint"));
        whitelistBox.setMaxLength(40);
        whitelistBox.setBordered(false);
        whitelistBox.setTextColor(Colors.TEXT);
        whitelistBox.setHint(Component.translatable("arcadiaguard.gui.zonedetail.whitelist_box.hint")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        whitelistBox.setVisible(false);
        addRenderableWidget(whitelistBox);

        parentBox = new EditBox(font, col3X + 4, gy + HEADER_H + 28, col3W - 10, 14,
            Component.translatable("arcadiaguard.gui.zone_detail.parent_hint"));
        parentBox.setMaxLength(60);
        parentBox.setBordered(false);
        parentBox.setTextColor(Colors.TEXT);
        parentBox.setHint(Component.translatable("arcadiaguard.gui.zonedetail.parent_box.hint")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        parentBox.setVisible(false);
        addRenderableWidget(parentBox);

        flagSearchBox = new EditBox(font, 0, 0, 100, 14,
            Component.translatable("arcadiaguard.gui.zone_detail.flag_search_hint"));
        flagSearchBox.setMaxLength(40);
        flagSearchBox.setBordered(false);
        flagSearchBox.setTextColor(Colors.TEXT);
        flagSearchBox.setHint(Component.translatable("arcadiaguard.gui.zonedetail.flag_search_box.hint")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        flagSearchBox.setVisible(false);
        addRenderableWidget(flagSearchBox);

        // S-H20 : search box pour l'ItemBlocksPicker
        itemSearchBox = new EditBox(font, 0, 0, 100, 14,
            Component.translatable("arcadiaguard.gui.zonedetail.itemblocks.search_hint"));
        itemSearchBox.setMaxLength(80);
        itemSearchBox.setBordered(false);
        itemSearchBox.setTextColor(Colors.TEXT);
        itemSearchBox.setHint(Component.translatable("arcadiaguard.gui.zonedetail.itemblocks.search_hint")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        itemSearchBox.setResponder(s -> itemPickerScroll = 0);
        itemSearchBox.setVisible(false);
        addRenderableWidget(itemSearchBox);

        String[] coordHintKeys = {
            "arcadiaguard.gui.zonedetail.coord_minx.hint",
            "arcadiaguard.gui.zonedetail.coord_miny.hint",
            "arcadiaguard.gui.zonedetail.coord_minz.hint",
            "arcadiaguard.gui.zonedetail.coord_maxx.hint",
            "arcadiaguard.gui.zonedetail.coord_maxy.hint",
            "arcadiaguard.gui.zonedetail.coord_maxz.hint"
        };
        coordBoxes = new EditBox[6];
        for (int i = 0; i < 6; i++) {
            coordBoxes[i] = new EditBox(font, 0, 0, 60, 14, Component.translatable(coordHintKeys[i]));
            coordBoxes[i].setMaxLength(12);
            coordBoxes[i].setBordered(false);
            coordBoxes[i].setTextColor(Colors.TEXT);
            coordBoxes[i].setHint(Component.translatable(coordHintKeys[i])
                .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
            coordBoxes[i].setFilter(s -> s.matches("-?\\d{0,8}") || s.equals("-"));
            coordBoxes[i].setVisible(false);
            addRenderableWidget(coordBoxes[i]);
        }

        viewZone = ClientZoneCache.zones().stream().anyMatch(z -> z.name().equals(detail.name()));

        btW1 = 60; btW2 = 80; btW3 = 90; btW4 = 80; btW5 = 90;
        int totalBtns = btW1 + btW2 + btW3 + btW4 + btW5;
        int usable = GUI_W - 16;
        if (usable < totalBtns + 16) {
            int avail = Math.max(usable - 16, 80);
            btW1 = avail * 60 / totalBtns;
            btW2 = avail * 80 / totalBtns;
            btW3 = avail * 90 / totalBtns;
            btW4 = avail * 80 / totalBtns;
            btW5 = avail - btW1 - btW2 - btW3 - btW4;
        }
        int gap = Math.max(4, (usable - btW1 - btW2 - btW3 - btW4 - btW5) / 4);
        btX1 = gx + 8;
        btX2 = btX1 + btW1 + gap;
        btX3 = btX2 + btW2 + gap;
        btX4 = btX3 + btW3 + gap;
        btX5 = btX4 + btW4 + gap;

        int bfy = gy + GUI_H - FOOTER_H + 6;
        boolean isWhitelist = popup instanceof PopupState.WhitelistInput;
        boolean isParent    = popup instanceof PopupState.ParentInput;

        footerBackBtn = CartographiaButton.neutral(btX1, bfy, btW1, 16,
            Component.translatable("arcadiaguard.gui.common.back"),
            b -> minecraft.setScreen(parent));
        addRenderableWidget(footerBackBtn);

        footerPlayerBtn = CartographiaButton.good(btX2, bfy, btW2, 16,
            isWhitelist
                ? Component.translatable("arcadiaguard.gui.zonedetail.confirm_player")
                : Component.translatable("arcadiaguard.gui.zonedetail.add_player"),
            b -> {
                if (popup instanceof PopupState.WhitelistInput) {
                    String name = whitelistBox.getValue().trim();
                    if (!name.isEmpty())
                        PacketDistributor.sendToServer(GuiActionPayload.whitelistAdd(detail.name(), name));
                    whitelistBox.setValue("");
                    popup = new PopupState.None();
                } else {
                    popup = new PopupState.WhitelistInput();
                    setFocused(whitelistBox);
                }
            });
        addRenderableWidget(footerPlayerBtn);

        footerParentBtn = CartographiaButton.neutral(btX3, bfy, btW3, 16,
            isParent
                ? Component.translatable("arcadiaguard.gui.zonedetail.confirm_parent")
                : Component.translatable("arcadiaguard.gui.zonedetail.set_parent"),
            b -> {
                if (popup instanceof PopupState.ParentInput) {
                    String parentName = parentBox.getValue().trim();
                    PacketDistributor.sendToServer(GuiActionPayload.setParent(detail.name(), parentName));
                    parentBox.setValue("");
                    popup = new PopupState.None();
                } else {
                    popup = new PopupState.ParentInput();
                    parentBox.setValue(detail.parentName() != null ? detail.parentName() : "");
                    setFocused(parentBox);
                }
            });
        addRenderableWidget(footerParentBtn);

        footerItemBlocksBtn = CartographiaButton.neutral(btX4, bfy, btW4, 16,
            Component.translatable("arcadiaguard.gui.zonedetail.itemblocks"),
            b -> {
                if (popup instanceof PopupState.ItemBlocksPicker) {
                    popup = new PopupState.None();
                    itemSearchBox.setVisible(false);
                } else {
                    popup = new PopupState.ItemBlocksPicker();
                    itemSearchBox.setValue("");
                    itemSearchBox.setVisible(true);
                    setFocused(itemSearchBox);
                    itemPickerScroll = 0;
                }
            });
        addRenderableWidget(footerItemBlocksBtn);

        footerDeleteBtn = CartographiaButton.danger(btX5, bfy, btW5, 16,
            Component.translatable("arcadiaguard.gui.zonedetail.delete"),
            b -> popup = new PopupState.ConfirmDelete());
        addRenderableWidget(footerDeleteBtn);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void hit(int x, int y, int w, int h, Runnable action) {
        hitboxes.add(new Hitbox(x, y, w, h, action));
    }

    // ── Render ───────────────────────────────────────────────────────────────────

    @Override
    public void renderBlurredBackground(float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hitboxes.clear();

        g.fill(0, 0, width, height, 0xC0000000);
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, Colors.BG_0);
        GuiTextures.drawFrame(g, gx, gy, GUI_W, GUI_H);

        renderHeader(g, mx, my);
        renderCol1(g, mx, my);
        if (popup instanceof PopupState.None || popup instanceof PopupState.WhitelistInput
                || popup instanceof PopupState.ParentInput) {
            renderCol2(g, mx, my);
            renderCol3(g, mx, my);
            renderFlagDesc(g);
        }
        renderFooter(g, mx, my);

        if (!(popup instanceof PopupState.CoordsEditor)) for (EditBox b : coordBoxes) b.setVisible(false);

        // H-U5: draw focus ring for non-bordered EditBoxes
        drawFocusRingIfFocused(g, whitelistBox);
        drawFocusRingIfFocused(g, parentBox);
        drawFocusRingIfFocused(g, flagSearchBox);
        drawFocusRingIfFocused(g, itemSearchBox);
        if (coordBoxes != null) for (EditBox cb : coordBoxes) drawFocusRingIfFocused(g, cb);

        super.render(g, mx, my, delta);

        if (popup instanceof PopupState.FlagPicker)    renderFlagPicker(g, mx, my);
        if (popup instanceof PopupState.ItemBlocksPicker) renderItemBlocksPicker(g, mx, my);
        if (popup instanceof PopupState.ConfirmDelete) renderConfirmPopup(g, mx, my);
        if (popup instanceof PopupState.CoordsEditor)  renderCoordsEditor(g, mx, my);
    }

    // ── Éditeur de coordonnées (popup) ────────────────────────────────────────────

    private void renderCoordsEditor(GuiGraphics g, int mx, int my) {
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, 0x90000000);

        int pw = Math.min(300, GUI_W - 20);
        int ph = 180;
        int px = gx + (GUI_W - pw) / 2;
        int py = gy + (GUI_H - ph) / 2;

        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Colors.ACCENT_LO);
        g.fill(px, py, px + pw, py + ph, Colors.BG_1);

        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.coords_editor.title").getString(), px + 8, py + 8, Colors.ACCENT, false);
        int closeBtnX = px + pw - 20;
        boolean closeHov = mx >= closeBtnX && mx < closeBtnX + 16 && my >= py + 4 && my < py + 18;
        if (closeHov) g.fill(closeBtnX, py + 4, closeBtnX + 16, py + 18,
            Colors.DANGER & 0xFFFFFF | 0x40000000);
        g.drawCenteredString(font, "✕", closeBtnX + 8, py + 7,
            closeHov ? Colors.DANGER : Colors.TEXT_MUTE);
        GuiTextures.dividerH(g, px + 4, py + 22, pw - 8);

        int rowHeaderH = 14;
        int rowY1 = py + 32;
        int rowY2 = py + 82;
        int cellW = (pw - 24) / 3;
        int cellH = 16;

        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.corner_a").getString(), px + 8, rowY1, Colors.ACCENT_HI, false);
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.corner_b").getString(), px + 8, rowY2, Colors.ACCENT_HI, false);

        String[] labels = {"X", "Y", "Z"};
        for (int i = 0; i < 6; i++) {
            int col = i % 3;
            int row = i / 3;
            int fx = px + 8 + col * cellW + 4;
            int fy = (row == 0 ? rowY1 : rowY2) + rowHeaderH;
            g.drawString(font, labels[col], fx, fy - 10, Colors.TEXT_MUTE, false);
            g.fill(fx, fy, fx + cellW - 8, fy + cellH, Colors.BG_0);
            g.fill(fx, fy, fx + cellW - 8, fy + 1, Colors.ACCENT_LO);
            g.fill(fx, fy + cellH - 1, fx + cellW - 8, fy + cellH, Colors.ACCENT_LO);
            coordBoxes[i].setX(fx + 3);
            coordBoxes[i].setY(fy + 3);
            coordBoxes[i].setWidth(cellW - 14);
            coordBoxes[i].setVisible(true);
        }

        int btnY = py + ph - 24;
        int applyX = px + pw - 100;
        int cancelX = px + 8;
        boolean applyHov  = mx >= applyX  && mx < applyX + 90  && my >= btnY && my < btnY + 16;
        boolean cancelHov = mx >= cancelX && mx < cancelX + 70 && my >= btnY && my < btnY + 16;

        g.fill(cancelX, btnY, cancelX + 70, btnY + 16, cancelHov ? Colors.accentTint(0x30) : Colors.BG_2);
        g.fill(cancelX,      btnY,      cancelX + 70, btnY + 1,  Colors.ACCENT_LO);
        g.fill(cancelX,      btnY + 15, cancelX + 70, btnY + 16, Colors.ACCENT_LO);
        g.fill(cancelX,      btnY,      cancelX + 1,  btnY + 16, Colors.ACCENT_LO);
        g.fill(cancelX + 69, btnY,      cancelX + 70, btnY + 16, Colors.ACCENT_LO);
        g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.common.cancel").getString(), cancelX + 35, btnY + 4,
            cancelHov ? Colors.TEXT : Colors.TEXT_MUTE);

        g.fill(applyX, btnY, applyX + 90, btnY + 16, applyHov ? Colors.accentTint(0x40) : Colors.BG_2);
        g.fill(applyX,      btnY,      applyX + 90, btnY + 1,  Colors.GOOD);
        g.fill(applyX,      btnY + 15, applyX + 90, btnY + 16, Colors.GOOD);
        g.fill(applyX,      btnY,      applyX + 1,  btnY + 16, Colors.GOOD);
        g.fill(applyX + 89, btnY,      applyX + 90, btnY + 16, Colors.GOOD);
        g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.apply").getString(), applyX + 45, btnY + 4,
            applyHov ? Colors.ACCENT_HI : Colors.GOOD);
    }

    private int[] coordsEditorBounds() {
        int pw = Math.min(300, GUI_W - 20);
        int ph = 180;
        return new int[]{ gx + (GUI_W - pw) / 2, gy + (GUI_H - ph) / 2, pw, ph };
    }

    private void openCoordsEditor() {
        popup = new PopupState.CoordsEditor();
        coordBoxes[0].setValue(String.valueOf(detail.minX()));
        coordBoxes[1].setValue(String.valueOf(detail.minY()));
        coordBoxes[2].setValue(String.valueOf(detail.minZ()));
        coordBoxes[3].setValue(String.valueOf(detail.maxX()));
        coordBoxes[4].setValue(String.valueOf(detail.maxY()));
        coordBoxes[5].setValue(String.valueOf(detail.maxZ()));
        setFocused(coordBoxes[0]);
    }

    private void closeCoordsEditor() {
        popup = new PopupState.None();
        for (EditBox b : coordBoxes) b.setVisible(false);
    }

    private void applyCoordsEditor() {
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            try { v[i] = Integer.parseInt(coordBoxes[i].getValue().trim()); }
            catch (NumberFormatException e) { return; }
        }
        PacketDistributor.sendToServer(GuiActionPayload.setZoneBounds(
            detail.name(), v[0], v[1], v[2], v[3], v[4], v[5]));
        closeCoordsEditor();
    }

    // ── Header ──────────────────────────────────────────────────────────────────

    private void renderHeader(GuiGraphics g, int mx, int my) {
        GuiTextures.icon(g, GuiTextures.ICO_SHIELD, gx + 10, gy + 10);
        int eyeBtnW = 80;
        int eyeBtnX = gx + GUI_W - eyeBtnW - 8;
        int enabledBtnW = 84;
        int enabledBtnX = eyeBtnX - enabledBtnW - 4;
        int logsBtnW = 70;
        int logsBtnX = enabledBtnX - logsBtnW - 4;
        int nameMaxW = logsBtnX - (gx + 30) - 8;
        String nameLabel = font.plainSubstrByWidth(detail.name(), nameMaxW);
        if (nameLabel.length() < detail.name().length()) nameLabel += "…";
        g.drawString(font, nameLabel, gx + 30, gy + 8, Colors.ACCENT_HI, false);
        String sub = detail.dim()
            + (detail.parentName() != null ? Component.translatable("arcadiaguard.gui.zonedetail.subzone_of", detail.parentName()).getString() : "");
        String subLabel = font.plainSubstrByWidth(sub, nameMaxW);
        g.drawString(font, subLabel, gx + 30, gy + 18, Colors.TEXT_MUTE, false);

        boolean isEnabled = detail.enabled();
        int enColor = isEnabled ? Colors.GOOD : Colors.DANGER;
        int enBg    = isEnabled ? (Colors.GOOD & 0xFFFFFF | 0x18000000) : (Colors.DANGER & 0xFFFFFF | 0x18000000);
        g.fill(enabledBtnX, gy + 6, enabledBtnX + enabledBtnW, gy + 26, enBg);
        g.fill(enabledBtnX, gy + 6, enabledBtnX + enabledBtnW, gy + 7, enColor);
        g.fill(enabledBtnX, gy + 25, enabledBtnX + enabledBtnW, gy + 26, enColor);
        g.fill(enabledBtnX, gy + 6, enabledBtnX + 1, gy + 26, enColor);
        g.fill(enabledBtnX + enabledBtnW - 1, gy + 6, enabledBtnX + enabledBtnW, gy + 26, enColor);
        g.drawCenteredString(font, isEnabled ? Component.translatable("arcadiaguard.gui.zonedetail.zone_on").getString() : Component.translatable("arcadiaguard.gui.zonedetail.zone_off").getString(),
            enabledBtnX + enabledBtnW / 2, gy + 13, enColor);

        int eyeColor = viewZone ? Colors.GOOD : Colors.TEXT_MUTE;
        int eyeBg    = viewZone ? (Colors.GOOD & 0xFFFFFF | 0x18000000) : Colors.BG_2;
        g.fill(eyeBtnX, gy + 6, eyeBtnX + eyeBtnW, gy + 26, eyeBg);
        g.fill(eyeBtnX, gy + 6, eyeBtnX + eyeBtnW, gy + 7, eyeColor);
        g.fill(eyeBtnX, gy + 25, eyeBtnX + eyeBtnW, gy + 26, eyeColor);
        g.fill(eyeBtnX, gy + 6, eyeBtnX + 1, gy + 26, eyeColor);
        g.fill(eyeBtnX + eyeBtnW - 1, gy + 6, eyeBtnX + eyeBtnW, gy + 26, eyeColor);
        g.drawCenteredString(font, viewZone ? Component.translatable("arcadiaguard.gui.zonedetail.view_on").getString() : Component.translatable("arcadiaguard.gui.zonedetail.view_off").getString(),
            eyeBtnX + eyeBtnW / 2, gy + 13, eyeColor);

        g.fill(logsBtnX, gy + 6, logsBtnX + logsBtnW, gy + 26, Colors.BG_2);
        g.fill(logsBtnX, gy + 6, logsBtnX + logsBtnW, gy + 7, Colors.ACCENT_LO);
        g.fill(logsBtnX, gy + 25, logsBtnX + logsBtnW, gy + 26, Colors.ACCENT_LO);
        g.fill(logsBtnX, gy + 6, logsBtnX + 1, gy + 26, Colors.ACCENT_LO);
        g.fill(logsBtnX + logsBtnW - 1, gy + 6, logsBtnX + logsBtnW, gy + 26, Colors.ACCENT_LO);
        GuiTextures.icon(g, GuiTextures.ICO_CLOCK, logsBtnX + 4, gy + 8);
        g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.logs_btn").getString(), logsBtnX + logsBtnW / 2 + 8, gy + 13, Colors.ACCENT);

        GuiTextures.dividerH(g, gx + 8, gy + HEADER_H, GUI_W - 16);

        // Register hitboxes
        final int ebx = enabledBtnX, ebw = enabledBtnW;
        hit(ebx, gy + 6, ebw, 20, () ->
            PacketDistributor.sendToServer(GuiActionPayload.toggleZoneEnabled(detail.name(), !detail.enabled())));
        final int evx = eyeBtnX, evw = eyeBtnW;
        hit(evx, gy + 6, evw, 20, () -> { viewZone = !viewZone; updateViewCache(); });
        final int lbx = logsBtnX, lbw = logsBtnW;
        hit(lbx, gy + 6, lbw, 20, () ->
            PacketDistributor.sendToServer(GuiActionPayload.requestZoneLogs(detail.name(), "", "")));
    }

    // ── Colonne 1 : Coordonnées + Stats + Hiérarchie ─────────────────────────────

    private void renderCol1(GuiGraphics g, int mx, int my) {
        int cx = gx + 10;
        int cy = gy + HEADER_H + 8;

        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.section_perimeter").getString(), cx, cy, Colors.ACCENT, false); cy += 14;

        int dx = detail.maxX() - detail.minX() + 1;
        int dy = detail.maxY() - detail.minY() + 1;
        int dz = detail.maxZ() - detail.minZ() + 1;

        drawCoordBlock(g, cx, cy, Component.translatable("arcadiaguard.gui.zonedetail.corner_a").getString(),
            detail.minX() + ", " + detail.minY() + ", " + detail.minZ()); cy += 34;
        drawCoordBlock(g, cx, cy, Component.translatable("arcadiaguard.gui.zonedetail.corner_b").getString(),
            detail.maxX() + ", " + detail.maxY() + ", " + detail.maxZ()); cy += 34;

        net.minecraft.core.BlockPos wp1 = com.arcadia.arcadiaguard.client.ClientZoneCache.wandPos1();
        net.minecraft.core.BlockPos wp2 = com.arcadia.arcadiaguard.client.ClientZoneCache.wandPos2();
        boolean canEdit = wp1 != null && wp2 != null;
        int ebx = cx, eby = cy, ebw = COL1_W - 22;
        int btnH = 14;
        boolean hov = canEdit && mx >= ebx && mx < ebx + ebw && my >= eby && my < eby + btnH;
        int bg = !canEdit ? Colors.BG_0 : (hov ? Colors.accentTint(0x40) : Colors.BG_2);
        g.fill(ebx, eby, ebx + ebw, eby + btnH, bg);
        g.fill(ebx,           eby,            ebx + ebw, eby + 1,        Colors.ACCENT_LO);
        g.fill(ebx,           eby + btnH - 1, ebx + ebw, eby + btnH,    Colors.ACCENT_LO);
        g.fill(ebx,           eby,            ebx + 1,   eby + btnH,    Colors.ACCENT_LO);
        g.fill(ebx + ebw - 1, eby,            ebx + ebw, eby + btnH,    Colors.ACCENT_LO);
        String label = canEdit ? Component.translatable("arcadiaguard.gui.zonedetail.redefine_wand").getString() : Component.translatable("arcadiaguard.gui.zonedetail.need_wand_selection").getString();
        g.drawCenteredString(font, label, ebx + ebw / 2, eby + 3,
            !canEdit ? Colors.TEXT_MUTE : (hov ? Colors.ACCENT_HI : Colors.ACCENT));
        if (canEdit) {
            hit(ebx, eby, ebw, btnH, () -> {
                net.minecraft.core.BlockPos p1 = com.arcadia.arcadiaguard.client.ClientZoneCache.wandPos1();
                net.minecraft.core.BlockPos p2 = com.arcadia.arcadiaguard.client.ClientZoneCache.wandPos2();
                if (p1 != null && p2 != null) {
                    PacketDistributor.sendToServer(GuiActionPayload.setZoneBounds(
                        detail.name(), p1.getX(), p1.getY(), p1.getZ(),
                        p2.getX(), p2.getY(), p2.getZ()));
                }
            });
        }
        cy += btnH + 3;

        int ecbx = cx, ecby = cy, ecbw = COL1_W - 22;
        boolean hov2 = mx >= ecbx && mx < ecbx + ecbw && my >= ecby && my < ecby + btnH;
        g.fill(ecbx, ecby, ecbx + ecbw, ecby + btnH, hov2 ? Colors.accentTint(0x40) : Colors.BG_2);
        g.fill(ecbx,            ecby,            ecbx + ecbw, ecby + 1,        Colors.ACCENT_LO);
        g.fill(ecbx,            ecby + btnH - 1, ecbx + ecbw, ecby + btnH,    Colors.ACCENT_LO);
        g.fill(ecbx,            ecby,            ecbx + 1,    ecby + btnH,    Colors.ACCENT_LO);
        g.fill(ecbx + ecbw - 1, ecby,            ecbx + ecbw, ecby + btnH,    Colors.ACCENT_LO);
        g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.edit_manually").getString(), ecbx + ecbw / 2, ecby + 3,
            hov2 ? Colors.ACCENT_HI : Colors.ACCENT);
        hit(ecbx, ecby, ecbw, btnH, this::openCoordsEditor);
        cy += btnH + 6;

        GuiTextures.dividerH(g, cx - 2, cy, COL1_W - 8); cy += 6;

        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.section_stats").getString(), cx, cy, Colors.ACCENT, false); cy += 14;
        drawStat(g, cx, cy, Component.translatable("arcadiaguard.gui.zonedetail.stat_surface").getString(),  dx + "×" + dz);         cy += 13;
        drawStat(g, cx, cy, Component.translatable("arcadiaguard.gui.zonedetail.stat_height").getString(),   dy + " blocs");          cy += 13;
        drawStat(g, cx, cy, Component.translatable("arcadiaguard.gui.zonedetail.stat_volume").getString(),   fmtVol((long)dx*dy*dz)); cy += 13;
        drawStat(g, cx, cy, Component.translatable("arcadiaguard.gui.zonedetail.stat_chunks").getString(),   (((dx+15)/16)*((dz+15)/16)) + " ch"); cy += 13;
        int memberCount = detail.members().size();
        String memberKey = "arcadiaguard.gui.zonedetail.stat_members." + (memberCount == 1 ? "one" : "other");
        drawStat(g, cx, cy, Component.translatable("arcadiaguard.gui.zonedetail.stat_members_label").getString(),
            Component.translatable(memberKey, memberCount).getString()); cy += 13;
        long explicitCount = detail.flags().stream().filter(f -> !f.inherited()).count();
        String flagKey = "arcadiaguard.gui.zonedetail.stat_flags." + (explicitCount == 1 ? "one" : "other");
        drawStat(g, cx, cy, Component.translatable("arcadiaguard.gui.zonedetail.stat_flags_label").getString(),
            Component.translatable(flagKey, explicitCount).getString()); cy += 16;

        GuiTextures.dividerH(g, cx - 2, cy, COL1_W - 8); cy += 6;

        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.section_hierarchy").getString(), cx, cy, Colors.ACCENT, false); cy += 14;
        String parentLabel = detail.parentName() != null
            ? Component.translatable("arcadiaguard.gui.zonedetail.parent_label_value", detail.parentName()).getString()
            : Component.translatable("arcadiaguard.gui.zonedetail.root_zone").getString();
        int col1TextW = COL1_W - 22;
        String parentLabelTrunc = font.plainSubstrByWidth(parentLabel, col1TextW);
        g.drawString(font, parentLabelTrunc, cx, cy, Colors.TEXT_MUTE, false); cy += 14;

        int inheritToggleY = cy;
        boolean inh = detail.inheritDimFlags();
        int inhColor = inh ? Colors.VERDIGRIS : Colors.TEXT_MUTE;
        boolean inhHov = mx >= cx && mx < cx + col1TextW && my >= cy - 1 && my < cy + 11;
        if (inhHov) g.fill(cx, cy - 1, cx + col1TextW, cy + 11, Colors.accentTint(0x0E));
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.dim_flags_label").getString(), cx, cy, Colors.TEXT_MUTE, false);
        String inhBadge = inh ? Component.translatable("arcadiaguard.gui.zonedetail.inherited").getString() : Component.translatable("arcadiaguard.gui.zonedetail.own").getString();
        g.drawString(font, inhBadge, cx + col1TextW - font.width(inhBadge), cy, inhColor, false);
        hit(cx, inheritToggleY - 1, col1TextW, 12, () ->
            PacketDistributor.sendToServer(
                GuiActionPayload.toggleInheritDimFlags(detail.name(), !detail.inheritDimFlags())));

        GuiTextures.dividerV(g, gx + COL1_W, gy + HEADER_H, GUI_H - HEADER_H - FOOTER_H);
    }

    private void drawCoordBlock(GuiGraphics g, int x, int y, String label, String coords) {
        g.fill(x, y, x + COL1_W - 20, y + 30, Colors.SLOT_BG);
        g.fill(x, y, x + COL1_W - 20, y + 1, Colors.LINE_STRONG);
        g.fill(x, y, x + 1, y + 30, Colors.ACCENT_LO);
        g.drawString(font, label, x + 4, y + 3, Colors.ACCENT, false);
        g.drawString(font, coords, x + 4, y + 15, Colors.TEXT, false);
    }

    private void drawStat(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, label, x, y, Colors.TEXT_MUTE, false);
        g.drawString(font, value, x + COL1_W - 22 - font.width(value), y, Colors.TEXT, false);
    }

    // ── Colonne 2 : Flags ────────────────────────────────────────────────────────

    private static final int DESC_H = 36;

    private void renderCol2(GuiGraphics g, int mx, int my) {
        int cx = gx + COL1_W + 6;
        int cy = gy + HEADER_H + 4;
        int cw = COL2_W - 10;

        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.section_flags").getString(), cx, cy + 2, Colors.ACCENT, false);
        int addBtnX = cx + cw - 22;
        boolean addHov = mx >= addBtnX && mx < addBtnX + 18 && my >= cy && my < cy + 14;
        g.fill(addBtnX, cy, addBtnX + 18, cy + 14, addHov ? Colors.accentTint(0x40) : Colors.BG_2);
        g.fill(addBtnX,      cy,      addBtnX + 18, cy + 1,  Colors.ACCENT_LO);
        g.fill(addBtnX,      cy + 13, addBtnX + 18, cy + 14, Colors.ACCENT_LO);
        g.fill(addBtnX,      cy,      addBtnX + 1,  cy + 14, Colors.ACCENT_LO);
        g.fill(addBtnX + 17, cy,      addBtnX + 18, cy + 14, Colors.ACCENT_LO);
        g.drawCenteredString(font, "+", addBtnX + 9, cy + 3,
            addHov ? Colors.ACCENT_HI : Colors.VERDIGRIS);
        hit(addBtnX, cy, 18, 14, this::openPicker);

        String parentInfo = detail.parentName() != null
            ? Component.translatable("arcadiaguard.gui.zonedetail.inherits_from", detail.parentName()).getString()
            : Component.translatable("arcadiaguard.gui.zonedetail.no_parent_flags").getString();
        g.drawString(font, parentInfo, cx, cy + 12, Colors.TEXT_MUTE, false);
        cy += 26;
        GuiTextures.dividerH(g, cx - 2, cy, cw); cy += 2;

        List<FlagEntry> flags = detail.flags().stream()
            .filter(f -> !f.inherited())
            .toList();

        if (flags.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.no_flags").getString(), cx + cw / 2,
                cy + 20, Colors.TEXT_MUTE);
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.no_flags_hint").getString(), cx + cw / 2,
                cy + 32, Colors.ACCENT_LO);
        } else {
            int listH  = GUI_H - HEADER_H - FOOTER_H - 32 - DESC_H - 4;
            int maxVis = listH / FLAG_H;
            int end    = Math.min(flagScroll + maxVis + 1, flags.size());

            hoveredFlagDesc = "";
            for (int i = flagScroll; i < end; i++) {
                FlagEntry f = flags.get(i);
                int iy  = cy + (i - flagScroll) * FLAG_H;
                boolean hov = mx >= cx && mx < cx + cw && my >= iy && my < iy + FLAG_H;
                if (i % 2 == 1) g.fill(cx, iy, cx + cw, iy + FLAG_H, 0x08FFFFFF);
                if (hov) {
                    g.fill(cx, iy, cx + cw, iy + FLAG_H, Colors.accentTint(0x10));
                    if (!f.description().isEmpty()) hoveredFlagDesc = Component.translatable(f.description()).getString();
                }

                g.drawString(font, f.label(), cx + 4, iy + 6, Colors.TEXT, false);

                int bx = cx + cw - 34;
                int rx = bx - 16;
                final FlagEntry ff = f;
                hit(rx, iy + 4, 14, FLAG_H - 8, () ->
                    PacketDistributor.sendToServer(GuiActionPayload.resetFlag(detail.name(), ff.id())));

                if (f.type() == FlagEntry.TYPE_BOOL) {
                    boolean protectionOn = !f.value();
                    int badgeColor = protectionOn ? Colors.GOOD : Colors.DANGER;
                    int badgeBg    = badgeColor & 0xFFFFFF | 0x20000000;
                    g.fill(bx, iy + 4, bx + 30, iy + FLAG_H - 4, badgeBg);
                    g.fill(bx, iy + 4, bx + 30, iy + 5, badgeColor);
                    g.drawCenteredString(font, protectionOn ? "ON" : "OFF", bx + 15, iy + 7, badgeColor);
                    hit(bx, iy + 4, 30, FLAG_H - 8, () ->
                        PacketDistributor.sendToServer(GuiActionPayload.setFlag(detail.name(), ff.id(), !ff.value())));
                } else {
                    String preview = f.type() == FlagEntry.TYPE_INT ? f.stringValue()
                        : "[" + (f.stringValue().isEmpty() ? 0 : f.stringValue().split(",").length) + "]";
                    g.drawString(font, preview, bx - font.width(preview) + 30, iy + 7, Colors.TEXT_MUTE, false);
                    boolean hovArrow = mx >= bx + 20 && mx < bx + 34 && my >= iy + 4 && my < iy + FLAG_H - 4;
                    g.fill(bx + 20, iy + 4, bx + 34, iy + FLAG_H - 4,
                        hovArrow ? Colors.accentTint(0x40) : Colors.BG_2);
                    g.drawCenteredString(font, ">", bx + 27, iy + 7,
                        hovArrow ? Colors.ACCENT_HI : Colors.ACCENT);
                    hit(bx + 20, iy + 4, 14, FLAG_H - 8, () -> {
                        FlagConfigScreen.FlagType t = ff.type() == FlagEntry.TYPE_INT
                            ? FlagConfigScreen.FlagType.INT : FlagConfigScreen.FlagType.LIST;
                        minecraft.setScreen(new FlagConfigScreen(
                            ZoneDetailScreen.this, t, FlagConfigScreen.Target.ZONE,
                            detail.name(), ff.id(), ff.label(), ff.description(), ff.stringValue()));
                    });
                }

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
                int thumbY = cy + (listH - thumbH) * flagScroll / Math.max(1, flags.size() - maxVis);
                g.fill(trackX, cy, trackX + 2, cy + listH, Colors.BG_0);
                g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Colors.ACCENT_LO);
            }
        }

        GuiTextures.dividerV(g, gx + COL1_W + COL2_W + 1, gy + HEADER_H, GUI_H - HEADER_H - FOOTER_H);
    }

    // ── Flag Picker (modal overlay) ───────────────────────────────────────────────

    private int[] pickerBounds() {
        int pw = Math.min(340, GUI_W - 20);
        int ph = Math.min(360, GUI_H - 40);
        return new int[]{ gx + (GUI_W - pw) / 2, gy + (GUI_H - ph) / 2, pw, ph };
    }

    private List<FlagEntry> pickerFiltered() {
        String s = flagSearchBox == null ? "" : flagSearchBox.getValue().toLowerCase().trim();
        if (s.isEmpty()) return detail.flags();
        return detail.flags().stream()
            .filter(f -> f.label().toLowerCase().contains(s) || f.id().toLowerCase().contains(s))
            .toList();
    }

    private void renderFlagPicker(GuiGraphics g, int mx, int my) {
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, 0x90000000);

        int[] b = pickerBounds();
        int px = b[0], py = b[1], pw = b[2], ph = b[3];

        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Colors.ACCENT_LO);
        g.fill(px, py, px + pw, py + ph, Colors.BG_1);

        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.picker_title").getString(), px + 8, py + 7, Colors.ACCENT, false);
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
        if (flagSearchBox != null) {
            flagSearchBox.setX(px + 8);
            flagSearchBox.setY(searchY + 3);
            flagSearchBox.setWidth(pw - 16);
            flagSearchBox.setVisible(true);
        }
        GuiTextures.dividerH(g, px + 4, searchY + 20, pw - 8);

        int listTop = searchY + 22;
        int listH   = ph - (listTop - py) - 16;
        int maxVis  = listH / PICKER_FLAG_H;
        List<FlagEntry> filtered = pickerFiltered();
        flagPickerScroll = Mth.clamp(flagPickerScroll, 0, Math.max(0, filtered.size() - maxVis));
        int end = Math.min(flagPickerScroll + maxVis + 1, filtered.size());

        for (int i = flagPickerScroll; i < end; i++) {
            FlagEntry f = filtered.get(i);
            int iy = listTop + (i - flagPickerScroll) * PICKER_FLAG_H;
            boolean hov = mx >= px + 4 && mx < px + pw - 4 && my >= iy && my < iy + PICKER_FLAG_H;
            if (i % 2 == 1) g.fill(px + 4, iy, px + pw - 4, iy + PICKER_FLAG_H, 0x08FFFFFF);
            if (hov)         g.fill(px + 4, iy, px + pw - 4, iy + PICKER_FLAG_H, Colors.accentTint(0x20));

            int labelColor = f.inherited() ? Colors.TEXT_MUTE : Colors.TEXT;
            g.drawString(font, f.label(), px + 10, iy + 6, labelColor, false);
            if (!f.inherited()) {
                g.fill(px + 4, iy + 8, px + 7, iy + 12, Colors.VERDIGRIS);
            }

            String typeTag = f.type() == FlagEntry.TYPE_BOOL
                ? Component.translatable("arcadiaguard.gui.zonedetail.flag_type.bool").getString()
                : f.type() == FlagEntry.TYPE_INT
                ? Component.translatable("arcadiaguard.gui.zonedetail.flag_type.int").getString()
                : Component.translatable("arcadiaguard.gui.zonedetail.flag_type.list").getString();
            g.drawString(font, typeTag, px + pw - 8 - font.width(typeTag), iy + 7, Colors.TEXT_MUTE, false);
            GuiTextures.dividerH(g, px + 4, iy + PICKER_FLAG_H - 1, pw - 8);
        }

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.picker_empty").getString(), px + pw / 2, listTop + 16, Colors.TEXT_MUTE);
        }

        if (filtered.size() > maxVis) {
            int trackX = px + pw - 7;
            int thumbH = Math.max(12, listH * maxVis / filtered.size());
            int thumbY = listTop + (listH - thumbH) * flagPickerScroll
                / Math.max(1, filtered.size() - maxVis);
            g.fill(trackX, listTop, trackX + 3, listTop + listH, Colors.BG_0);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, Colors.ACCENT_LO);
        }

        g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.picker_legend").getString(),
            px + pw / 2, py + ph - 10, Colors.TEXT_MUTE);
    }

    // ── S-H20 : ItemBlocks picker ────────────────────────────────────────────────

    private static final int ITEM_ROW_H = 14;

    /** Retourne la liste des ResourceLocation de tous les items candidats filtres par recherche. */
    private List<net.minecraft.resources.ResourceLocation> itemPickerSearchResults() {
        String q = itemSearchBox != null ? itemSearchBox.getValue().trim().toLowerCase() : "";
        java.util.List<net.minecraft.resources.ResourceLocation> all = new java.util.ArrayList<>();
        // Registry client-side contient tous les items vanilla + mods charges
        var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        for (var item : registry) {
            var id = registry.getKey(item);
            if (id == null) continue;
            if (id.toString().equals("minecraft:air")) continue;
            // Deja bloque : skip dans les resultats (affiche dans la section du haut)
            if (detail.blockedItems().contains(id.toString())) continue;
            if (q.isEmpty() || id.getPath().contains(q) || id.getNamespace().contains(q)) {
                all.add(id);
            }
            if (all.size() >= 200) break; // cap pour perf
        }
        all.sort(java.util.Comparator.comparing(net.minecraft.resources.ResourceLocation::toString));
        return all;
    }

    private void renderItemBlocksPicker(GuiGraphics g, int mx, int my) {
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, 0x90000000);
        int pw = Math.min(440, GUI_W - 40);
        int ph = Math.min(360, GUI_H - 40);
        int px = gx + (GUI_W - pw) / 2;
        int py = gy + (GUI_H - ph) / 2;

        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Colors.ACCENT_LO);
        g.fill(px, py, px + pw, py + ph, Colors.BG_1);

        // Titre + close
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.itemblocks.title").getString(),
            px + 8, py + 7, Colors.ACCENT, false);
        int closeBtnX = px + pw - 20;
        boolean closeHov = mx >= closeBtnX && mx < closeBtnX + 16 && my >= py + 4 && my < py + 18;
        if (closeHov) g.fill(closeBtnX, py + 4, closeBtnX + 16, py + 18,
            Colors.DANGER & 0xFFFFFF | 0x40000000);
        g.drawCenteredString(font, "\u2715", closeBtnX + 8, py + 7,
            closeHov ? Colors.DANGER : Colors.TEXT_MUTE);
        GuiTextures.dividerH(g, px + 4, py + 20, pw - 8);

        // Section 1 : liste des items deja bannis (scrollable si > 4)
        int y = py + 24;
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.itemblocks.blocked",
            detail.blockedItems().size()).getString(), px + 8, y, Colors.TEXT_MUTE, false);
        y += 12;
        int blockedTop = y;
        int blockedVisible = 4;
        int blockedH = blockedVisible * ITEM_ROW_H;
        g.fill(px + 4, y, px + pw - 4, y + blockedH, Colors.BG_0);
        List<String> blockedList = detail.blockedItems();
        int blockedEnd = Math.min(blockedList.size(), blockedVisible);
        for (int i = 0; i < blockedEnd; i++) {
            String id = blockedList.get(i);
            int iy = blockedTop + i * ITEM_ROW_H;
            if (i % 2 == 1) g.fill(px + 4, iy, px + pw - 4, iy + ITEM_ROW_H, 0x08FFFFFF);
            g.drawString(font, id, px + 10, iy + 3, Colors.TEXT, false);
            // Bouton ✕ a droite
            int xbtnX = px + pw - 22;
            boolean xhov = mx >= xbtnX && mx < xbtnX + 14 && my >= iy && my < iy + ITEM_ROW_H;
            if (xhov) g.fill(xbtnX, iy, xbtnX + 14, iy + ITEM_ROW_H, Colors.DANGER & 0xFFFFFF | 0x40000000);
            g.drawCenteredString(font, "\u2715", xbtnX + 7, iy + 3, xhov ? Colors.DANGER : Colors.TEXT_MUTE);
        }
        if (blockedList.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.itemblocks.empty").getString(),
                px + pw / 2, blockedTop + blockedH / 2 - 4, Colors.TEXT_MUTE);
        } else if (blockedList.size() > blockedVisible) {
            g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.itemblocks.more",
                blockedList.size() - blockedVisible).getString(),
                px + 8, blockedTop + blockedH + 2, Colors.TEXT_MUTE, false);
        }

        // Separateur
        int searchY = blockedTop + blockedH + 14;
        GuiTextures.dividerH(g, px + 4, searchY - 2, pw - 8);

        // Search box
        g.fill(px + 4, searchY, px + pw - 4, searchY + 18, Colors.BG_0);
        g.fill(px + 4, searchY, px + pw - 4, searchY + 1, Colors.ACCENT_LO);
        g.fill(px + 4, searchY + 17, px + pw - 4, searchY + 18, Colors.ACCENT_LO);
        if (itemSearchBox != null) {
            itemSearchBox.setX(px + 8);
            itemSearchBox.setY(searchY + 3);
            itemSearchBox.setWidth(pw - 16);
            itemSearchBox.setVisible(true);
        }

        // Liste resultats de recherche (section ajoutable)
        int listTop = searchY + 22;
        int listH = ph - (listTop - py) - 14;
        int maxVis = listH / ITEM_ROW_H;
        List<net.minecraft.resources.ResourceLocation> results = itemPickerSearchResults();
        itemPickerScroll = Mth.clamp(itemPickerScroll, 0, Math.max(0, results.size() - maxVis));
        int end = Math.min(itemPickerScroll + maxVis, results.size());
        for (int i = itemPickerScroll; i < end; i++) {
            var id = results.get(i);
            int iy = listTop + (i - itemPickerScroll) * ITEM_ROW_H;
            boolean hov = mx >= px + 4 && mx < px + pw - 4 && my >= iy && my < iy + ITEM_ROW_H;
            if (i % 2 == 1) g.fill(px + 4, iy, px + pw - 4, iy + ITEM_ROW_H, 0x08FFFFFF);
            if (hov) g.fill(px + 4, iy, px + pw - 4, iy + ITEM_ROW_H, Colors.accentTint(0x20));
            g.drawString(font, id.toString(), px + 10, iy + 3, Colors.TEXT, false);
            // Icone + a droite
            int addX = px + pw - 22;
            boolean ahov = mx >= addX && mx < addX + 14 && my >= iy && my < iy + ITEM_ROW_H;
            if (ahov) g.fill(addX, iy, addX + 14, iy + ITEM_ROW_H, Colors.VERDIGRIS & 0xFFFFFF | 0x40000000);
            g.drawCenteredString(font, "+", addX + 7, iy + 3, ahov ? Colors.VERDIGRIS : Colors.TEXT_MUTE);
        }
        if (results.isEmpty() && itemSearchBox != null && !itemSearchBox.getValue().trim().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.itemblocks.no_match").getString(),
                px + pw / 2, listTop + 16, Colors.TEXT_MUTE);
        }

        // Scrollbar
        if (results.size() > maxVis) {
            int trackX = px + pw - 7;
            int thumbH = Math.max(12, listH * maxVis / results.size());
            int thumbY = listTop + (listH - thumbH) * itemPickerScroll
                / Math.max(1, results.size() - maxVis);
            g.fill(trackX, listTop, trackX + 3, listTop + listH, Colors.BG_0);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, Colors.ACCENT_LO);
        }
    }

    /** Click handling pour le ItemBlocks popup. Retourne true si consomme. */
    private boolean handleItemBlocksPickerClick(double mx, double my) {
        int pw = Math.min(440, GUI_W - 40);
        int ph = Math.min(360, GUI_H - 40);
        int px = gx + (GUI_W - pw) / 2;
        int py = gy + (GUI_H - ph) / 2;

        // Close
        int closeBtnX = px + pw - 20;
        if (mx >= closeBtnX && mx < closeBtnX + 16 && my >= py + 4 && my < py + 18) {
            popup = new PopupState.None();
            itemSearchBox.setVisible(false);
            return true;
        }

        // Hors du popup = close
        if (mx < px || mx >= px + pw || my < py || my >= py + ph) {
            popup = new PopupState.None();
            itemSearchBox.setVisible(false);
            return true;
        }

        // Section items bannis (haut) : clic sur ✕
        int blockedTop = py + 24 + 12;
        int blockedVisible = 4;
        List<String> blockedList = detail.blockedItems();
        int blockedEnd = Math.min(blockedList.size(), blockedVisible);
        for (int i = 0; i < blockedEnd; i++) {
            int iy = blockedTop + i * ITEM_ROW_H;
            int xbtnX = px + pw - 22;
            if (mx >= xbtnX && mx < xbtnX + 14 && my >= iy && my < iy + ITEM_ROW_H) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    GuiActionPayload.itemBlockRemove(detail.name(), blockedList.get(i)));
                return true;
            }
        }

        // Section search results (bas) : clic sur +
        int searchY = blockedTop + blockedVisible * ITEM_ROW_H + 14;
        int listTop = searchY + 22;
        int listH = ph - (listTop - py) - 14;
        int maxVis = listH / ITEM_ROW_H;
        List<net.minecraft.resources.ResourceLocation> results = itemPickerSearchResults();
        int end = Math.min(itemPickerScroll + maxVis, results.size());
        for (int i = itemPickerScroll; i < end; i++) {
            int iy = listTop + (i - itemPickerScroll) * ITEM_ROW_H;
            int addX = px + pw - 22;
            if (mx >= addX && mx < addX + 14 && my >= iy && my < iy + ITEM_ROW_H) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    GuiActionPayload.itemBlockAdd(detail.name(), results.get(i).toString()));
                return true;
            }
        }

        return true; // absorbe le clic sur le popup
    }

    // ── Encart description flag ──────────────────────────────────────────────────

    private void renderFlagDesc(GuiGraphics g) {
        int cx = gx + COL1_W + 6;
        int cw = COL2_W - 10;
        int dy = gy + GUI_H - FOOTER_H - DESC_H - 2;

        GuiTextures.dividerH(g, cx - 2, dy, cw);
        g.fill(cx, dy + 2, cx + cw, dy + DESC_H, Colors.BG_1);
        g.fill(cx, dy + 2, cx + 2, dy + DESC_H, Colors.ACCENT_LO);

        String text = hoveredFlagDesc.isEmpty()
            ? Component.translatable("arcadiaguard.gui.zonedetail.flag_hover_hint").getString()
            : hoveredFlagDesc;
        int textColor = hoveredFlagDesc.isEmpty() ? Colors.TEXT_MUTE : Colors.TEXT;

        int maxW = cw - 8;
        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
            font.split(net.minecraft.network.chat.Component.literal(text), maxW);
        int ly = dy + 6;
        int maxLines = Math.min(lines.size(), 2);
        for (int i = 0; i < maxLines; i++) {
            g.drawString(font, lines.get(i), cx + 4, ly, textColor, false);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                int lw = font.width(lines.get(i));
                g.drawString(font, "\u2026", cx + 4 + lw, ly, textColor, false);
            }
            ly += font.lineHeight + 2;
        }
    }

    // ── Colonne 3 : Membres ──────────────────────────────────────────────────────

    private void renderCol3(GuiGraphics g, int mx, int my) {
        int cx = gx + COL1_W + COL2_W + 6;
        int cy = gy + HEADER_H + 4;
        int cw = GUI_W - COL1_W - COL2_W - 12;

        g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.section_members").getString(), cx, cy + 2, Colors.ACCENT, false);
        int mc2 = detail.members().size();
        String mcKey = "arcadiaguard.gui.zonedetail.stat_members." + (mc2 == 1 ? "one" : "other");
        g.drawString(font, Component.translatable(mcKey, mc2).getString(), cx, cy + 12, Colors.TEXT_MUTE, false);
        cy += 26;
        GuiTextures.dividerH(g, cx - 2, cy, cw); cy += 2;

        boolean isWhitelist = popup instanceof PopupState.WhitelistInput;
        boolean isParent    = popup instanceof PopupState.ParentInput;

        if (isWhitelist) {
            g.fill(cx, cy, cx + cw, cy + 20, Colors.SLOT_BG);
            g.fill(cx, cy, cx + cw, cy + 1, Colors.LINE_STRONG);
            g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.add_player_label").getString(), cx + 2, cy + 12 - font.lineHeight / 2, Colors.TEXT_MUTE, false);
            whitelistBox.setX(cx + 80);
            whitelistBox.setY(cy + 3);
            whitelistBox.setWidth(cw - 84);
            whitelistBox.setVisible(true);
            parentBox.setVisible(false);
            cy += 24;
        } else if (isParent) {
            g.fill(cx, cy, cx + cw, cy + 20, Colors.SLOT_BG);
            g.fill(cx, cy, cx + cw, cy + 1, Colors.LINE_STRONG);
            g.drawString(font, Component.translatable("arcadiaguard.gui.zonedetail.parent_label").getString(), cx + 2, cy + 12 - font.lineHeight / 2, Colors.TEXT_MUTE, false);
            parentBox.setX(cx + 72);
            parentBox.setY(cy + 3);
            parentBox.setWidth(cw - 76);
            parentBox.setVisible(true);
            whitelistBox.setVisible(false);
            cy += 24;
        } else {
            whitelistBox.setVisible(false);
            parentBox.setVisible(false);
        }

        List<MemberEntry> members = detail.members();
        int listH  = GUI_H - HEADER_H - FOOTER_H - 52;
        if (isWhitelist || isParent) listH -= 24;
        int maxVis = listH / MBR_H;
        int end    = Math.min(memberScroll + maxVis + 1, members.size());

        for (int i = memberScroll; i < end; i++) {
            MemberEntry m = members.get(i);
            int iy  = cy + (i - memberScroll) * MBR_H;
            boolean hov = mx >= cx && mx < cx + cw && my >= iy && my < iy + MBR_H;
            if (hov) g.fill(cx, iy, cx + cw, iy + MBR_H, Colors.accentTint(0x0C));

            g.fill(cx + 2, iy + 3, cx + 16, iy + MBR_H - 3, Colors.ACCENT_LO);
            g.drawString(font, String.valueOf(m.name().charAt(0)).toUpperCase(),
                cx + 5, iy + 7, Colors.BG_0, false);
            g.drawString(font, m.name(), cx + 20, iy + 4, Colors.TEXT, false);
            g.drawString(font, m.uuid().substring(0, 8) + "…", cx + 20, iy + 13, Colors.TEXT_MUTE, false);

            if (hov) {
                int rx = cx + cw - 30;
                g.fill(rx, iy + 4, rx + 26, iy + MBR_H - 4, Colors.DANGER & 0xFFFFFF | 0x40000000);
                g.drawCenteredString(font, "×", rx + 13, iy + 7, Colors.DANGER);
            }
            GuiTextures.dividerH(g, cx, iy + MBR_H - 1, cw);

            final MemberEntry mm = m;
            int rx = cx + cw - 30;
            hit(rx, iy + 4, 26, MBR_H - 8, () ->
                PacketDistributor.sendToServer(GuiActionPayload.whitelistRemove(detail.name(), mm.name())));
        }

        if (members.isEmpty())
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.no_members").getString(), cx + cw / 2, cy + 30, Colors.TEXT_MUTE);
    }

    // ── Footer ───────────────────────────────────────────────────────────────────

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int fy = gy + GUI_H - FOOTER_H;
        GuiTextures.dividerH(g, gx + 8, fy, GUI_W - 16);

        // Update footer button labels dynamically based on popup state
        boolean isWhitelist = popup instanceof PopupState.WhitelistInput;
        boolean isParent    = popup instanceof PopupState.ParentInput;
        if (footerPlayerBtn != null) {
            footerPlayerBtn.setMessage(isWhitelist
                ? Component.translatable("arcadiaguard.gui.zonedetail.confirm_player")
                : Component.translatable("arcadiaguard.gui.zonedetail.add_player"));
        }
        if (footerParentBtn != null) {
            footerParentBtn.setMessage(isParent
                ? Component.translatable("arcadiaguard.gui.zonedetail.confirm_parent")
                : Component.translatable("arcadiaguard.gui.zonedetail.set_parent"));
        }
        // CartographiaButton widgets are rendered by super.render() — no drawBtn calls needed.
    }

    private void drawFocusRingIfFocused(GuiGraphics g, EditBox eb) {
        if (eb == null || !eb.isVisible()) return;
        if (getFocused() == eb) {
            int x = eb.getX(), y = eb.getY(), w = eb.getWidth(), h = eb.getHeight();
            g.fill(x - 1, y - 1, x + w + 1, y,     Colors.ACCENT_HI);
            g.fill(x - 1, y + h, x + w + 1, y + h + 1, Colors.ACCENT_HI);
            g.fill(x - 1, y,     x,          y + h, Colors.ACCENT_HI);
            g.fill(x + w, y,     x + w + 1,  y + h, Colors.ACCENT_HI);
        }
    }

    private void drawBtn(GuiGraphics g, int mx, int my, int x, int y, int w, int h,
                         String label, int tc, int bg) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? Colors.accentTint(0x30) : bg);
        g.fill(x, y, x + w, y + 1, Colors.ACCENT_LO);
        g.fill(x, y + h - 1, x + w, y + h, Colors.ACCENT_LO);
        g.fill(x, y, x + 1, y + h, Colors.ACCENT_LO);
        g.fill(x + w - 1, y, x + w, y + h, Colors.ACCENT_LO);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hov ? Colors.ACCENT_HI : tc);
    }

    // ── Popup confirmation suppression ───────────────────────────────────────────

    private void renderConfirmPopup(GuiGraphics g, int mx, int my) {
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, 0x80000000);

        int pw = Math.min(270, GUI_W - 10);
        int ph = 86;
        int px = gx + (GUI_W - pw) / 2;
        int py = gy + (GUI_H - ph) / 2;

        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, Colors.DANGER & 0xFFFFFF | 0xFF000000);
        g.fill(px, py, px + pw, py + ph, Colors.BG_1);

        g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.confirm_delete_title", detail.name()).getString(), px + pw / 2, py + 10, Colors.TEXT);
        g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.confirm_delete_warn1").getString(), px + pw / 2, py + 22, Colors.TEXT_MUTE);
        g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonedetail.confirm_delete_warn2").getString(), px + pw / 2, py + 33, Colors.TEXT_MUTE);

        drawBtn(g, mx, my, px + 10,       py + 54, 100, 22, Component.translatable("arcadiaguard.gui.common.cancel").getString(),     Colors.TEXT_DIM, Colors.BG_2);
        drawBtn(g, mx, my, px + pw - 110, py + 54, 100, 22, Component.translatable("arcadiaguard.gui.zonedetail.confirm_delete_btn").getString(), Colors.DANGER,   Colors.BG_2);
    }

    // ── Popup + Hitbox handlers ───────────────────────────────────────────────────

    private boolean handleConfirmDeleteClick(int imx, int imy) {
        int pw = Math.min(270, GUI_W - 10), ph = 86;
        int px = gx + (GUI_W - pw) / 2, py = gy + (GUI_H - ph) / 2;
        if (imx >= px + 10 && imx < px + 110 && imy >= py + 54 && imy < py + 76) {
            popup = new PopupState.None(); return true;
        }
        if (imx >= px + pw - 110 && imx < px + pw - 10 && imy >= py + 54 && imy < py + 76) {
            PacketDistributor.sendToServer(GuiActionPayload.deleteZone(detail.name()));
            minecraft.setScreen(parent);
        }
        return true;
    }

    private boolean handleFlagPickerClick(int imx, int imy, double mx, double my, int btn) {
        int[] b = pickerBounds();
        int px = b[0], py = b[1], pw = b[2], ph = b[3];
        int closeBtnX = px + pw - 20;
        if (imx >= closeBtnX && imx < closeBtnX + 16 && imy >= py + 4 && imy < py + 18) {
            closePicker(); return true;
        }
        int searchY = py + 24, listTop = searchY + 22;
        int listH = ph - (listTop - py) - 16;
        List<FlagEntry> filtered = pickerFiltered();
        if (imx >= px + 4 && imx < px + pw - 4 && imy >= listTop && imy < listTop + listH) {
            int idx = (imy - listTop) / PICKER_FLAG_H + flagPickerScroll;
            if (idx >= 0 && idx < filtered.size()) {
                FlagEntry f = filtered.get(idx);
                if (f.type() == FlagEntry.TYPE_BOOL) {
                    boolean newVal = f.inherited() ? false : !f.value();
                    PacketDistributor.sendToServer(GuiActionPayload.setFlag(detail.name(), f.id(), newVal));
                } else {
                    closePicker();
                    FlagConfigScreen.FlagType t = f.type() == FlagEntry.TYPE_INT
                        ? FlagConfigScreen.FlagType.INT : FlagConfigScreen.FlagType.LIST;
                    minecraft.setScreen(new FlagConfigScreen(
                        this, t, FlagConfigScreen.Target.ZONE,
                        detail.name(), f.id(), f.label(), f.description(), f.stringValue()));
                }
                return true;
            }
        }
        if (imx < px || imx >= px + pw || imy < py || imy >= py + ph) {
            closePicker(); return true;
        }
        if (flagSearchBox != null && flagSearchBox.isVisible()) flagSearchBox.mouseClicked(mx, my, btn);
        return true;
    }

    private boolean handleCoordsEditorClick(int imx, int imy, double mx, double my, int btn) {
        int[] b = coordsEditorBounds();
        int px = b[0], py = b[1], pw = b[2], ph = b[3];
        int closeBtnX = px + pw - 20;
        if (imx >= closeBtnX && imx < closeBtnX + 16 && imy >= py + 4 && imy < py + 18) {
            closeCoordsEditor(); return true;
        }
        int btnY = py + ph - 24;
        if (imx >= px + 8 && imx < px + 78 && imy >= btnY && imy < btnY + 16) {
            closeCoordsEditor(); return true;
        }
        if (imx >= px + pw - 100 && imx < px + pw - 10 && imy >= btnY && imy < btnY + 16) {
            applyCoordsEditor(); return true;
        }
        if (imx < px || imx >= px + pw || imy < py || imy >= py + ph) {
            closeCoordsEditor(); return true;
        }
        if (coordBoxes != null) for (EditBox cb : coordBoxes) if (cb.isVisible() && cb.mouseClicked(mx, my, btn)) break;
        return true;
    }

    // ── mouseClicked ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;
        if (popup instanceof PopupState.ConfirmDelete)
            return handleConfirmDeleteClick(imx, imy);
        if (popup instanceof PopupState.FlagPicker)
            return handleFlagPickerClick(imx, imy, mx, my, btn);
        if (popup instanceof PopupState.ItemBlocksPicker) {
            // Laisser la search box consommer le clic d'abord
            if (itemSearchBox != null && itemSearchBox.isVisible() && itemSearchBox.mouseClicked(mx, my, btn)) {
                setFocused(itemSearchBox);
                return true;
            }
            return handleItemBlocksPickerClick(mx, my);
        }
        if (popup instanceof PopupState.CoordsEditor)
            return handleCoordsEditorClick(imx, imy, mx, my, btn);
        if (super.mouseClicked(mx, my, btn)) return true;
        for (Hitbox h : hitboxes) { if (h.hit(imx, imy)) { h.action().run(); return true; } }
        return false;
    }

    // ── Scroll + Keys ────────────────────────────────────────────────────────────

    private void updateViewCache() {
        if (viewZone) {
            ClientZoneInfo info = new ClientZoneInfo(
                detail.name(), detail.minX(), detail.minY(), detail.minZ(),
                detail.maxX(), detail.maxY(), detail.maxZ(),
                detail.minX() == Integer.MIN_VALUE, detail.parentName() != null);
            ClientZoneCache.add(info);
        } else {
            ClientZoneCache.remove(detail.name());
        }
    }

    private void openPicker() {
        popup = new PopupState.FlagPicker();
        flagPickerScroll = 0;
        if (flagSearchBox != null) {
            flagSearchBox.setValue("");
            flagSearchBox.setVisible(true);
            setFocused(flagSearchBox);
        }
    }

    private void closePicker() {
        popup = new PopupState.None();
        if (flagSearchBox != null) {
            flagSearchBox.setVisible(false);
            flagSearchBox.setValue("");
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (popup instanceof PopupState.FlagPicker) {
            int[] b = pickerBounds();
            int searchY = b[1] + 24;
            int listTop = searchY + 22;
            int listH   = b[3] - (listTop - b[1]) - 16;
            int maxVis  = listH / PICKER_FLAG_H;
            flagPickerScroll = Mth.clamp((int)(flagPickerScroll - dy),
                0, Math.max(0, pickerFiltered().size() - maxVis));
            return true;
        }
        if (popup instanceof PopupState.ItemBlocksPicker) {
            int pw = Math.min(440, GUI_W - 40);
            int ph = Math.min(360, GUI_H - 40);
            int py = gy + (GUI_H - ph) / 2;
            int blockedTop = py + 24 + 12;
            int searchY = blockedTop + 4 * ITEM_ROW_H + 14;
            int listTop = searchY + 22;
            int listH = ph - (listTop - py) - 14;
            int maxVis = listH / ITEM_ROW_H;
            itemPickerScroll = Mth.clamp((int)(itemPickerScroll - dy),
                0, Math.max(0, itemPickerSearchResults().size() - maxVis));
            return true;
        }

        if (super.mouseScrolled(mx, my, dx, dy)) return true;
        int col2X = gx + COL1_W + 6;
        int col3X = gx + COL1_W + COL2_W + 6;
        int imx = (int) mx;
        if (imx >= col3X) {
            boolean extraRow = popup instanceof PopupState.WhitelistInput
                || popup instanceof PopupState.ParentInput;
            int mListH  = GUI_H - HEADER_H - FOOTER_H - 52 - (extraRow ? 24 : 0);
            int mMaxVis = mListH / MBR_H;
            memberScroll = Mth.clamp((int)(memberScroll - dy),
                0, Math.max(0, detail.members().size() - mMaxVis));
        } else if (imx >= col2X) {
            List<FlagEntry> explicit = detail.flags().stream().filter(f -> !f.inherited()).toList();
            int listH  = GUI_H - HEADER_H - FOOTER_H - 32 - DESC_H - 4;
            int maxVis = listH / FLAG_H;
            flagScroll = Mth.clamp((int)(flagScroll - dy),
                0, Math.max(0, explicit.size() - maxVis));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            if (getFocused() instanceof EditBox eb) {
                eb.setFocused(false);
                setFocused(null);
                return true;
            }
            if (popup instanceof PopupState.FlagPicker) { closePicker(); return true; }
            if (popup instanceof PopupState.ConfirmDelete) { popup = new PopupState.None(); return true; }
            if (popup instanceof PopupState.ItemBlocksPicker) {
                popup = new PopupState.None();
                if (itemSearchBox != null) {
                    itemSearchBox.setVisible(false);
                    itemSearchBox.setValue("");
                }
                return true;
            }
            if (popup instanceof PopupState.CoordsEditor) { popup = new PopupState.None(); return true; }
            if (popup instanceof PopupState.WhitelistInput || popup instanceof PopupState.ParentInput) {
                popup = new PopupState.None();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String fmtVol(long v) {
        if (v >= 1_000_000) return (v / 1_000_000) + "M bl";
        if (v >= 1_000)     return (v / 1_000) + "k bl";
        return v + " bl";
    }
}
