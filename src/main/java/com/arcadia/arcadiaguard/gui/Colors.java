package com.arcadia.arcadiaguard.gui;

/** Palette Copper Patina — constantes ARGB pour GuiGraphics. */
public final class Colors {
    private Colors() {}

    public static final int BG_0        = 0xFF17120D;
    public static final int BG_1        = 0xFF1F1812;
    public static final int BG_2        = 0xFF2A2019;
    public static final int BG_3        = 0xFF362A20;
    public static final int LINE        = 0x60D6A167;
    public static final int LINE_STRONG = 0x90D6A167;
    public static final int TEXT        = 0xFFF3E7D3;
    public static final int TEXT_DIM    = 0xFFC2AD8E;
    public static final int TEXT_MUTE   = 0xFFB8A684;
    public static final int ACCENT      = 0xFFD89255;
    public static final int ACCENT_HI   = 0xFFF0B27A;
    public static final int ACCENT_LO   = 0xFFA0642C;
    public static final int VERDIGRIS   = 0xFF6BA894;
    public static final int VERDIM      = 0xFF3F6E5F;
    public static final int WARN        = 0xFFE0A84A;
    public static final int DANGER      = 0xFFC9533D;
    public static final int GOOD        = 0xFF8FB96B;
    public static final int SLOT_BG     = 0xFF0F0B07;

    /** Retourne la couleur accent teinté pour un fond hover/sélection. */
    public static int accentTint(int alpha) {
        return (alpha << 24) | (ACCENT & 0xFFFFFF);
    }
}
