package com.arcadia.arcadiaguard.handler;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste la logique de correspondance mob-spawn-list partagée entre
 * FinalizeSpawnEvent et EntityJoinLevelEvent (filet addFreshEntity).
 */
class MobSpawnListMatchTest {

    private static boolean match(List<String> list, String id) throws Exception {
        Method m = EntityEventHandler.class.getDeclaredMethod("matchesMobList", List.class, ResourceLocation.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, list, ResourceLocation.parse(id));
    }

    @Test
    void exactMatch() throws Exception {
        assertTrue(match(List.of("minecraft:zombie"), "minecraft:zombie"));
    }

    @Test
    void exactNoMatch() throws Exception {
        assertFalse(match(List.of("minecraft:creeper"), "minecraft:zombie"));
    }

    @Test
    void namespaceWildcard() throws Exception {
        assertTrue(match(List.of("hominid:*"), "hominid:juggernaut"));
        assertTrue(match(List.of("hominid:*"), "hominid:bellman"));
        assertFalse(match(List.of("hominid:*"), "minecraft:zombie"));
    }

    @Test
    void pathWildcard() throws Exception {
        assertTrue(match(List.of("*:zombie"), "minecraft:zombie"));
        assertFalse(match(List.of("*:zombie"), "minecraft:creeper"));
    }

    @Test
    void fullWildcard() throws Exception {
        assertTrue(match(List.of("*:*"), "hominid:juggernaut"));
        assertTrue(match(List.of("*:*"), "minecraft:zombie"));
    }

    @Test
    void pathOnlyDefaultsToMinecraft() throws Exception {
        assertTrue(match(List.of("zombie"), "minecraft:zombie"));
        assertFalse(match(List.of("zombie"), "hominid:zombie"));
    }

    @Test
    void multipleEntries() throws Exception {
        assertTrue(match(List.of("minecraft:creeper", "hominid:juggernaut"), "hominid:juggernaut"));
        assertFalse(match(List.of("minecraft:creeper", "hominid:bellman"), "hominid:juggernaut"));
    }

    @Test
    void caseInsensitive() throws Exception {
        assertTrue(match(List.of("Minecraft:Zombie"), "minecraft:zombie"));
        assertTrue(match(List.of("HOMINID:*"), "hominid:juggernaut"));
    }

    @Test
    void emptyListNeverMatches() throws Exception {
        assertFalse(match(List.of(), "minecraft:zombie"));
    }

    @Test
    void nullAndBlankEntriesIgnored() throws Exception {
        assertFalse(match(List.of("", "  "), "minecraft:zombie"));
    }
}
