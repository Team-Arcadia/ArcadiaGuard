package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.flag.FlagResolver;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class EntityEventHandler {

    private final GuardService guard;

    public EntityEventHandler(GuardService guard) {
        this.guard = guard;
    }

    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide()) return;

        // H-P2: single zone lookup; subsequent flag checks use the already-resolved zone
        var zoneIZoneOpt = guard.zoneManager().findZoneContaining(level, victim.blockPosition());
        if (zoneIZoneOpt.isEmpty()) return; // no zone → no restriction
        ProtectedZone zone = (ProtectedZone) zoneIZoneOpt.get();

        if (victim instanceof Player) {
            if (guard.isZoneDenying(zone, BuiltinFlags.INVINCIBLE, level)) {
                event.setCanceled(true);
                return;
            }
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof Player) {
                if (guard.isZoneDenying(zone, BuiltinFlags.PVP, level)) {
                    event.setCanceled(true);
                    return;
                }
            }
            if (guard.isZoneDenying(zone, BuiltinFlags.PLAYER_DAMAGE, level)) {
                event.setCanceled(true);
            }
        } else if (victim instanceof Animal animal) {
            if (isAnimalInvincibleZone(level, animal, zone)) {
                event.setCanceled(true);
                return;
            }
            if (guard.isZoneDenying(zone, BuiltinFlags.MOB_DAMAGE, level)) {
                event.setCanceled(true);
            }
        } else if (victim instanceof Monster) {
            if (guard.isZoneDenying(zone, BuiltinFlags.MOB_DAMAGE, level)) {
                event.setCanceled(true);
            }
        }
    }

    public void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (guard.isZoneDenying(level, player.blockPosition(), BuiltinFlags.FALL_DAMAGE)) {
            event.setCanceled(true);
        }
    }

    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;
        Mob entity = event.getEntity();

        if (guard.isZoneDenying(level, entity.blockPosition(), BuiltinFlags.MOB_SPAWN)) {
            event.setSpawnCancelled(true);
            return;
        }

        BooleanFlag typeFlag;
        if (entity instanceof Monster) {
            typeFlag = BuiltinFlags.MONSTER_SPAWN;
        } else if (entity instanceof AbstractVillager) {
            typeFlag = BuiltinFlags.VILLAGER_SPAWN;
        } else if (entity instanceof Animal) {
            typeFlag = BuiltinFlags.ANIMAL_SPAWN;
        } else {
            return;
        }

        if (guard.isZoneDenying(level, entity.blockPosition(), typeFlag)) {
            event.setSpawnCancelled(true);
        }
    }

    public void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        var affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        var explosion = event.getExplosion();
        Entity exploder = explosion.getDirectSourceEntity();
        BooleanFlag flag;
        if (exploder instanceof Creeper) {
            flag = BuiltinFlags.CREEPER_EXPLOSION;
        } else if (exploder instanceof PrimedTnt
                || (exploder == null
                    && "TNT".equals(explosion.getBlockInteraction().name()))) {
            flag = BuiltinFlags.TNT_EXPLOSION;
        } else {
            flag = BuiltinFlags.BLOCK_EXPLOSION;
        }

        // H-P5: Long2ByteOpenHashMap avoids Long boxing: -1=absent, 0=allow, 1=deny.
        // Cache zone verdicts per 16×16×16 cell: typical explosions cluster in
        // a handful of cells, so we go from O(N) zone lookups to ~O(cells).
        Long2ByteOpenHashMap cellVerdict = new Long2ByteOpenHashMap(16);
        cellVerdict.defaultReturnValue((byte) -1);
        affected.removeIf(pos -> {
            long key = cellKey(pos);
            byte cached = cellVerdict.get(key);
            if (cached != -1) return cached == 1;
            boolean deny = guard.isZoneDenying(level, pos, flag);
            cellVerdict.put(key, deny ? (byte) 1 : (byte) 0);
            return deny;
        });
    }

    private static long cellKey(net.minecraft.core.BlockPos pos) {
        long cx = pos.getX() >> 4;
        long cy = pos.getY() >> 4;
        long cz = pos.getZ() >> 4;
        return (cx & 0x1FFFFFFL) | ((cz & 0x1FFFFFFL) << 25) | ((cy & 0xFFFL) << 50);
    }

    /**
     * Applies Resistance and Regeneration to animals in zones with ANIMAL_INVINCIBLE=true.
     * Refreshes effects every 40 ticks so they never expire.
     */
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Animal animal)) return;
        Level level = animal.level();
        if (level.isClientSide()) return;
        if (animal.tickCount % 40 != 0) return;
        if (!isAnimalInvincibleZone(level, animal)) return;

        animal.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 255, false, false));
        animal.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 255, false, false));
    }

    /** H-P2 overload: uses an already-resolved zone — no extra lookup. */
    @SuppressWarnings("unchecked")
    private boolean isAnimalInvincibleZone(Level level, Animal animal, ProtectedZone zone) {
        Function<String, Optional<ProtectedZone>> lookup =
            name -> (Optional<ProtectedZone>)(Optional<?>) guard.zoneManager().get(level, name);
        return FlagResolver.resolve(zone, BuiltinFlags.ANIMAL_INVINCIBLE, lookup);
    }

    /** Overload used by onEntityTick (no pre-resolved zone available). */
    @SuppressWarnings("unchecked")
    private boolean isAnimalInvincibleZone(Level level, Animal animal) {
        return guard.zoneManager().findZoneContaining(level, animal.blockPosition())
            .map(z -> {
                Function<String, Optional<ProtectedZone>> lookup =
                    name -> (Optional<ProtectedZone>)(Optional<?>) guard.zoneManager().get(level, name);
                return FlagResolver.resolve((ProtectedZone) z, BuiltinFlags.ANIMAL_INVINCIBLE, lookup);
            })
            .orElse(false);
    }
}
