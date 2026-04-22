package com.arcadia.arcadiaguard.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * EditBox dont le texte est visuellement centre verticalement dans la box.
 *
 * <p>Vanilla {@link EditBox} dessine deja le texte a {@code y + (h-8)/2} (mathematiquement
 * centre), mais la font Minecraft a un ascender plus grand que le descender, donnant une
 * illusion optique de texte "trop haut". Cette sous-classe ajoute +1px de decalage vers
 * le bas pour compenser, sans bouger le cadre.
 *
 * <p>Technique : on dessine le cadre nous-memes, on desactive le cadre vanilla, puis on
 * laisse super.renderWidget dessiner le texte avec un {@code pose().translate} applique
 * a la position texte voulue.
 */
@OnlyIn(Dist.CLIENT)
public class CenteredEditBox extends EditBox {

    private static final float BASELINE_CORRECTION = 1.0f;

    public CenteredEditBox(Font font, int x, int y, int width, int height, Component placeholder) {
        super(font, x, y, width, height, placeholder);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        if (!this.isVisible()) return;
        boolean bordered = this.isBordered();
        // Cas 1: bordered=false — vanilla dessine texte a y brut (top-aligned).
        // On translate pour centrer verticalement dans la hauteur de l'EditBox.
        if (!bordered) {
            float dy = (getHeight() - 8) / 2.0f + BASELINE_CORRECTION;
            g.pose().pushPose();
            g.pose().translate(0.0f, dy, 0.0f);
            super.renderWidget(g, mx, my - (int) dy, partialTick);
            g.pose().popPose();
            return;
        }
        // Cas 2: bordered=true — on dessine le cadre nous-memes, puis on desactive
        // le cadre vanilla et on translate le texte pour centrer visuellement.
        int borderColor = this.isFocused() ? -1 : -6250336;
        g.fill(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY() + getHeight() + 1, borderColor);
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), -16777216);
        this.setBordered(false);
        try {
            float dx = 4.0f;
            float dy = (getHeight() - 8) / 2.0f + BASELINE_CORRECTION;
            g.pose().pushPose();
            g.pose().translate(dx, dy, 0.0f);
            super.renderWidget(g, mx - (int) dx, my - (int) dy, partialTick);
            g.pose().popPose();
        } finally {
            this.setBordered(true);
        }
    }
}
