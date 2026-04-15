package com.arcadia.arcadiaguard.tag;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ArcadiaGuardTags {

    /**
     * Items banned from use in protected spawn dimensions.
     * Default: the four vanilla book types known to enable duplication exploits.
     * Override via datapack: data/arcadiaguard/tags/item/spawn_banned_books.json
     */
    public static final TagKey<Item> SPAWN_BANNED_BOOKS =
        ItemTags.create(ResourceLocation.fromNamespaceAndPath("arcadiaguard", "spawn_banned_books"));

    private ArcadiaGuardTags() {}
}
