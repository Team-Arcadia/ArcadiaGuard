package com.arcadia.arcadiaguard.helper;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlagMixinHelperTest {

    @AfterEach
    void cleanup() {
        FlagMixinHelper.invalidateAll();
    }

    // ─── Null-safety ─────────────────────────────────────────────────────────────

    @Test
    void isDenied_nullLevel_returnsFalse() {
        assertFalse(FlagMixinHelper.isDenied(null, null, BuiltinFlags.VINE_GROWTH));
    }

    @Test
    void hasAnyZoneInDim_nullLevel_returnsFalse() {
        assertFalse(FlagMixinHelper.hasAnyZoneInDim((Level) null));
    }

    @Test
    void invalidateHasZoneCache_nullKey_clearsAll() {
        assertDoesNotThrow(() -> FlagMixinHelper.invalidateHasZoneCache(null));
    }

    @Test
    void invalidateHasZoneCache_specificKey_doesNotThrow() {
        assertDoesNotThrow(() -> FlagMixinHelper.invalidateHasZoneCache("minecraft:overworld"));
    }

    @Test
    void invalidateAll_doesNotThrow() {
        assertDoesNotThrow(() -> FlagMixinHelper.invalidateAll());
    }

    // ─── Client-side fast return ─────────────────────────────────────────────────

    @Test
    void isDenied_clientSide_returnsFalseWithoutCallingGuard() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        BlockPos pos = new BlockPos(0, 0, 0);

        try (MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            GuardService guard = mock(GuardService.class);
            ag.when(ArcadiaGuard::guardService).thenReturn(guard);

            assertFalse(FlagMixinHelper.isDenied(level, pos, BuiltinFlags.VINE_GROWTH));
            verify(guard, never()).isZoneDenying(any(Level.class), any(BlockPos.class), any());
        }
    }

    // ─── GuardService absent (startup) ────────────────────────────────────────────

    @Test
    void isDenied_guardServiceNull_returnsFalse() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        BlockPos pos = new BlockPos(0, 0, 0);

        try (MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            ag.when(ArcadiaGuard::guardService).thenReturn(null);
            assertFalse(FlagMixinHelper.isDenied(level, pos, BuiltinFlags.VINE_GROWTH));
        }
    }

    // ─── Logique métier : VINE_GROWTH denied ─────────────────────────────────────

    @Test
    void isDenied_vineGrowthDeniedInZone_returnsTrue() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        BlockPos pos = new BlockPos(10, 64, 10);

        try (MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            GuardService guard = mock(GuardService.class);
            when(guard.isZoneDenying(level, pos, BuiltinFlags.VINE_GROWTH)).thenReturn(true);
            ag.when(ArcadiaGuard::guardService).thenReturn(guard);

            assertTrue(FlagMixinHelper.isDenied(level, pos, BuiltinFlags.VINE_GROWTH));
            verify(guard).isZoneDenying(level, pos, BuiltinFlags.VINE_GROWTH);
        }
    }

    @Test
    void isDenied_vineGrowthAllowed_returnsFalse() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        BlockPos pos = new BlockPos(10, 64, 10);

        try (MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            GuardService guard = mock(GuardService.class);
            when(guard.isZoneDenying(level, pos, BuiltinFlags.VINE_GROWTH)).thenReturn(false);
            ag.when(ArcadiaGuard::guardService).thenReturn(guard);

            assertFalse(FlagMixinHelper.isDenied(level, pos, BuiltinFlags.VINE_GROWTH));
        }
    }

    // ─── isDenied : exception propre interne ne propage pas ──────────────────────

    @Test
    void isDenied_guardThrows_returnsFalseInsteadOfPropagating() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        BlockPos pos = new BlockPos(0, 0, 0);

        try (MockedStatic<ArcadiaGuard> ag = mockStatic(ArcadiaGuard.class)) {
            GuardService guard = mock(GuardService.class);
            when(guard.isZoneDenying(any(Level.class), any(BlockPos.class), any()))
                .thenThrow(new RuntimeException("synthetic"));
            ag.when(ArcadiaGuard::guardService).thenReturn(guard);

            assertFalse(FlagMixinHelper.isDenied(level, pos, BuiltinFlags.VINE_GROWTH));
        }
    }
}
