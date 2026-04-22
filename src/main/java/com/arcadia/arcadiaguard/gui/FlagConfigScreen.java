package com.arcadia.arcadiaguard.gui;

import com.arcadia.arcadiaguard.gui.widget.CartographiaButton;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Écran de configuration pour un flag non-booléen (IntFlag ou ListFlag). */
@OnlyIn(Dist.CLIENT)
public final class FlagConfigScreen extends Screen {

    public enum FlagType { INT, LIST }
    public enum Target { ZONE, DIM }

    private int GUI_W, GUI_H;

    private final Screen parent;
    private final FlagType flagType;
    private final Target target;
    private final String targetName;
    private final String flagId;
    private final String label;
    private final String description;
    private final List<String> listEntries;

    private int gx, gy;
    private EditBox input;
    private EditBox listAddBox;
    private int listScroll = 0;
    private String errorMsg = "";

    /** Autocomplete (S-H22) : suggestions pour les ListFlag ars/irons spell. */
    private List<String> allSuggestions = List.of();
    private List<String> filteredSuggestions = List.of();
    private int suggestionScroll = 0;
    private static final int SUGG_ROW_H = 12;
    private static final int SUGG_MAX_ROWS = 6;

    private CartographiaButton backBtn;
    private CartographiaButton resetBtn;
    private CartographiaButton saveBtn;
    private CartographiaButton addEntryBtn;

