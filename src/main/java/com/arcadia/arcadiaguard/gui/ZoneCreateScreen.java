package com.arcadia.arcadiaguard.gui;

import com.arcadia.arcadiaguard.gui.widget.CartographiaButton;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class ZoneCreateScreen extends Screen {

    private int GUI_W, GUI_H;

    private final Screen parent;
    private BlockPos pos1, pos2;

    private int gx, gy;
    private EditBox nameBox;
    private String errorMsg = "";

    private CartographiaButton cancelBtn;
    private CartographiaButton createBtn;

    public ZoneCreateScreen(Screen parent, BlockPos pos1, BlockPos pos2) {
        super(Component.translatable("arcadiaguard.gui.zonecreate.title"));
        this.parent = parent;
        this.pos1   = pos1;
        this.pos2   = pos2;
    }

    @Override
    protected void init() {
        GUI_W = Math.min(540, width  - 4);
        GUI_H = Math.min(360, height - 4);
        gx = (width  - GUI_W) / 2;
        gy = (height - GUI_H) / 2;

        nameBox = new EditBox(font, gx + 14, gy + 53, GUI_W - 28, 16,
            Component.translatable("arcadiaguard.gui.zone_create.name_hint"));
        nameBox.setMaxLength(64);
        nameBox.setBordered(false);
        nameBox.setTextColor(Colors.TEXT);
        nameBox.setHint(Component.translatable("arcadiaguard.gui.zone_create.name_example")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        addRenderableWidget(nameBox);
        setFocused(nameBox);

        int fy = gy + GUI_H - 28;

        cancelBtn = CartographiaButton.neutral(
            gx + 8, fy + 6, 80, 16,
            Component.translatable("arcadiaguard.gui.common.cancel"),
            b -> minecraft.setScreen(parent));
        addRenderableWidget(cancelBtn);

        createBtn = CartographiaButton.accent(
            gx + GUI_W - 130, fy + 6, 122, 16,
            Component.translatable("arcadiaguard.gui.zonecreate.create"),
            b -> tryCreate());
        addRenderableWidget(createBtn);
    }

    private void tryCreate() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            errorMsg = Component.translatable("arcadiaguard.gui.zonecreate.error.empty").getString();
            return;
        }
        if (!name.matches("[a-z0-9_\\-]+")) {
            errorMsg = Component.translatable("arcadiaguard.gui.zonecreate.error.invalid").getString();
            return;
        }
        if (name.length() > 64) {
            errorMsg = Component.translatable("arcadiaguard.gui.zonecreate.error.toolong").getString();
            return;
        }
        PacketDistributor.sendToServer(GuiActionPayload.createZone(
            name,
            pos1.getX(), pos1.getY(), pos1.getZ(),
            pos2.getX(), pos2.getY(), pos2.getZ()
        ));
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBlurredBackground(float partialTick) { /* désactivé */ }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xC0000000);
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, Colors.BG_0);
        GuiTextures.drawFrame(g, gx, gy, GUI_W, GUI_H);

        renderHeader(g);
        renderForm(g, mx, my);
        renderFooterDivider(g);

        // Update create button state
        boolean canCreate = pos1 != null && pos2 != null
            && nameBox.getValue().matches("[a-z0-9_\\-]+")
            && !nameBox.getValue().isEmpty()
            && nameBox.getValue().length() <= 64;
        createBtn.active = canCreate;

        super.render(g, mx, my, delta);
    }

    private void renderHeader(GuiGraphics g) {
        GuiTextures.icon(g, GuiTextures.ICO_PIN, gx + 10, gy + 10);
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.zonecreate.title").getString(),
            gx + 30, gy + 8, Colors.ACCENT_HI, false);
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.zonecreate.subtitle").getString(),
            gx + 30, gy + 18, Colors.TEXT_MUTE, false);
        GuiTextures.dividerH(g, gx + 8, gy + 32, GUI_W - 16);
    }

    private void renderForm(GuiGraphics g, int mx, int my) {
        int cx = gx + 14, cy = gy + 40;

        // Champ nom
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.zonecreate.name_label").getString(),
            cx, cy, Colors.TEXT_MUTE, false);
        cy += 11;
        g.fill(cx - 2, cy, cx + GUI_W - 26, cy + 20, Colors.SLOT_BG);
        g.fill(cx - 2, cy, cx + GUI_W - 26, cy + 1, Colors.ACCENT_LO);
        // EditBox rendu par super
        cy += 26;

        // Séparateur
        GuiTextures.dividerH(g, cx - 2, cy, GUI_W - 24); cy += 8;

        // Blocs A/B
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.zonecreate.corners_label").getString(),
            cx, cy, Colors.TEXT_MUTE, false);
        cy += 11;

        renderCornerBox(g, cx,             cy, Component.translatable("arcadiaguard.gui.zonecreate.corner_a").getString(), pos1);
        renderCornerBox(g, cx + (GUI_W - 28) / 2 + 4, cy, Component.translatable("arcadiaguard.gui.zonecreate.corner_b").getString(), pos2);
        cy += 46;

        // Stats de la zone
        if (pos1 != null && pos2 != null) {
            int dx = Math.abs(pos2.getX() - pos1.getX()) + 1;
            int dy = Math.abs(pos2.getY() - pos1.getY()) + 1;
            int dz = Math.abs(pos2.getZ() - pos1.getZ()) + 1;
            long vol = (long) dx * dy * dz;

            GuiTextures.dividerH(g, cx - 2, cy, GUI_W - 24); cy += 8;

            int statsCol2 = cx + (GUI_W - 28) / 2;
            g.drawString(font, Component.translatable("arcadiaguard.gui.zonecreate.stat_surface", dx + "×" + dz).getString(), cx, cy, Colors.TEXT_DIM, false);
            g.drawString(font, Component.translatable("arcadiaguard.gui.zonecreate.stat_volume", fmtVol(vol)).getString(), statsCol2, cy, Colors.TEXT_DIM, false);
            cy += 12;
            g.drawString(font, Component.translatable("arcadiaguard.gui.zonecreate.stat_height", dy).getString(), cx, cy, Colors.TEXT_DIM, false);
            int chunks = ((dx + 15) / 16) * ((dz + 15) / 16);
            g.drawString(font, Component.translatable("arcadiaguard.gui.zonecreate.stat_chunks_label", chunks).getString(), statsCol2, cy, Colors.TEXT_DIM, false);
            cy += 16;

            // Validation visuelle
            g.fill(cx - 2, cy, cx + GUI_W - 24, cy + 24, Colors.VERDIGRIS & 0xFFFFFF | 0x18000000);
            g.fill(cx - 2, cy, cx - 1, cy + 24, Colors.VERDIGRIS);
            GuiTextures.icon(g, GuiTextures.ICO_CHECK, cx + 4, cy + 4);
            g.drawString(font,
                Component.translatable("arcadiaguard.gui.zonecreate.valid").getString(),
                cx + 24, cy + 8, Colors.VERDIGRIS, false);
        } else {
            g.fill(cx - 2, cy, cx + GUI_W - 24, cy + 24, Colors.WARN & 0xFFFFFF | 0x18000000);
            g.fill(cx - 2, cy, cx - 1, cy + 24, Colors.WARN);
            GuiTextures.icon(g, GuiTextures.ICO_BOLT, cx + 4, cy + 4);
            g.drawString(font,
                Component.translatable("arcadiaguard.gui.zonecreate.need_wand").getString(),
                cx + 24, cy + 8, Colors.WARN, false);
        }

        // Message d'erreur
        if (!errorMsg.isEmpty()) {
            g.drawCenteredString(font, errorMsg, gx + GUI_W / 2, gy + GUI_H - 40, Colors.DANGER);
        }
    }

    private void renderCornerBox(GuiGraphics g, int x, int y, String label, BlockPos pos) {
        int bw = (GUI_W - 28) / 2 - 2;
        g.fill(x, y, x + bw, y + 38, Colors.SLOT_BG);
        g.fill(x, y, x + bw, y + 1, Colors.ACCENT_LO);
        g.fill(x, y, x + 1, y + 38, Colors.ACCENT_LO);
        g.drawString(font, label, x + 4, y + 4, Colors.ACCENT, false);
        if (pos != null) {
            g.drawString(font, pos.getX() + ", " + pos.getY() + ", " + pos.getZ(),
                x + 4, y + 18, Colors.TEXT, false);
        } else {
            g.drawString(font,
                Component.translatable("arcadiaguard.gui.zonecreate.undefined").getString(),
                x + 4, y + 18, Colors.TEXT_MUTE, false);
            g.drawString(font,
                Component.translatable("arcadiaguard.gui.zonecreate.wand_hint").getString(),
                x + 4, y + 27, Colors.TEXT_MUTE, false);
        }
    }

    private void renderFooterDivider(GuiGraphics g) {
        int fy = gy + GUI_H - 28;
        GuiTextures.dividerH(g, gx + 8, fy, GUI_W - 16);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static String fmtVol(long v) {
        if (v >= 1_000_000) return (v / 1_000_000) + "M bl";
        if (v >= 1_000)     return (v / 1_000) + "k bl";
        return v + " bl";
    }
}
