package com.arcadia.arcadiaguard.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * EditBox vanilla dont le TEXTE INTERIEUR est centre verticalement dans la box.
 *
 * <p>Minecraft {@link EditBox#renderWidget} dessine le texte a {@code y + 4} fixe
 * (quasi top-aligned dans une box de hauteur > 8). Pour une box de 14-20px,
 * le rendu apparait visuellement top-heavy (3-5px d'espace en bas).
 *
 * <p>Cette sous-classe decale le canvas de rendu d'un offset calcule pour que le
 * texte soit centre verticalement dans la hauteur declaree — sans toucher a la
 * position ou taille de la box elle-meme.
 */
@OnlyIn(Dist.CLIENT)
public class CenteredEditBox extends EditBox {

    public CenteredEditBox(Font font, int x, int y, int width, int height, Component placeholder) {
        super(font, x, y, width, height, placeholder);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        // Vanilla Minecraft EditBox draws text at internal y+4 (approx). For centered visual
        // inside a box of this.getHeight(), we want text top at y + (h - 8) / 2.
        // Offset Y = desired - current = ((h-8)/2) - 4.
        int offsetY = (this.getHeight() - 8) / 2 - 4;
        if (offsetY == 0) { super.renderWidget(g, mx, my, partialTick); return; }
        g.pose().pushPose();
        g.pose().translate(0.0f, (float) offsetY, 0.0f);
        super.renderWidget(g, mx, my - offsetY, partialTick);
        g.pose().popPose();
    }
}
