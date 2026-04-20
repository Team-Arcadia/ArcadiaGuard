package com.arcadia.arcadiaguard.flag;

import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.flag.FlagRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal implementation of {@link FlagRegistry}. Populated at FMLCommonSetupEvent. */
public final class FlagRegistryImpl implements FlagRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlagRegistryImpl.class);

    private final Map<String, Flag<?>> flags = new LinkedHashMap<>();

    @Override
    public void register(Flag<?> flag) {
        if (this.flags.containsKey(flag.id())) {
            LOGGER.error("[ArcadiaGuard] Flag ID collision — '{}' is already registered. The second registration is ignored. Check for duplicate flag IDs across mods.", flag.id());
            return;
        }
        this.flags.put(flag.id(), flag);
    }

    @Override
    public Optional<Flag<?>> get(String id) {
        return Optional.ofNullable(this.flags.get(id));
    }

    @Override
    public Collection<Flag<?>> all() {
        return Collections.unmodifiableCollection(this.flags.values());
    }

    /** Registers all built-in flags. Called during ArcadiaGuard initialization. */
    public void registerBuiltins() {
        // Blocks
        register(BuiltinFlags.BLOCK_BREAK);
        register(BuiltinFlags.BLOCK_PLACE);
        register(BuiltinFlags.BLOCK_INTERACT);
        register(BuiltinFlags.CONTAINER_ACCESS);
        register(BuiltinFlags.DOOR);
        register(BuiltinFlags.TRAPDOOR);
        register(BuiltinFlags.BUTTON);
        register(BuiltinFlags.LEVER);
        register(BuiltinFlags.PRESSURE_PLATE);
        register(BuiltinFlags.GATE);
        // Combat
        register(BuiltinFlags.PVP);
        register(BuiltinFlags.PLAYER_DAMAGE);
        register(BuiltinFlags.MOB_DAMAGE);
        register(BuiltinFlags.FALL_DAMAGE);
        register(BuiltinFlags.INVINCIBLE);
        register(BuiltinFlags.ATTACK_ANIMALS);
        register(BuiltinFlags.ATTACK_MONSTERS);
        register(BuiltinFlags.ENDER_PEARL);
        register(BuiltinFlags.CHORUS_FRUIT);
        // Mobs
        register(BuiltinFlags.MOB_SPAWN);
        register(BuiltinFlags.ANIMAL_SPAWN);
        register(BuiltinFlags.MONSTER_SPAWN);
        register(BuiltinFlags.VILLAGER_SPAWN);
        register(BuiltinFlags.VEHICLE_PLACE);
        register(BuiltinFlags.VEHICLE_DESTROY);
        register(BuiltinFlags.LEASH);
        register(BuiltinFlags.SPAWN_EGG);
        // Explosions & Spread
        register(BuiltinFlags.CREEPER_EXPLOSION);
        register(BuiltinFlags.TNT_EXPLOSION);
        register(BuiltinFlags.BLOCK_EXPLOSION);
        register(BuiltinFlags.FIRE_SPREAD);
        register(BuiltinFlags.LAVA_FIRE);
        register(BuiltinFlags.LAVA_SPREAD);
        register(BuiltinFlags.WATER_SPREAD);
        register(BuiltinFlags.LEAF_DECAY);
        // Environment
        register(BuiltinFlags.CROP_GROWTH);
        register(BuiltinFlags.TREE_GROWTH);
        register(BuiltinFlags.GRASS_SPREAD);
        register(BuiltinFlags.VINE_GROWTH);
        register(BuiltinFlags.SCULK_SPREAD);
        register(BuiltinFlags.SNOW_MELT);
        register(BuiltinFlags.ICE_MELT);
        // Items
        register(BuiltinFlags.ITEM_DROP);
        register(BuiltinFlags.ITEM_PICKUP);
        register(BuiltinFlags.ITEM_THROW);
        register(BuiltinFlags.EXP_DROP);
        // Entry / Exit
        register(BuiltinFlags.ENTRY);
        register(BuiltinFlags.EXIT);
        // Magic mods
        register(BuiltinFlags.ARS_SPELL_CAST);
        register(BuiltinFlags.ARS_SPELL_BLACKLIST);
        register(BuiltinFlags.ARS_SPELL_WHITELIST);
        register(BuiltinFlags.IRONS_SPELL_CAST);
        register(BuiltinFlags.IRONS_SPELL_BLACKLIST);
        register(BuiltinFlags.IRONS_SPELL_WHITELIST);
        register(BuiltinFlags.SIMPLYSWORDS_ABILITY);
        // Custom
        register(BuiltinFlags.SPELL_MOVEMENT);
        register(BuiltinFlags.NPC_INTERACT);
        register(BuiltinFlags.MOB_BUCKET);
        register(BuiltinFlags.ANIMAL_INVINCIBLE);
        // Zone configuration
        register(BuiltinFlags.HEAL_AMOUNT);
        register(BuiltinFlags.FEED_AMOUNT);

        validateAllBuiltinsRegistered();
    }

    private void validateAllBuiltinsRegistered() {
        for (Field field : BuiltinFlags.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!Flag.class.isAssignableFrom(field.getType())) continue;
            try {
                Flag<?> flag = (Flag<?>) field.get(null);
                if (!this.flags.containsKey(flag.id())) {
                    LOGGER.error("[ArcadiaGuard] BuiltinFlags.{} ('{}') was not registered — add it to registerBuiltins().", field.getName(), flag.id());
                }
            } catch (IllegalAccessException ignored) {}
        }
    }
}
