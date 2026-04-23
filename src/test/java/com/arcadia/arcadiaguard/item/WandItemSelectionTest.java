package com.arcadia.arcadiaguard.item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Couvre FR09 : sélection pos1/pos2 par le wand ArcadiaGuard.
 * WandItem stocke les positions côté serveur dans deux ConcurrentHashMap statiques,
 * indexées par UUID joueur. Aucune dépendance NeoForge registry — testable en pur JUnit.
 */
class WandItemSelectionTest {

    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @AfterEach
    void cleanup() {
        WandItem.clearSelection(P1);
        WandItem.clearSelection(P2);
    }

    // ── getPos avant set ──────────────────────────────────────────────────────

    @Test
    void getPos1_beforeSet_returnsNull() {
        assertNull(WandItem.getPos1(P1));
    }

    @Test
    void getPos2_beforeSet_returnsNull() {
        assertNull(WandItem.getPos2(P1));
    }

    // ── setPos1 / getPos1 ─────────────────────────────────────────────────────

    @Test
    void setPos1_thenGet_returnsSamePos() {
        BlockPos pos = new BlockPos(10, 64, -20);
        WandItem.setPos1(P1, pos);
        assertEquals(pos, WandItem.getPos1(P1));
    }

    @Test
    void setPos2_thenGet_returnsSamePos() {
        BlockPos pos = new BlockPos(50, 128, 30);
        WandItem.setPos2(P1, pos);
        assertEquals(pos, WandItem.getPos2(P1));
    }

    @Test
    void setPos1_overwrite_returnsNewPos() {
        WandItem.setPos1(P1, new BlockPos(1, 1, 1));
        BlockPos newer = new BlockPos(42, 64, -100);
        WandItem.setPos1(P1, newer);
        assertEquals(newer, WandItem.getPos1(P1));
    }

    @Test
    void setPos2_overwrite_returnsNewPos() {
        WandItem.setPos2(P1, new BlockPos(9, 9, 9));
        BlockPos newer = new BlockPos(0, 100, 200);
        WandItem.setPos2(P1, newer);
        assertEquals(newer, WandItem.getPos2(P1));
    }

    // ── clearSelection ────────────────────────────────────────────────────────

    @Test
    void clearSelection_removesPos1AndPos2() {
        WandItem.setPos1(P1, new BlockPos(1, 1, 1));
        WandItem.setPos2(P1, new BlockPos(2, 2, 2));
        WandItem.clearSelection(P1);
        assertNull(WandItem.getPos1(P1));
        assertNull(WandItem.getPos2(P1));
    }

    @Test
    void clearUnknownPlayer_doesNotThrow() {
        UUID unknown = UUID.randomUUID();
        assertDoesNotThrow(() -> WandItem.clearSelection(unknown));
    }

    // ── Isolation entre joueurs ───────────────────────────────────────────────

    @Test
    void twoPlayers_pos1_doNotInterfere() {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(99, 99, 99);
        WandItem.setPos1(P1, a);
        WandItem.setPos1(P2, b);
        assertEquals(a, WandItem.getPos1(P1));
        assertEquals(b, WandItem.getPos1(P2));
    }

    @Test
    void clearSelection_onlyAffectsSpecifiedPlayer() {
        WandItem.setPos1(P1, new BlockPos(5, 5, 5));
        WandItem.setPos1(P2, new BlockPos(7, 7, 7));
        WandItem.clearSelection(P1);
        assertNull(WandItem.getPos1(P1));
        assertEquals(new BlockPos(7, 7, 7), WandItem.getPos1(P2));
    }

    @Test
    void pos1AndPos2_independentPerPlayer() {
        BlockPos p = new BlockPos(0, 64, 0);
        BlockPos q = new BlockPos(100, 100, 100);
        WandItem.setPos1(P1, p);
        WandItem.setPos2(P1, q);
        assertEquals(p, WandItem.getPos1(P1));
        assertEquals(q, WandItem.getPos2(P1));
    }

    @Test
    void setPos1_doesNotAffectPos2() {
        WandItem.setPos1(P1, new BlockPos(3, 3, 3));
        assertNull(WandItem.getPos2(P1));
    }

    @Test
    void setPos2_doesNotAffectPos1() {
        WandItem.setPos2(P1, new BlockPos(5, 5, 5));
        assertNull(WandItem.getPos1(P1));
    }

    // ── Coordonnées limites ───────────────────────────────────────────────────

    @Test
    void setPos1_extremeCoordinates_storedCorrectly() {
        BlockPos extreme = new BlockPos(Integer.MAX_VALUE, 0, Integer.MIN_VALUE);
        WandItem.setPos1(P1, extreme);
        assertEquals(extreme, WandItem.getPos1(P1));
    }

    @Test
    void setPos1_negativeY_storedCorrectly() {
        BlockPos pos = new BlockPos(0, -64, 0);
        WandItem.setPos1(P1, pos);
        assertEquals(pos, WandItem.getPos1(P1));
    }
}
