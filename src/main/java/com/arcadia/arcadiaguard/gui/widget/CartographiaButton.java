package com.arcadia.arcadiaguard.gui.widget;

import com.arcadia.arcadiaguard.gui.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Bouton custom au style Cartographia (cuivre/verdigris) — vrai widget Minecraft :
 * supporte narrator, navigation Tab, Entrée/Espace, et focus ring clavier.
 */
public class CartographiaButton extends AbstractButton {

    public enum Variant { NEUTRAL, ACCENT, DANGER, GOOD }

    private final OnPress onPress;
    private final Variant variant;

    public CartographiaButton(int x, int y, int w, int h, Component label, Variant variant, OnPress onPress) {
        super(x, y, w, h, label);
        this.variant = variant;
        this.onPress = onPress;
    }

    public static CartographiaButton neutral(int x, int y, int w, int h, Component label, OnPress onPress) {
        return new CartographiaButton(x, y, w, h, label, Variant.NEUTRAL, onPress);
    }

    public static CartographiaButton accent(int x, int y, int w, int h, Component label, OnPress onPress) {
        return new CartographiaButton(x, y, w, h, label, Variant.ACCENT, onPress);
    }

    public static CartographiaButton danger(int x, int y, int w, int h, Component label, OnPress onPress) {
        return new CartographiaButton(x, y, w, h, label, Variant.DANGER, onPress);
    }

    public static CartographiaButton good(int x, int y, int w, int h, Component label, OnPress onPress) {
        return new CartographiaButton(x, y, w, h, label, Variant.GOOD, onPress);
    }

    @Override
    public void onPress() {
        if (this.active && this.onPress != null) this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHovered();
        boolean focused = isFocused();

        int bg = backgroundFor();
        int border = borderFor();
        int textColor = textColorFor();

        if (!active) {
            bg = Colors.BG_2;
            border = Colors.LINE;
            textColor = Colors.TEXT_MUTE;
        } else if (hov) {
            bg = Colors.accentTint(0x30);
            textColor = textHoverFor();
        }

        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);

        if (focused && active) {
            int ring = Colors.ACCENT_HI;
            g.fill(x - 1, y - 1, x + w + 1, y, ring);
            g.fill(x - 1, y + h, x + w + 1, y + h + 1, ring);
            g.fill(x - 1, y, x, y + h, ring);
            g.fill(x + w, y, x + w + 1, y + h, ring);
        }

        var font = Minecraft.getInstance().font;
        int textY = y + (h - 8) / 2;
        g.drawCenteredString(font, getMessage(), x + w / 2, textY, textColor);
    }

    private int backgroundFor() {
        return switch (variant) {
            case ACCENT -> Colors.BG_3;
            case DANGER -> Colors.BG_2;
            case GOOD   -> Colors.BG_2;
            case NEUTRAL -> Colors.BG_1;
        };
    }

    private int borderFor() {
        return switch (variant) {
            case ACCENT -> Colors.ACCENT;
            case DANGER -> Colors.DANGER;
            case GOOD   -> Colors.GOOD;
            case NEUTRAL -> Colors.ACCENT_LO;
        };
    }

    private int textColorFor() {
        return switch (variant) {
            case ACCENT -> Colors.ACCENT_HI;
            case DANGER -> Colors.DANGER;
            case GOOD   -> Colors.GOOD;
            case NEUTRAL -> Colors.TEXT;
        };
    }

    private int textHoverFor() {
        return switch (variant) {
            case DANGER -> 0xFFE07568;
            case GOOD   -> 0xFFB6D38F;
            default     -> Colors.ACCENT_HI;
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }

    @FunctionalInterface
    public interface OnPress {
        void onPress(CartographiaButton button);
    }
}
