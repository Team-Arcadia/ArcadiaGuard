package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
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
        } else if (victim instanceof Animal) {
            // Convention GUI : "ON" (vert) = value=false = protection active
            // → on utilise isZoneDenying comme tous les autres flags (cohérence sémantique).
            if (guard.isZoneDenying(zone, BuiltinFlags.ANIMAL_INVINCIBLE, level)) {
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

        affected.removeIf(pos -> guard.isZoneDenying(level, pos, flag));
    }

    public void onAnimalJoinLevel(EntityJoinLevelEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;
        if (!(event.getEntity() instanceof Animal animal)) return;
        if (!isAnimalInvincibleAt(level, animal.blockPosition())) return;
        animal.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 255, false, false));
        animal.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 255, false, false));
    }

    /**
     * Applies Resistance and Regeneration to animals in zones with ANIMAL_INVINCIBLE=true.
     * Refreshes every 20 ticks with a 40-tick duration so effects never expire between refreshes,
     * closing the damage window both for zone entry (via frontier crossing, no JoinLevelEvent)
     * and for animals whose initial join-level effect expires.
     */
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Animal animal)) return;
        Level level = animal.level();
        if (level.isClientSide()) return;
        if (animal.tickCount % 20 != 0) return;
        if (!isAnimalInvincibleAt(level, animal.blockPosition())) return;

        animal.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 255, false, false));
        animal.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 255, false, false));
    }

    /**
     * Retourne true si la zone contenant {@code pos} a la protection animal_invincible
     * active (sémantique GUI : "ON (vert)" = value=false = protection active, cohérent
     * avec le reste des flags via {@link GuardService#isZoneDenying(Level, BlockPos, BooleanFlag)}).
     */
    private boolean isAnimalInvincibleAt(Level level, net.minecraft.core.BlockPos pos) {
        return guard.isZoneDenying(level, pos, BuiltinFlags.ANIMAL_INVINCIBLE);
    }
}