    public FlagConfigScreen(Screen parent, FlagType flagType, Target target,
                            String targetName, String flagId, String label, String description,
                            String currentValue) {
        super(Component.literal(label));
        this.parent = parent;
        this.flagType = flagType;
        this.target = target;
        this.targetName = targetName;
        this.flagId = flagId;
        this.label = label;
        this.description = description == null ? "" : description;
        this.listEntries = new ArrayList<>();
        if (flagType == FlagType.LIST && currentValue != null && !currentValue.isBlank()) {
            for (String s : currentValue.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) listEntries.add(t);
            }
        }
        this.input = null;
        this.listAddBox = null;
        this.initialIntValue = currentValue;
    }

    private final String initialIntValue;

    @Override
    protected void init() {
        GUI_W = Math.min(360, this.width  - 8);
        GUI_H = Math.min(280, this.height - 8);
        gx = (width - GUI_W) / 2;
        gy = (height - GUI_H) / 2;

        if (flagType == FlagType.INT) {
            input = new com.arcadia.arcadiaguard.gui.widget.CenteredEditBox(font, gx + 16, gy + 74, GUI_W - 32, 18, Component.translatable("arcadiaguard.gui.flag_config.value_hint"));
            input.setMaxLength(12);
            input.setBordered(false);
            input.setTextColor(Colors.TEXT);
            input.setHint(Component.translatable("arcadiaguard.gui.flagconfig.input.hint")
                .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
            input.setValue(initialIntValue == null ? "0" : initialIntValue);
            addRenderableWidget(input);
            setFocused(input);
        } else {
            listAddBox = new com.arcadia.arcadiaguard.gui.widget.CenteredEditBox(font, gx + 16, gy + GUI_H - 58, GUI_W - 80, 18, Component.translatable("arcadiaguard.gui.flag_config.add_hint"));
            listAddBox.setMaxLength(64);
            listAddBox.setBordered(false);
            listAddBox.setTextColor(Colors.TEXT);
            listAddBox.setHint(Component.translatable("arcadiaguard.gui.flagconfig.list_add.hint")
                .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
            listAddBox.setResponder(this::onSearchChanged);
            addRenderableWidget(listAddBox);
            setFocused(listAddBox);
            // S-H22 : charge les suggestions d'autocompletion si le flag est un spell blacklist/whitelist
            allSuggestions = com.arcadia.arcadiaguard.util.SpellRegistryHelper.suggestionsFor(flagId);
            refreshFilteredSuggestions();

            int inputY = gy + GUI_H - 60;
            int addX = gx + GUI_W - 76;
            addEntryBtn = CartographiaButton.accent(
                addX, inputY, 62, 20,
                Component.translatable("arcadiaguard.gui.flagconfig.add_entry"),
                b -> {
                    String v = listAddBox.getValue().trim();
                    if (!v.isEmpty()) { listEntries.add(v); listAddBox.setValue(""); }
                });
            addRenderableWidget(addEntryBtn);
        }

        int fy = gy + GUI_H - 28;

        backBtn = CartographiaButton.neutral(
            gx + 8, fy + 6, 70, 16,
            Component.translatable("arcadiaguard.gui.common.back"),
            b -> minecraft.setScreen(parent));
        addRenderableWidget(backBtn);

        resetBtn = CartographiaButton.danger(
            gx + 84, fy + 6, 90, 16,
            Component.translatable("arcadiaguard.gui.flagconfig.reset"),
            b -> openResetConfirm());
        addRenderableWidget(resetBtn);

        saveBtn = CartographiaButton.accent(
            gx + GUI_W - 90, fy + 6, 82, 16,
            Component.translatable("arcadiaguard.gui.common.save"),
            b -> { if (sendSave()) minecraft.setScreen(parent); });
        addRenderableWidget(saveBtn);
    }

    private void openResetConfirm() {
        minecraft.setScreen(new ConfirmScreen(
            confirmed -> {
                minecraft.setScreen(this);
                if (confirmed) {
                    sendReset();
                    minecraft.setScreen(parent);
                }
            },
            Component.translatable("arcadiaguard.gui.confirm.reset.title"),
            Component.translatable("arcadiaguard.gui.confirm.reset.message", label),
            Component.translatable("arcadiaguard.gui.confirm.reset.yes"),
            Component.translatable("arcadiaguard.gui.confirm.reset.no")
        ));
    }

    @Override
    public void renderBlurredBackground(float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xC0000000);
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, Colors.BG_0);
        GuiTextures.drawFrame(g, gx, gy, GUI_W, GUI_H);

        // Header
        GuiTextures.icon(g, GuiTextures.ICO_GEAR, gx + 10, gy + 8);
        g.drawString(font, label, gx + 28, gy + 10, Colors.ACCENT_HI, false);
        String ctx = target == Target.DIM
            ? Component.translatable("arcadiaguard.gui.flagconfig.ctx_dim", targetName).getString()
            : Component.translatable("arcadiaguard.gui.flagconfig.ctx_zone", targetName).getString();
        g.drawString(font, ctx, gx + 10, gy + 22, Colors.TEXT_MUTE, false);
        GuiTextures.dividerH(g, gx + 8, gy + 36, GUI_W - 16);

        // Description
        List<net.minecraft.util.FormattedCharSequence> lines =
            font.split(description.isEmpty()
                ? Component.translatable("arcadiaguard.gui.flag_config.no_description")
                : Component.literal(description), GUI_W - 24);
        int dy = gy + 42;
        int maxDescLines = Math.min(lines.size(), 2);
        for (int i = 0; i < maxDescLines; i++) {
            g.drawString(font, lines.get(i), gx + 12, dy, Colors.TEXT_MUTE, false);
            if (i == maxDescLines - 1 && lines.size() > maxDescLines) {
                int lw = font.width(lines.get(i));
                g.drawString(font, "\u2026", gx + 12 + lw, dy, Colors.TEXT_MUTE, false);
            }
            dy += font.lineHeight + 2;
        }

        if (flagType == FlagType.INT) renderInt(g, mx, my);
        else                          renderList(g, mx, my);

        // Error message
        if (!errorMsg.isEmpty()) {
            int fy = gy + GUI_H - 28;
            g.drawCenteredString(font, errorMsg, gx + GUI_W / 2, fy - 10, Colors.DANGER);
        }

        renderFooterDivider(g);

        super.render(g, mx, my, delta);
    }

    private void renderInt(GuiGraphics g, int mx, int my) {
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.flagconfig.int_label").getString(),
            gx + 16, gy + 62, Colors.TEXT, false);
        g.fill(gx + 14, gy + 72, gx + GUI_W - 14, gy + 94, Colors.BG_1);
        g.fill(gx + 14, gy + 72, gx + GUI_W - 14, gy + 73, Colors.ACCENT_LO);
        g.fill(gx + 14, gy + 93, gx + GUI_W - 14, gy + 94, Colors.ACCENT_LO);
    }

    private static final int LIST_ROW_H = 18;

    private void renderList(GuiGraphics g, int mx, int my) {
        g.drawString(font,
            Component.translatable("arcadiaguard.gui.flagconfig.entries_label", listEntries.size()).getString(),
            gx + 16, gy + 62, Colors.TEXT, false);
        int listY = gy + 74;
        int listX = gx + 14;
        int listW = GUI_W - 28;
        int listH = GUI_H - 74 - 80;

        g.fill(listX, listY, listX + listW, listY + listH, Colors.BG_1);

        int maxVis = listH / LIST_ROW_H;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listEntries.size() - maxVis)));
        int end = Math.min(listScroll + maxVis, listEntries.size());

        if (listEntries.isEmpty()) {
            g.drawCenteredString(font,
                Component.translatable("arcadiaguard.gui.flagconfig.empty_list").getString(),
                gx + GUI_W / 2, listY + listH / 2 - 4, Colors.TEXT_MUTE);
        }

        for (int i = listScroll; i < end; i++) {
            int iy = listY + (i - listScroll) * LIST_ROW_H;
            if (i % 2 == 1) g.fill(listX, iy, listX + listW, iy + LIST_ROW_H, 0x08FFFFFF);
            String entry = listEntries.get(i);
            g.drawString(font, entry, listX + 6, iy + 5, Colors.TEXT, false);
            int rx = listX + listW - 16;
            boolean rhov = mx >= rx && mx < rx + 12 && my >= iy + 3 && my < iy + LIST_ROW_H - 3;
            g.fill(rx, iy + 3, rx + 12, iy + LIST_ROW_H - 3, rhov
                ? Colors.DANGER & 0xFFFFFF | 0x50000000 : Colors.BG_2);
            g.drawCenteredString(font, "✕", rx + 6, iy + 5, rhov ? Colors.DANGER : Colors.TEXT_MUTE);
        }

        // Input background (button rendered by addRenderableWidget)
        int inputY = gy + GUI_H - 60;
        g.fill(gx + 14, inputY, gx + GUI_W - 80, inputY + 20, Colors.BG_1);
        g.fill(gx + 14, inputY, gx + GUI_W - 80, inputY + 1, Colors.ACCENT_LO);
        g.fill(gx + 14, inputY + 19, gx + GUI_W - 80, inputY + 20, Colors.ACCENT_LO);

        // S-H22 : dropdown d'autocompletion au-dessus de l'input si listAddBox focus + suggestions dispo
        if (!filteredSuggestions.isEmpty() && listAddBox != null && listAddBox.isFocused()) {
            renderSuggestionDropdown(g, mx, my, inputY);
        }
    }

    private void renderSuggestionDropdown(GuiGraphics g, int mx, int my, int inputY) {
        int maxVis = Math.min(SUGG_MAX_ROWS, filteredSuggestions.size());
        int ddH = maxVis * SUGG_ROW_H + 4;
        int ddW = GUI_W - 80 - 14;
        int ddX = gx + 14;
        int ddY = inputY - ddH - 2;
        g.fill(ddX - 1, ddY - 1, ddX + ddW + 1, ddY + ddH + 1, Colors.ACCENT_LO);
        g.fill(ddX, ddY, ddX + ddW, ddY + ddH, Colors.BG_0);
        int end = Math.min(suggestionScroll + maxVis, filteredSuggestions.size());
        for (int i = suggestionScroll; i < end; i++) {
            int iy = ddY + 2 + (i - suggestionScroll) * SUGG_ROW_H;
            boolean hov = mx >= ddX && mx < ddX + ddW && my >= iy && my < iy + SUGG_ROW_H;
            if (hov) g.fill(ddX + 1, iy, ddX + ddW - 1, iy + SUGG_ROW_H, Colors.ACCENT & 0xFFFFFF | 0x40000000);
            String entry = filteredSuggestions.get(i);
            g.drawString(font, entry, ddX + 4, iy + 2, hov ? Colors.ACCENT_HI : Colors.TEXT, false);
        }
        // Indicateur scroll
        if (filteredSuggestions.size() > maxVis) {
            g.drawString(font, "↓ " + (filteredSuggestions.size() - end), ddX + ddW - 30, ddY + ddH - 10, Colors.TEXT_MUTE, false);
        }
    }

    private void onSearchChanged(String value) {
        suggestionScroll = 0;
        refreshFilteredSuggestions();
    }

    private void refreshFilteredSuggestions() {
        if (allSuggestions.isEmpty()) { filteredSuggestions = List.of(); return; }
        String q = listAddBox != null ? listAddBox.getValue().trim().toLowerCase() : "";
        if (q.isEmpty()) {
            filteredSuggestions = allSuggestions.size() > 50 ? allSuggestions.subList(0, 50) : allSuggestions;
            return;
        }
        List<String> out = new ArrayList<>();
        for (String s : allSuggestions) {
            if (s.toLowerCase().contains(q) && !listEntries.contains(s)) out.add(s);
            if (out.size() >= 50) break;
        }
        filteredSuggestions = out;
    }

    private void renderFooterDivider(GuiGraphics g) {
        int fy = gy + GUI_H - 28;
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
    public boolean mouseClicked(double mx, double my, int btn) {
        // S-H22 : clic sur une suggestion d'autocompletion -> add + clear input
        if (flagType == FlagType.LIST && !filteredSuggestions.isEmpty() && listAddBox != null && listAddBox.isFocused()) {
            int inputY = gy + GUI_H - 60;
            int maxVis = Math.min(SUGG_MAX_ROWS, filteredSuggestions.size());
            int ddH = maxVis * SUGG_ROW_H + 4;
            int ddW = GUI_W - 80 - 14;
            int ddX = gx + 14;
            int ddY = inputY - ddH - 2;
            if (mx >= ddX && mx < ddX + ddW && my >= ddY && my < ddY + ddH) {
                int row = (int)(my - ddY - 2) / SUGG_ROW_H + suggestionScroll;
                if (row >= 0 && row < filteredSuggestions.size()) {
                    String picked = filteredSuggestions.get(row);
                    if (!listEntries.contains(picked)) listEntries.add(picked);
                    listAddBox.setValue("");
                    refreshFilteredSuggestions();
                    return true;
                }
            }
        }
        if (flagType == FlagType.LIST) {
            // ✕ sur une entrée
            int listY = gy + 74;
            int listX = gx + 14;
            int listW = GUI_W - 28;
            int listH = GUI_H - 74 - 80;
            int maxVis = listH / LIST_ROW_H;
            int end = Math.min(listScroll + maxVis, listEntries.size());
            int imx = (int) mx, imy = (int) my;
            if (imx >= listX && imx < listX + listW && imy >= listY && imy < listY + listH) {
                int idx = (imy - listY) / LIST_ROW_H + listScroll;
                if (idx >= listScroll && idx < end) {
                    int iy = listY + (idx - listScroll) * LIST_ROW_H;
                    int rx = listX + listW - 16;
                    if (imx >= rx && imx < rx + 12 && imy >= iy + 3 && imy < iy + LIST_ROW_H - 3) {
                        listEntries.remove(idx);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // S-H22 : scroll dans le dropdown de suggestions
        if (flagType == FlagType.LIST && !filteredSuggestions.isEmpty()
                && listAddBox != null && listAddBox.isFocused()) {
            int inputY = gy + GUI_H - 60;
            int maxVis = Math.min(SUGG_MAX_ROWS, filteredSuggestions.size());
            int ddH = maxVis * SUGG_ROW_H + 4;
            int ddW = GUI_W - 80 - 14;
            int ddX = gx + 14;
            int ddY = inputY - ddH - 2;
            if (mx >= ddX && mx < ddX + ddW && my >= ddY && my < ddY + ddH) {
                suggestionScroll = Math.max(0, Math.min(
                    suggestionScroll - (int) dy,
                    Math.max(0, filteredSuggestions.size() - maxVis)));
                return true;
            }
        }
        if (flagType == FlagType.LIST) {
            listScroll = Math.max(0, listScroll - (int) dy);
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    /** Returns true if save succeeded, false if there was a validation error. */
    private boolean sendSave() {
        String value;
        if (flagType == FlagType.INT) {
            value = input.getValue().trim();
            int parsed;
            try { parsed = Integer.parseInt(value); }
            catch (NumberFormatException e) {
                errorMsg = Component.translatable("arcadiaguard.gui.flagconfig.error.not_int").getString();
                return false;
            }
            // Validation des bornes min/max si le flag est un IntFlag avec limites.
            var flagOpt = com.arcadia.arcadiaguard.api.ArcadiaGuardAPI.get().flagRegistry().get(flagId);
            if (flagOpt.isPresent() && flagOpt.get() instanceof com.arcadia.arcadiaguard.api.flag.IntFlag intFlag) {
                int min = intFlag.min();
                int max = intFlag.max();
                if (parsed < min || parsed > max) {
                    errorMsg = Component.translatable("arcadiaguard.gui.flagconfig.error.out_of_bounds",
                        String.valueOf(min), String.valueOf(max)).getString();
                    return false;
                }
            }
        } else {
            value = String.join(",", listEntries);
        }
        if (target == Target.ZONE) {
            PacketDistributor.sendToServer(GuiActionPayload.setFlagStr(targetName, flagId, value));
        } else {
            PacketDistributor.sendToServer(GuiActionPayload.setDimFlagStr(targetName, flagId, value));
        }
        return true;
    }

    private void sendReset() {
        if (target == Target.ZONE) {
            PacketDistributor.sendToServer(GuiActionPayload.resetFlag(targetName, flagId));
        } else {
            PacketDistributor.sendToServer(GuiActionPayload.resetDimFlag(targetName, flagId));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
