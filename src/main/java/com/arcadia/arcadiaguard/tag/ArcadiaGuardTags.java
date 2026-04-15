package com.arcadia.arcadiaguard.tag;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ArcadiaGuardTags {

    /** Vanilla books + enchanted/writable variants. Override via datapack. */
    public static final TagKey<Item> SPAWN_BANNED_BOOKS =
        ItemTags.create(ResourceLocation.fromNamespaceAndPath("arcadiaguard", "spawn_banned_books"));

    /** Vanilla lead + Apothic Enchanting ender leads. Override via datapack. */
    public static final TagKey<Item> BANNED_LEADS =
        ItemTags.create(ResourceLocation.fromNamespaceAndPath("arcadiaguard", "banned_leads"));

    /** All vanilla spawn eggs (#minecraft:spawn_eggs). Override via datapack. */
    public static final TagKey<Item> BANNED_SPAWN_EGGS =
        ItemTags.create(ResourceLocation.fromNamespaceAndPath("arcadiaguard", "banned_spawn_eggs"));

    private ArcadiaGuardTags() {}
}
