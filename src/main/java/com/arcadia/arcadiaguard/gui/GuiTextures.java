package com.arcadia.arcadiaguard.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Constantes d'atlas et helpers de rendu.
 *
 * cartographia_atlas.png (256×256):
 *   Cadre 9-slice  : (0,0,200,200), coins 8×8
 *   Header bar     : (0,210,200,20)
 *   Bouton primaire: (0,236,60,18)  — cuivre
 *   Bouton ghost   : (70,236,60,18) — contour seul
 *   Bouton danger  : (140,236,60,18) — rouge
 *   Scroll track   : (232,0,6,48)
 *   Scroll thumb   : (240,0,6,16)
 *
 * cartographia_icons.png (128×32) — 16 icônes 16×16
 *   Ligne 0: shield(0) key(1) pin(2) bolt(3) compass(4) gear(5) tag(6) search(7)
 *   Ligne 1: plus(0)   check(1) x(2) eye(3) sword(4) layers(5) users(6) clock(7)
 *
 * cartographia_status.png (32×8) — 4 dots 8×8
 *   0=vert(actif) 8=jaune(alerte) 16=gris(inerte) 24=rouge(danger)
 */
public final class GuiTextures {
    private GuiTextures() {}

    public static final ResourceLocation ATLAS  = ResourceLocation.fromNamespaceAndPath("arcadiaguard", "textures/gui/cartographia_atlas.png");
    public static final ResourceLocation ICONS  = ResourceLocation.fromNamespaceAndPath("arcadiaguard", "textures/gui/cartographia_icons.png");
    public static final ResourceLocation STATUS = ResourceLocation.fromNamespaceAndPath("arcadiaguard", "textures/gui/cartographia_status.png");

    /** 9-slice du cadre principal à partir de l'atlas. */
    public static void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
        int C = 8, AW = 256, AH = 256;
        int inner = 184, edge = 200 - C * 2;
        // fond
        g.fill(x + C, y + C, x + w - C, y + h - C, Colors.BG_1);
        // coins
        g.blit(ATLAS, x,       y,       0,   0,   C, C, AW, AH);
        g.blit(ATLAS, x+w-C,   y,       192, 0,   C, C, AW, AH);
        g.blit(ATLAS, x,       y+h-C,   0,   192, C, C, AW, AH);
        g.blit(ATLAS, x+w-C,   y+h-C,   192, 192, C, C, AW, AH);
        // bords (répétés, on utilise fill avec teinte lineBorder)
        g.fill(x+C, y,     x+w-C, y+1,     Colors.LINE_STRONG);
        g.fill(x+C, y+h-1, x+w-C, y+h,    Colors.LINE_STRONG);
        g.fill(x,   y+C,   x+1,   y+h-C,  Colors.LINE_STRONG);
        g.fill(x+w-1, y+C, x+w,   y+h-C,  Colors.LINE_STRONG);
    }

    /** Ligne de séparateur vertical cuivre. */
    public static void dividerV(GuiGraphics g, int x, int y, int h) {
        g.fill(x, y, x+1, y+h, Colors.ACCENT_LO);
    }

    /** Ligne de séparateur horizontal cuivre. */
    public static void dividerH(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x+w, y+1, Colors.LINE_STRONG);
    }

    /** Icône 16×16 depuis cartographia_icons.png. col/row = index grille. */
    public static void icon(GuiGraphics g, int col, int row, int x, int y) {
        g.blit(ICONS, x, y, col * 16, row * 16, 16, 16, 128, 32);
    }

    /** Status dot 8×8. uOffset = 0(actif) 8(alerte) 16(inerte) 24(danger). */
    public static void statusDot(GuiGraphics g, int u, int x, int y) {
        g.blit(STATUS, x, y, u, 0, 8, 8, 32, 8);
    }

    // indices icônes ligne 0
    public static final int ICO_SHIELD  = 0;
    public static final int ICO_KEY     = 1;
    public static final int ICO_PIN     = 2;
    public static final int ICO_BOLT    = 3;
    public static final int ICO_COMPASS = 4;
    public static final int ICO_GEAR    = 5;
    public static final int ICO_TAG     = 6;
    public static final int ICO_SEARCH  = 7;
    // ligne 1
    public static final int ICO_PLUS   = 8;
    public static final int ICO_CHECK  = 9;
    public static final int ICO_X      = 10;
    public static final int ICO_EYE    = 11;
    public static final int ICO_SWORD  = 12;
    public static final int ICO_LAYERS = 13;
    public static final int ICO_USERS  = 14;
    public static final int ICO_CLOCK  = 15;

    /** Icône par index continu (0-7 = ligne 0, 8-15 = ligne 1). */
    public static void icon(GuiGraphics g, int index, int x, int y) {
        if (index < 0 || index > 15) throw new IllegalArgumentException("icon index out of range: " + index);
        icon(g, index % 8, index / 8, x, y);
    }
}
