package com.arcadia.arcadiaguard.gui;

import com.arcadia.arcadiaguard.gui.widget.CartographiaButton;
import com.arcadia.arcadiaguard.network.gui.GuiActionPayload;
import com.arcadia.arcadiaguard.network.gui.OpenGuiPayload;
import com.arcadia.arcadiaguard.network.gui.OpenGuiPayload.ZoneEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class ZoneListScreen extends Screen {

    private static final int ITEM_H   = 26;
    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 28;

    private enum ParentFilter { ALL, ROOT, CHILDREN }

    private int GUI_W, GUI_H, SIDEBAR_W, PREVIEW_W;
    private int[] zoneColX = new int[6];

    private final List<ZoneEntry> allZones;
    private List<ZoneEntry> filteredZones;
    private BlockPos wandPos1, wandPos2;

    private int currentPage = 1;
    private int totalPages  = 1;
    private int pageSize    = 50;
    private final Map<Integer, List<ZoneEntry>> pageCache = new HashMap<>();

    private String dimFilter  = "";
    private String searchText = "";
    private ParentFilter parentFilter = ParentFilter.ALL;
    private int selectedIndex = -1;
    private int scrollOffset  = 0;
    /** Set à true après un focus de la search box via F/Ctrl+F : consomme le prochain charTyped('f'/'F'). */
    private boolean suppressNextFChar = false;

    /** Extra dimensions from mod-added zones (computed in applyFilter). */
    private List<String> extraDims = new ArrayList<>();

    private static boolean debugMode = false;

    private int gx, gy;
    private EditBox searchBox;

    // Footer fixed buttons (rebuilt each init)
    private CartographiaButton teleportBtn;
    private CartographiaButton detailsBtn;
    private CartographiaButton deleteBtn;
    private CartographiaButton createBtn;
    private CartographiaButton prevBtn;
    private CartographiaButton nextBtn;
    private CartographiaButton dimFlagsBtn;
    private CartographiaButton debugBtn;
    private CartographiaButton closeBtn;

    // Clickable regions for dim rows / filter rows — rebuilt each render pass.
    private record Rect(int x1, int y1, int x2, int y2) {
        boolean hit(int mx, int my) { return mx >= x1 && mx < x2 && my >= y1 && my < y2; }
    }
    private final List<DimHit> dimHits = new ArrayList<>();
    private record DimHit(String dim, Rect row, Rect gear) {}
    private Rect filterAllRect, filterRootRect, filterChildRect;

    public ZoneListScreen(List<ZoneEntry> zones, BlockPos pos1, BlockPos pos2, boolean debug,
            int page, int pageSize, int totalPages) {
        super(Component.translatable("arcadiaguard.gui.zone_list.title"));
        this.currentPage   = page;
        this.pageSize      = pageSize;
        this.totalPages    = totalPages;
        this.allZones      = new ArrayList<>(zones);
        this.filteredZones = new ArrayList<>(zones);
        this.wandPos1      = pos1;
        this.wandPos2      = pos2;
        debugMode          = debug;
        pageCache.put(page, new ArrayList<>(zones));
    }

    public void refresh(OpenGuiPayload payload) {
        currentPage = payload.page();
        pageSize    = payload.pageSize();
        totalPages  = payload.totalPages();
        pageCache.put(currentPage, new ArrayList<>(payload.zones()));
        allZones.clear();
        allZones.addAll(payload.zones());
        wandPos1  = payload.pos1();
        wandPos2  = payload.pos2();
        debugMode = payload.debugMode();
        scrollOffset  = 0;
        selectedIndex = -1;
        applyFilter();
    }

    @Override
    protected void init() {
        GUI_W    = Math.min(860, width  - 4);
        GUI_H    = Math.min(520, height - 4);
        float scale = Math.min(GUI_W / 860f, GUI_H / 520f);
        SIDEBAR_W = Math.max(80,  Math.round(155 * scale));
        PREVIEW_W = Math.max(80,  Math.round(220 * scale));
        gx = (width  - GUI_W) / 2;
        gy = (height - GUI_H) / 2;

        int tableX = gx + SIDEBAR_W + 4;
        int tableW  = GUI_W - SIDEBAR_W - PREVIEW_W - 8;
        int searchY = gy + HEADER_H + 4;

        zoneColX[0] = tableX + 4;
        zoneColX[1] = tableX + 30;
        zoneColX[2] = tableX + Math.max(80,  tableW * 195 / 477);
        zoneColX[3] = tableX + Math.max(130, tableW * 295 / 477);
        zoneColX[4] = tableX + Math.max(175, tableW * 360 / 477);
        zoneColX[5] = tableX + Math.max(210, tableW * 420 / 477);

        searchBox = new EditBox(font, tableX + 22, searchY + 6, tableW - 30, 14,
            Component.translatable("arcadiaguard.gui.zone_list.search_placeholder"));
        searchBox.setMaxLength(60);
        searchBox.setBordered(false);
        searchBox.setTextColor(Colors.TEXT);
        searchBox.setHint(Component.translatable("arcadiaguard.gui.zone_list.search_hint")
            .withStyle(s -> s.withColor(Colors.TEXT_MUTE)));
        searchBox.setResponder(t -> { searchText = t; scrollOffset = 0; applyFilter(); });
        addRenderableWidget(searchBox);

        applyFilter();

        // ── Footer fixed buttons ────────────────────────────────────────────────
        // NOTE: per-row buttons (Téléporter / Détails per zone row) remain as drawBtn
        // approach because they are dynamically positioned per scroll row.
        // Footer-level fixed buttons are migrated to CartographiaButton.
        int fy = gy + GUI_H - FOOTER_H;
        boolean narrow = GUI_W < 540;

        // Conditional buttons — created based on current state but always registered;
        // we rebuild them every init() so their positions are always fresh.
        boolean canCreate = wandPos1 != null && wandPos2 != null;

        int cursor = gx + 8;
        if (selectedIndex >= 0) {
            teleportBtn = CartographiaButton.neutral(cursor, fy + 6, BTN_W_ACTION, 16,
                Component.translatable("arcadiaguard.gui.zonelist.btn_teleport"),
                b -> { if (selectedIndex >= 0 && selectedIndex < filteredZones.size())
                    PacketDistributor.sendToServer(GuiActionPayload.teleport(filteredZones.get(selectedIndex).name())); });
            addRenderableWidget(teleportBtn);
            cursor += BTN_W_ACTION + 8;

            detailsBtn = CartographiaButton.neutral(cursor, fy + 6, BTN_W_ACTION, 16,
                Component.translatable("arcadiaguard.gui.zonelist.btn_details"),
                b -> { if (selectedIndex >= 0 && selectedIndex < filteredZones.size())
                    PacketDistributor.sendToServer(GuiActionPayload.requestDetail(filteredZones.get(selectedIndex).name())); });
            addRenderableWidget(detailsBtn);
            cursor += BTN_W_ACTION + 8;

            // Bouton Supprimer la zone selectionnee (avec confirmation)
            deleteBtn = CartographiaButton.danger(cursor, fy + 6, BTN_W_ACTION, 16,
                Component.translatable("arcadiaguard.gui.zonelist.btn_delete"),
                b -> { if (selectedIndex >= 0 && selectedIndex < filteredZones.size()) openDeleteConfirm(); });
            addRenderableWidget(deleteBtn);
            cursor += BTN_W_ACTION + 8;
        } else {
            teleportBtn = null;
            detailsBtn  = null;
            deleteBtn   = null;
        }

        createBtn = CartographiaButton.accent(cursor, fy + 6, BTN_W_CREATE, 16,
            Component.translatable("arcadiaguard.gui.zonelist.btn_create"),
            b -> { if (wandPos1 != null && wandPos2 != null)
                minecraft.setScreen(new ZoneCreateScreen(this, wandPos1, wandPos2)); });
        createBtn.active = canCreate;
        addRenderableWidget(createBtn);

        if (totalPages > 1) {
            int navW  = 56;
            int navCx = gx + GUI_W / 2;
            int prevX = navCx - navW - 40;
            int nextX = navCx + 40;

            prevBtn = CartographiaButton.neutral(prevX, fy + 6, navW, 16,
                Component.translatable("arcadiaguard.gui.zonelist.btn_prev"),
                b -> { if (currentPage > 1) navigatePage(currentPage - 1); });
            prevBtn.active = currentPage > 1;
            addRenderableWidget(prevBtn);

            nextBtn = CartographiaButton.neutral(nextX, fy + 6, navW, 16,
                Component.translatable("arcadiaguard.gui.zonelist.btn_next"),
                b -> { if (currentPage < totalPages) navigatePage(currentPage + 1); });
            nextBtn.active = currentPage < totalPages;
            addRenderableWidget(nextBtn);
        } else {
            prevBtn = null;
            nextBtn = null;
        }

        if (!dimFilter.isEmpty()) {
            int dimBtnX = gx + GUI_W - 260;
            int dimBtnW = narrow ? 22 : 84;
            String dimBtnLabel = narrow ? "\u2699"
                : Component.translatable("arcadiaguard.gui.zonelist.btn_dim_flags").getString();
            dimFlagsBtn = CartographiaButton.neutral(dimBtnX, fy + 6, dimBtnW, 16,
                Component.literal(dimBtnLabel),
                b -> PacketDistributor.sendToServer(GuiActionPayload.requestDimDetail(dimFilter)));
            addRenderableWidget(dimFlagsBtn);
        } else {
            dimFlagsBtn = null;
        }

        int debugBtnX = gx + GUI_W - 168;
        int debugBtnW = narrow ? 36 : 92;
        String debugLabel = narrow ? (debugMode ? "ON" : "OFF")
            : (debugMode
                ? Component.translatable("arcadiaguard.gui.zonelist.debug_on").getString()
                : Component.translatable("arcadiaguard.gui.zonelist.debug_off").getString());
        debugBtn = CartographiaButton.neutral(debugBtnX, fy + 6, debugBtnW, 16,
            Component.literal(debugLabel),
            b -> { debugMode = !debugMode; PacketDistributor.sendToServer(GuiActionPayload.toggleDebug()); rebuildWidgets(); });
        addRenderableWidget(debugBtn);

        closeBtn = CartographiaButton.neutral(gx + GUI_W - 68, fy + 6, 60, 16,
            Component.translatable("arcadiaguard.gui.zonelist.btn_close"),
            b -> onClose());
        addRenderableWidget(closeBtn);
    }

    @Override
    public void renderBlurredBackground(float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xC0000000);
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, Colors.BG_0);
        GuiTextures.drawFrame(g, gx, gy, GUI_W, GUI_H);

        dimHits.clear();
        renderHeader(g);
        renderSidebar(g, mx, my);
        renderTable(g, mx, my);
        renderPreview(g, mx, my);
        renderFooter(g, mx, my);

        // H-U5: focus ring for non-bordered searchBox
        if (searchBox != null && getFocused() == searchBox) {
            int x = searchBox.getX(), y = searchBox.getY(), w = searchBox.getWidth(), h = searchBox.getHeight();
            g.fill(x - 1, y - 1, x + w + 1, y,         Colors.ACCENT_HI);
            g.fill(x - 1, y + h, x + w + 1, y + h + 1, Colors.ACCENT_HI);
            g.fill(x - 1, y,     x,          y + h,     Colors.ACCENT_HI);
            g.fill(x + w, y,     x + w + 1,  y + h,     Colors.ACCENT_HI);
        }

        super.render(g, mx, my, delta);
        renderDeleteConfirmOverlay(g, mx, my);
    }

    // ── Header ──────────────────────────────────────────────────────────────────

    private void renderHeader(GuiGraphics g) {
        int hx = gx + 8, hy = gy + 8;
        GuiTextures.icon(g, GuiTextures.ICO_SHIELD, hx, hy + 2);
        g.drawString(font, "ArcadiaGuard", hx + 20, hy + 1, Colors.ACCENT, false);
        g.drawString(font, Component.translatable("arcadiaguard.gui.zonelist.subtitle").getString(), hx + 20, hy + 11, Colors.TEXT_MUTE, false);

        // Header droit : [ DEBUG ACTIF ]  #X zones — positionnement pour eviter l'overlap.
        int rightEdge = gx + GUI_W - 8;
        if (debugMode) {
            int dbw = 90;
            int dbx = rightEdge - dbw;
            g.fill(dbx, hy, dbx + dbw, hy + 14, Colors.GOOD & 0xFFFFFF | 0x30000000);
            g.fill(dbx, hy, dbx + dbw, hy + 1, Colors.GOOD);
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonelist.debug_active").getString(), dbx + dbw / 2, hy + 4, Colors.GOOD);
            rightEdge = dbx - 10; // laisse 10px d'espace avant le compteur
        }

        int zoneCount = filteredZones.size();
        String zoneCountKey = "arcadiaguard.gui.zonelist.zone_count." + (zoneCount == 1 ? "one" : "other");
        String zoneCountText = Component.translatable(zoneCountKey, zoneCount).getString();
        int zoneCountW = font.width(zoneCountText);
        g.drawString(font, zoneCountText, rightEdge - zoneCountW, hy + 6, Colors.TEXT_MUTE, false);
        GuiTextures.dividerH(g, gx + 8, gy + HEADER_H, GUI_W - 16);
    }

    // ── Sidebar ─────────────────────────────────────────────────────────────────

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        int sx = gx + 8;
        int sy = gy + HEADER_H + 8;

        drawSectionLabel(g, sx, sy, Component.translatable("arcadiaguard.gui.zonelist.section_dimension").getString());
        sy += 12;

        // Scroll clippe pour la liste des dimensions (peut deborder si beaucoup de dim mods).
        int dimListTop = sy;
        int filtersH = 8 + 12 + 3 * 18; // gap + label + 3 filter rows
        int dimListBottom = gy + GUI_H - FOOTER_H - filtersH - 4;
        if (dimListBottom < dimListTop + 36) dimListBottom = dimListTop + 36; // min safety
        dimListAreaTop = dimListTop;
        dimListAreaBottom = dimListBottom;

        int totalRows = 4 + extraDims.size(); // all + OW + Nether + End + extras
        int contentH = totalRows * 18;
        int viewH = dimListBottom - dimListTop;
        int maxScroll = Math.max(0, contentH - viewH);
        sidebarScrollOffset = Mth.clamp(sidebarScrollOffset, 0, maxScroll);

        g.enableScissor(gx, dimListTop, gx + SIDEBAR_W, dimListBottom);
        int rowY = dimListTop - sidebarScrollOffset;
        rowY = renderDimRow(g, mx, my, sx, rowY, "", Component.translatable("arcadiaguard.gui.zonelist.dim_all").getString(), Colors.ACCENT, countDim(""), false);
        rowY = renderDimRow(g, mx, my, sx, rowY, "minecraft:overworld",  "Overworld", Colors.VERDIGRIS, countDim("minecraft:overworld"),  true);
        rowY = renderDimRow(g, mx, my, sx, rowY, "minecraft:the_nether", "Nether",    Colors.DANGER,    countDim("minecraft:the_nether"),   true);
        rowY = renderDimRow(g, mx, my, sx, rowY, "minecraft:the_end",    "The End",   Colors.ACCENT_HI, countDim("minecraft:the_end"),       true);
        for (String dim : extraDims) {
            rowY = renderDimRow(g, mx, my, sx, rowY, dim, shortDim(dim), Colors.TEXT_MUTE, countDim(dim), true);
        }
        g.disableScissor();

        // Indicateur scroll si overflow (barre verticale fine a droite).
        if (maxScroll > 0) {
            int barX = gx + SIDEBAR_W - 6;
            int barH = Math.max(12, viewH * viewH / contentH);
            int barY = dimListTop + (sidebarScrollOffset * (viewH - barH)) / maxScroll;
            g.fill(barX, dimListTop, barX + 2, dimListBottom, 0x30FFFFFF);
            g.fill(barX, barY, barX + 2, barY + barH, Colors.ACCENT_LO);
        }

        sy = dimListBottom + 8;
        drawSectionLabel(g, sx, sy, Component.translatable("arcadiaguard.gui.zonelist.section_filters").getString());
        sy += 12;
        int allCount    = allZones.size();
        int rootCount   = (int) allZones.stream().filter(z -> !z.hasParent()).count();
        int childCount  = allZones.size() - rootCount;
        filterAllRect   = filterRow(g, mx, my, sx, sy, ParentFilter.ALL,
            Component.translatable("arcadiaguard.gui.zonelist.filter_all").getString(), allCount);   sy += 18;
        filterRootRect  = filterRow(g, mx, my, sx, sy, ParentFilter.ROOT,
            Component.translatable("arcadiaguard.gui.zonelist.filter_root").getString(), rootCount); sy += 18;
        filterChildRect = filterRow(g, mx, my, sx, sy, ParentFilter.CHILDREN,
            Component.translatable("arcadiaguard.gui.zonelist.filter_children").getString(), childCount);

        GuiTextures.dividerV(g, gx + SIDEBAR_W, gy + HEADER_H, GUI_H - HEADER_H - FOOTER_H);
    }

    private int sidebarScrollOffset = 0;
    private int dimListAreaTop = 0;
    private int dimListAreaBottom = 0;

    private Rect filterRow(GuiGraphics g, int mx, int my, int sx, int sy,
                           ParentFilter filter, String label, int count) {
        boolean active = parentFilter == filter;
        boolean hov    = mx >= sx - 2 && mx < sx + SIDEBAR_W - 10 && my >= sy && my < sy + 16;
        if (active) g.fill(sx - 2, sy, sx + SIDEBAR_W - 10, sy + 16, Colors.accentTint(0x20));
        if (active) g.fill(sx - 2, sy, sx - 1, sy + 16, Colors.ACCENT);
        if (!active && hov) g.fill(sx - 2, sy, sx + SIDEBAR_W - 10, sy + 16, Colors.accentTint(0x10));
        g.drawString(font, label, sx + 4, sy + 4, active ? Colors.TEXT : Colors.TEXT_DIM, false);
        g.drawString(font, String.valueOf(count), sx + SIDEBAR_W - 22, sy + 4, Colors.TEXT_MUTE, false);
        return new Rect(sx - 2, sy, sx + SIDEBAR_W - 10, sy + 16);
    }

    private int renderDimRow(GuiGraphics g, int mx, int my, int sx, int sy, String dim, String label, int dotColor, int count, boolean hasSettings) {
        boolean active = dim.equals(dimFilter);
        boolean hov = mx >= sx - 2 && mx < sx + SIDEBAR_W - 10 && my >= sy && my < sy + 16;
        if (!active && hov) g.fill(sx - 2, sy, sx + SIDEBAR_W - 10, sy + 16, Colors.accentTint(0x10));
        if (active) g.fill(sx - 2, sy, sx + SIDEBAR_W - 10, sy + 16, Colors.accentTint(0x20));
        if (active) g.fill(sx - 2, sy, sx - 1, sy + 16, Colors.ACCENT);
        g.fill(sx + 2, sy + 5, sx + 8, sy + 11, dotColor);
        int labelMaxW = hasSettings ? SIDEBAR_W - 38 : SIDEBAR_W - 26;
        String labelTrunc = font.plainSubstrByWidth(label, labelMaxW);
        g.drawString(font, labelTrunc, sx + 12, sy + 4, active ? Colors.TEXT : Colors.TEXT_DIM, false);
        g.drawString(font, String.valueOf(count), sx + SIDEBAR_W - 22, sy + 4, Colors.TEXT_MUTE, false);

        Rect rowRect = new Rect(sx - 2, sy, sx + SIDEBAR_W - 10, sy + 16);
        Rect gearRect = null;
        if (hasSettings) {
            int gearX = sx + SIDEBAR_W - 36;
            boolean gearHov = mx >= gearX && mx < gearX + 12 && my >= sy && my < sy + 16;
            if (gearHov || active)
                g.drawString(font, "\u2699", gearX, sy + 4, gearHov ? Colors.ACCENT_HI : Colors.ACCENT_LO, false);
            gearRect = new Rect(gearX, sy, gearX + 12, sy + 16);
        }
        dimHits.add(new DimHit(dim, rowRect, gearRect));
        return sy + 18;
    }

    private void drawSectionLabel(GuiGraphics g, int x, int y, String text) {
        g.drawString(font, text.toUpperCase(), x, y, Colors.ACCENT, false);
    }

    private int countDim(String dim) {
        if (dim.isEmpty()) return allZones.size();
        return (int) allZones.stream().filter(z -> z.dim().equals(dim)).count();
    }

    // ── Table ────────────────────────────────────────────────────────────────────

    private void renderTable(GuiGraphics g, int mx, int my) {
        int tx = gx + SIDEBAR_W + 4;
        int tw = GUI_W - SIDEBAR_W - PREVIEW_W - 8;
        int ty = gy + HEADER_H + 4;

        g.fill(tx, ty, tx + tw, ty + 20, Colors.SLOT_BG);
        g.fill(tx, ty, tx + tw, ty + 1, Colors.LINE_STRONG);
        g.fill(tx, ty + 19, tx + tw, ty + 20, Colors.LINE_STRONG);
        GuiTextures.icon(g, GuiTextures.ICO_SEARCH, tx + 3, ty + 2);
        ty += 24;

        g.fill(tx, ty, tx + tw, ty + 14, Colors.BG_2);
        String[] colH = {
            "",
            Component.translatable("arcadiaguard.gui.zonelist.col_zone").getString(),
            Component.translatable("arcadiaguard.gui.zonelist.col_tags").getString(),
            Component.translatable("arcadiaguard.gui.zonelist.col_coords").getString(),
            Component.translatable("arcadiaguard.gui.zonelist.col_size").getString(),
            Component.translatable("arcadiaguard.gui.zonelist.col_status").getString()
        };
        for (int i = 1; i < colH.length; i++)
            g.drawString(font, colH[i], zoneColX[i], ty + 3, Colors.TEXT_MUTE, false);
        GuiTextures.dividerH(g, tx, ty + 14, tw);
        ty += 14;

        int listH  = GUI_H - HEADER_H - FOOTER_H - 60;
        int maxVis = listH / ITEM_H;

        renderZoneRows(g, mx, my, tx, tw, ty, maxVis);

        int total = filteredZones.size();
        if (total > maxVis) {
            int trackX = tx + tw - 5;
            int trackH = listH;
            g.fill(trackX, ty, trackX + 4, ty + trackH, Colors.BG_0);
            int thumbH = Math.max(16, trackH * maxVis / total);
            int thumbY = ty + (trackH - thumbH) * scrollOffset / Math.max(1, total - maxVis);
            g.fill(trackX + 1, thumbY, trackX + 3, thumbY + thumbH, Colors.ACCENT_LO);
        }

        GuiTextures.dividerV(g, gx + GUI_W - PREVIEW_W - 1, gy + HEADER_H, GUI_H - HEADER_H - FOOTER_H);
    }

    private void renderZoneRows(GuiGraphics g, int mx, int my, int tx, int tw, int ty, int maxVis) {
        int end = Math.min(scrollOffset + maxVis + 1, filteredZones.size());
        int nameMaxW = zoneColX[2] - zoneColX[1] - 4;
        for (int i = scrollOffset; i < end; i++) {
            ZoneEntry z = filteredZones.get(i);
            int iy = ty + (i - scrollOffset) * ITEM_H;
            boolean selected = i == selectedIndex;
            boolean hov = mx >= tx && mx < tx + tw && my >= iy && my < iy + ITEM_H;

            if (selected) g.fill(tx, iy, tx + tw, iy + ITEM_H, Colors.accentTint(0x20));
            else if (hov)  g.fill(tx, iy, tx + tw, iy + ITEM_H, Colors.accentTint(0x10));
            else if (i % 2 == 1) g.fill(tx, iy, tx + tw, iy + ITEM_H, 0x08FFFFFF);
            if (selected) g.fill(tx, iy, tx + 2, iy + ITEM_H, Colors.ACCENT);

            GuiTextures.statusDot(g, z.enabled() ? 0 : 24, zoneColX[0] + 2, iy + 9);

            String nameLabel = font.plainSubstrByWidth(z.name(), nameMaxW);
            if (nameLabel.length() < z.name().length()) nameLabel += "…";
            int nameColor = z.enabled() ? (selected ? Colors.ACCENT_HI : Colors.TEXT) : Colors.TEXT_MUTE;
            g.drawString(font, nameLabel, zoneColX[1], iy + 4, nameColor, false);

            String dimShort = shortDim(z.dim());
            int dimColor = dimColor(z.dim());
            g.fill(zoneColX[1], iy + 14, zoneColX[1] + font.width(dimShort) + 6, iy + 22, dimColor & 0x00FFFFFF | 0x1A000000);
            g.drawString(font, dimShort, zoneColX[1] + 3, iy + 15, dimColor, false);

            if (z.hasParent()) {
                String subzoneLabel = Component.translatable("arcadiaguard.gui.zonelist.tag_subzone").getString();
                g.fill(zoneColX[2], iy + 6, zoneColX[2] + 50, iy + 18, Colors.VERDIM);
                g.drawString(font, subzoneLabel, zoneColX[2] + 2, iy + 8, Colors.VERDIGRIS, false);
            }

            int cx = (z.minX() + z.maxX()) / 2;
            int cz = (z.minZ() + z.maxZ()) / 2;
            g.drawString(font, cx + ", " + cz, zoneColX[3], iy + 9, Colors.TEXT_MUTE, false);
            g.drawString(font, (z.maxX()-z.minX()+1) + "×" + (z.maxZ()-z.minZ()+1), zoneColX[4], iy + 9, Colors.TEXT_DIM, false);
            g.drawString(font, z.memberCount() + " mb", zoneColX[5], iy + 9, Colors.TEXT_MUTE, false);
            GuiTextures.dividerH(g, tx, iy + ITEM_H - 1, tw);
        }
        if (filteredZones.isEmpty())
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonelist.no_zones").getString(), tx + tw / 2, ty + 40, Colors.TEXT_MUTE);
    }

    // ── Preview ──────────────────────────────────────────────────────────────────

    private void renderPreview(GuiGraphics g, int mx, int my) {
        int px = gx + GUI_W - PREVIEW_W + 4;
        int py = gy + HEADER_H + 8;

        drawSectionLabel(g, px, py, Component.translatable("arcadiaguard.gui.zonelist.section_preview").getString());
        py += 12;

        if (selectedIndex < 0 || selectedIndex >= filteredZones.size()) {
            g.drawCenteredString(font, Component.translatable("arcadiaguard.gui.zonelist.preview_hint").getString(), px + (PREVIEW_W - 8) / 2, py + 40, Colors.TEXT_MUTE);
            return;
        }
        renderZonePreview(g, px, py, filteredZones.get(selectedIndex));
    }

    private void renderZonePreview(GuiGraphics g, int px, int py, ZoneEntry z) {
        int statusColor = z.enabled() ? Colors.GOOD : Colors.DANGER;
        String statusLabel = z.enabled()
            ? Component.translatable("arcadiaguard.gui.zonelist.status_active").getString()
            : Component.translatable("arcadiaguard.gui.zonelist.status_inactive").getString();
        g.drawString(font, statusLabel, px, py, statusColor, false);
        py += 10;

        g.drawString(font, z.name(), px, py, Colors.TEXT, false);          py += 14;
        g.drawString(font, shortDim(z.dim()), px, py, dimColor(z.dim()), false); py += 18;
        GuiTextures.dividerH(g, px - 4, py, PREVIEW_W - 4);               py += 6;

        int dx = z.maxX() - z.minX() + 1;
        int dy = z.maxY() - z.minY() + 1;
        int dz = z.maxZ() - z.minZ() + 1;
        long vol = (long) dx * dy * dz;

        drawStatLine(g, px, py, Component.translatable("arcadiaguard.gui.zonelist.stat_surface").getString(), dx + "×" + dz);     py += 14;
        drawStatLine(g, px, py, Component.translatable("arcadiaguard.gui.zonelist.stat_volume").getString(),  formatVol(vol));     py += 14;
        drawStatLine(g, px, py, Component.translatable("arcadiaguard.gui.zonelist.stat_chunks").getString(),  (((dx+15)/16)*((dz+15)/16)) + " ch"); py += 14;
        drawStatLine(g, px, py, Component.translatable("arcadiaguard.gui.zonelist.stat_members").getString(), String.valueOf(z.memberCount())); py += 14;
        int flagCount = z.flagCount();
        String flagKey = "arcadiaguard.gui.zonelist.stat_flags." + (flagCount == 1 ? "one" : "other");
        drawStatLine(g, px, py, Component.translatable("arcadiaguard.gui.zonelist.stat_flags_label").getString(),
            Component.translatable(flagKey, flagCount).getString()); py += 14;
        if (z.hasParent()) { drawStatLine(g, px, py, Component.translatable("arcadiaguard.gui.zonelist.stat_type").getString(),
            Component.translatable("arcadiaguard.gui.zonelist.type_subzone").getString()); py += 14; }

        py += 4;
        GuiTextures.dividerH(g, px - 4, py, PREVIEW_W - 4);     py += 6;
        g.drawString(font, "A " + z.minX() + "," + z.minY() + "," + z.minZ(), px, py, Colors.TEXT_MUTE, false); py += 10;
        g.drawString(font, "B " + z.maxX() + "," + z.maxY() + "," + z.maxZ(), px, py, Colors.TEXT_MUTE, false);
    }

    private void drawStatLine(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, label, x, y, Colors.TEXT_MUTE, false);
        g.drawString(font, value, x + PREVIEW_W - 10 - font.width(value), y, Colors.TEXT, false);
    }

    // ── Footer ───────────────────────────────────────────────────────────────────

    private static final int BTN_W_ACTION = 90;  // Téléporter / Détails
    private static final int BTN_W_CREATE = 100; // + Créer zone

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int fy = gy + GUI_H - FOOTER_H;
        GuiTextures.dividerH(g, gx + 8, fy, GUI_W - 16);

        boolean canCreate = wandPos1 != null && wandPos2 != null;

        // Compute cursor position after conditional left buttons for wand message.
        // Inclut les 3 boutons Teleport/Details/Delete quand une zone est selectionnee.
        int cursor = gx + 8;
        if (selectedIndex >= 0) cursor += (BTN_W_ACTION + 8) * 3;
        cursor += BTN_W_CREATE + 8;

        // Message a droite des boutons — UNIQUEMENT le hint 'need wand' quand la selection
        // wand est incomplete (les coords pos1/pos2 sont deja affichees par WandHudRenderer
        // en HUD permanent, pas la peine de les dupliquer ici).
        int rightLimit = !dimFilter.isEmpty() ? (gx + GUI_W - 268) : (gx + GUI_W - 176);
        int maxTextW = rightLimit - cursor;
        if (maxTextW > 0 && !canCreate) {
            String msg = Component.translatable("arcadiaguard.gui.zonelist.need_wand").getString();
            g.drawString(font, font.plainSubstrByWidth(msg, maxTextW), cursor, fy + 10, Colors.TEXT_MUTE, false);
        }

        // Centre: page label (boutons prev/next are widgets)
        if (totalPages > 1) {
            int navCx = gx + GUI_W / 2;
            String pageLabel = currentPage + " / " + totalPages;
            g.drawCenteredString(font, pageLabel, navCx, fy + 10, Colors.TEXT_MUTE);
        }

        // Boutons footer (CartographiaButton) are rendered by super.render() — nothing more needed here.
    }

    // ── Interactions ─────────────────────────────────────────────────────────────

    /** Timestamp + index pour detecter un double-clic sur une zone de la table. */
    private long lastClickAt = 0;
    private int  lastClickIdx = -1;

    /** Popup overlay de confirmation suppression (meme style que ZoneDetailScreen). */
    private boolean confirmDeleteOpen = false;

    private void openDeleteConfirm() {
        if (selectedIndex < 0 || selectedIndex >= filteredZones.size()) return;
        confirmDeleteOpen = true;
    }

    private void renderDeleteConfirmOverlay(GuiGraphics g, int mx, int my) {
        if (!confirmDeleteOpen || selectedIndex < 0 || selectedIndex >= filteredZones.size()) return;
        String zoneName = filteredZones.get(selectedIndex).name();
        g.fill(gx, gy, gx + GUI_W, gy + GUI_H, 0x80000000);

        // Popup plus large pour accueillir les noms de zone longs.
        int pw = Math.min(360, GUI_W - 20);
        int ph = 92;
        int px = gx + (GUI_W - pw) / 2;
        int py = gy + (GUI_H - ph) / 2;

        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, Colors.DANGER & 0xFFFFFF | 0xFF000000);
        g.fill(px, py, px + pw, py + ph, Colors.BG_1);

        g.drawCenteredString(font,
            Component.translatable("arcadiaguard.gui.zonelist.confirm_delete.title").getString(),
            px + pw / 2, py + 10, Colors.TEXT);
        g.drawCenteredString(font,
            Component.translatable("arcadiaguard.gui.zonelist.confirm_delete.message", zoneName).getString(),
            px + pw / 2, py + 22, Colors.TEXT_MUTE);
        g.drawCenteredString(font,
            Component.translatable("arcadiaguard.gui.zonedetail.confirm_delete_warn2").getString(),
            px + pw / 2, py + 33, Colors.TEXT_MUTE);

        // Bouton Annuler (gauche)
        int cancelX = px + 10, btY = py + 54;
        boolean cancelHov = mx >= cancelX && mx < cancelX + 100 && my >= btY && my < btY + 22;
        g.fill(cancelX, btY, cancelX + 100, btY + 22, cancelHov ? Colors.accentTint(0x40) : Colors.BG_2);
        g.drawCenteredString(font,
            Component.translatable("arcadiaguard.gui.zonelist.confirm_delete.no").getString(),
            cancelX + 50, btY + 7, Colors.TEXT_DIM);

        // Bouton Supprimer (droite)
        int okX = px + pw - 110;
        boolean okHov = mx >= okX && mx < okX + 100 && my >= btY && my < btY + 22;
        g.fill(okX, btY, okX + 100, btY + 22, okHov ? Colors.DANGER & 0xFFFFFF | 0x40000000 : Colors.BG_2);
        g.drawCenteredString(font,
            Component.translatable("arcadiaguard.gui.zonelist.confirm_delete.yes").getString(),
            okX + 50, btY + 7, Colors.DANGER);
    }

    private boolean handleDeleteConfirmClick(int imx, int imy) {
        if (!confirmDeleteOpen || selectedIndex < 0 || selectedIndex >= filteredZones.size()) return false;
        int pw = Math.min(360, GUI_W - 20), ph = 92;
        int px = gx + (GUI_W - pw) / 2, py = gy + (GUI_H - ph) / 2;
        int btY = py + 54;
        if (imx >= px + 10 && imx < px + 110 && imy >= btY && imy < btY + 22) {
            confirmDeleteOpen = false; return true;
        }
        if (imx >= px + pw - 110 && imx < px + pw - 10 && imy >= btY && imy < btY + 22) {
            String zoneName = filteredZones.get(selectedIndex).name();
            PacketDistributor.sendToServer(GuiActionPayload.deleteZone(zoneName));
            selectedIndex = -1;
            confirmDeleteOpen = false;
            return true;
        }
        // Click en dehors ignore mais on bloque la propagation (overlay modal)
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // L'overlay delete-confirm est modal : intercepte tous les clics en priorite.
        if (confirmDeleteOpen) return handleDeleteConfirmClick((int) mx, (int) my);
        if (super.mouseClicked(mx, my, button)) return true;
        int imx = (int) mx, imy = (int) my;

        // Sidebar — dim rows (gear takes priority over row). Filtre les clics HORS de l'area
        // scrollable car les rects existent meme pour les rows masques par scissor.
        if (imy >= dimListAreaTop && imy < dimListAreaBottom) {
            for (DimHit dh : dimHits) {
                if (dh.gear != null && dh.gear.hit(imx, imy)) {
                    if (!dh.dim.isEmpty()) {
                        PacketDistributor.sendToServer(GuiActionPayload.requestDimDetail(dh.dim));
                    }
                    return true;
                }
                if (dh.row.hit(imx, imy)) {
                    dimFilter = dh.dim;
                    scrollOffset = 0;
                    selectedIndex = -1;
                    rebuildWidgets();
                    return true;
                }
            }
        }

        // Sidebar — parent filter rows
        if (filterAllRect != null && filterAllRect.hit(imx, imy)) {
            parentFilter = ParentFilter.ALL; scrollOffset = 0; selectedIndex = -1; rebuildWidgets(); return true;
        }
        if (filterRootRect != null && filterRootRect.hit(imx, imy)) {
            parentFilter = ParentFilter.ROOT; scrollOffset = 0; selectedIndex = -1; rebuildWidgets(); return true;
        }
        if (filterChildRect != null && filterChildRect.hit(imx, imy)) {
            parentFilter = ParentFilter.CHILDREN; scrollOffset = 0; selectedIndex = -1; rebuildWidgets(); return true;
        }

        // Table — sélection
        int tx = gx + SIDEBAR_W + 4;
        int tw = GUI_W - SIDEBAR_W - PREVIEW_W - 8;
        int ty = gy + HEADER_H + 4 + 24 + 14;

        if (imx >= tx && imx < tx + tw - 5 && imy >= ty) {
            int idx = (imy - ty) / ITEM_H + scrollOffset;
            if (idx >= 0 && idx < filteredZones.size()) {
                int prevSelected = selectedIndex;
                selectedIndex = idx;
                // Double-clic sur la meme zone -> ouvre le detail
                long now = System.currentTimeMillis();
                if (idx == lastClickIdx && now - lastClickAt < 400) {
                    PacketDistributor.sendToServer(GuiActionPayload.requestDetail(filteredZones.get(idx).name()));
                    lastClickAt = 0; lastClickIdx = -1;
                    return true;
                }
                lastClickAt = now;
                lastClickIdx = idx;
                if ((prevSelected < 0) != (selectedIndex < 0) || prevSelected != selectedIndex) rebuildWidgets();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (suppressNextFChar && (c == 'f' || c == 'F')) {
            suppressNextFChar = false;
            return true;
        }
        suppressNextFChar = false;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc ferme l'overlay de confirmation en priorite
        if (keyCode == 256 && confirmDeleteOpen) { confirmDeleteOpen = false; return true; }
        // H-U4: Esc unfocuses EditBox
        if (keyCode == 256 && getFocused() instanceof EditBox eb) {
            eb.setFocused(false);
            setFocused(null);
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        // F / Ctrl+F → focus search box. Ignore les combinaisons avec Alt/Shift
        // pour laisser passer d'éventuels autres raccourcis (AZERTY, mods tiers).
        if (keyCode == 70 && searchBox != null && !hasAltDown() && !hasShiftDown()) {
            setFocused(searchBox);
            searchBox.setFocused(true);
            // Consomme le prochain charTyped('f'/'F') pour que la lettre ne soit pas
            // tapée dans la barre de recherche juste après le focus.
            suppressNextFChar = true;
            return true;
        }
        // Enter / Numpad Enter → open detail for selected zone
        if ((keyCode == 257 || keyCode == 335) && selectedIndex >= 0
                && selectedIndex < filteredZones.size()) {
            PacketDistributor.sendToServer(
                GuiActionPayload.requestDetail(filteredZones.get(selectedIndex).name()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (super.mouseScrolled(mx, my, dx, dy)) return true;
        // Si le curseur est dans la liste des dimensions (sidebar), scroll la sidebar.
        if (mx >= gx && mx < gx + SIDEBAR_W && my >= dimListAreaTop && my < dimListAreaBottom) {
            sidebarScrollOffset = Math.max(0, sidebarScrollOffset - (int)(dy * 18));
            return true;
        }
        int listH  = GUI_H - HEADER_H - FOOTER_H - 60;
        int maxVis = listH / ITEM_H;
        int total  = filteredZones.size();
        scrollOffset = Mth.clamp((int)(scrollOffset - dy), 0, Math.max(0, total - maxVis));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Utilitaires ──────────────────────────────────────────────────────────────

    private void navigatePage(int page) {
        if (pageCache.containsKey(page)) {
            currentPage = page;
            allZones.clear();
            allZones.addAll(pageCache.get(page));
            scrollOffset  = 0;
            selectedIndex = -1;
            applyFilter();
        } else {
            PacketDistributor.sendToServer(GuiActionPayload.requestPage(page));
        }
    }

    private void applyFilter() {
        // Extra dims from mod zones
        SequencedSet<String> modDimSet = new LinkedHashSet<>();
        for (ZoneEntry z : allZones) {
            String d = z.dim();
            if (!d.equals("minecraft:overworld") && !d.equals("minecraft:the_nether") && !d.equals("minecraft:the_end"))
                modDimSet.add(d);
        }
        extraDims = new ArrayList<>(modDimSet);

        // N.B. dimFilter is NOT auto-reset when empty: users must be able to click
        // a vanilla dim tab even if no zone exists there yet, in order to access
        // the dimension-wide flag settings via the gear icon or footer button.

        int prevSel = selectedIndex;
        filteredZones = allZones.stream()
            .filter(z -> dimFilter.isEmpty() || z.dim().equals(dimFilter))
            .filter(z -> switch (parentFilter) {
                case ALL      -> true;
                case ROOT     -> !z.hasParent();
                case CHILDREN -> z.hasParent();
            })
            .filter(z -> searchText.isEmpty()
                || z.name().toLowerCase().contains(searchText.toLowerCase())
                || z.dim().toLowerCase().contains(searchText.toLowerCase()))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (selectedIndex >= filteredZones.size()) selectedIndex = -1;
        // Rebuild footer buttons if selection state changed (affects Téléporter/Détails visibility)
        if (minecraft != null && minecraft.screen == this && (prevSel < 0) != (selectedIndex < 0)) rebuildWidgets();
    }

    public static String shortDim(String dim) {
        return switch (dim) {
            case "minecraft:overworld"  -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end"    -> "The End";
            default -> dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        };
    }

    private static int dimColor(String dim) {
        return switch (dim) {
            case "minecraft:overworld"  -> Colors.VERDIGRIS;
            case "minecraft:the_nether" -> Colors.DANGER;
            case "minecraft:the_end"    -> Colors.ACCENT_HI;
            default -> Colors.TEXT_MUTE;
        };
    }

    private static String formatVol(long vol) {
        if (vol >= 1_000_000) return (vol / 1_000_000) + "M bl";
        if (vol >= 1_000)     return (vol / 1_000)     + "k bl";
        return vol + " bl";
    }
}
