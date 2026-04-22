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
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.server.level.ServerPlayer;
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

        Entity attacker = event.getSource().getEntity();
        if (victim instanceof Player) {
            if (guard.isZoneDenying(zone, BuiltinFlags.INVINCIBLE, level)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zone.name(), victim.blockPosition(), BuiltinFlags.INVINCIBLE, "invincible");
                }
                return;
            }
            if (attacker instanceof ServerPlayer attackerSp) {
                if (guard.isZoneDenying(zone, BuiltinFlags.PVP, level)) {
                    event.setCanceled(true);
                    guard.auditDenied(attackerSp, zone.name(), victim.blockPosition(), BuiltinFlags.PVP, "pvp");
                    return;
                }
            }
            if (guard.isZoneDenying(zone, BuiltinFlags.PLAYER_DAMAGE, level)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zone.name(), victim.blockPosition(), BuiltinFlags.PLAYER_DAMAGE, "player_damage");
                }
            }
        } else if (victim instanceof Animal) {
            // Convention GUI : "ON" (vert) = value=false = protection active
            // → on utilise isZoneDenying comme tous les autres flags (cohérence sémantique).
            if (guard.isZoneDenying(zone, BuiltinFlags.ANIMAL_INVINCIBLE, level)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zone.name(), victim.blockPosition(), BuiltinFlags.ANIMAL_INVINCIBLE, "animal_invincible");
                }
                return;
            }
            if (guard.isZoneDenying(zone, BuiltinFlags.MOB_DAMAGE, level)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zone.name(), victim.blockPosition(), BuiltinFlags.MOB_DAMAGE, "mob_damage");
                }
            }
        } else if (victim instanceof Monster) {
            if (guard.isZoneDenying(zone, BuiltinFlags.MOB_DAMAGE, level)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zone.name(), victim.blockPosition(), BuiltinFlags.MOB_DAMAGE, "mob_damage");
                }
            }
        }
    }

    public void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        // R3 : bypass OP/membre pour cohrence avec les autres flags — sinon un OP en
        // mode survie ne peut pas tester fall_damage sans desactiver le flag.
        if (guard.shouldBypass(player)) return;
        var zoneOpt = guard.zoneManager().findZoneContaining(level, player.blockPosition());
        if (zoneOpt.isEmpty()) return;
        ProtectedZone zone = (ProtectedZone) zoneOpt.get();
        if (guard.isZoneDenying(zone, BuiltinFlags.FALL_DAMAGE, level)) {
            event.setCanceled(true);
            guard.auditDenied(player, zone.name(), player.blockPosition(), BuiltinFlags.FALL_DAMAGE, "fall_damage");
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
        var affectedEntities = event.getAffectedEntities();
        if (affected.isEmpty() && affectedEntities.isEmpty()) return;
        // P3 : fast-path — explosions dans des dimensions sans zone passent vanilla
        // (gros gain sur mega-TNT avec 10k+ blocs affectes).
        if (!com.arcadia.arcadiaguard.helper.FlagMixinHelper.hasAnyZoneInDim(level)) return;

        var explosion = event.getExplosion();
        Entity exploder = explosion.getDirectSourceEntity();
        BooleanFlag flag;
        // S-H17 T1 : detecter aussi les entites du namespace mutantmonsters
        // (MutantCreeper, CreeperMinion, etc.) comme source creeper.
        boolean isMutantCreeperLike = false;
        if (exploder != null) {
            var entType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(exploder.getType());
            if (entType != null && "mutantmonsters".equals(entType.getNamespace())
                    && entType.getPath().contains("creeper")) {
                isMutantCreeperLike = true;
            }
        }
        if (exploder instanceof Creeper || isMutantCreeperLike) {
            flag = BuiltinFlags.CREEPER_EXPLOSION;
        } else if (exploder instanceof PrimedTnt
                || exploder instanceof MinecartTNT
                || (exploder == null
                    && "TNT".equals(explosion.getBlockInteraction().name()))) {
            flag = BuiltinFlags.TNT_EXPLOSION;
        } else {
            flag = BuiltinFlags.BLOCK_EXPLOSION;
        }

        // R2 + perf : pre-resoudre la zone a la position d'origine de l'explosion.
        // Si toute la bbox d'explosion est contenue dans cette zone, 1 seul check
        // vs N check par bloc (gros gain sur mega-TNT = 10k+ blocs affectes).
        final BooleanFlag finalFlag = flag;
        net.minecraft.world.phys.Vec3 origin = explosion.center();
        net.minecraft.core.BlockPos originPos = net.minecraft.core.BlockPos.containing(origin);
        var originZoneOpt = guard.zoneManager().findZoneContaining(level, originPos);
        if (originZoneOpt.isPresent()
                && originZoneOpt.get() instanceof ProtectedZone originZone
                && explosionBboxInsideZone(affected, affectedEntities, originZone)) {
            // Fast-path : toute l'explosion est dans la meme zone, 1 seul check.
            if (guard.isZoneDenying(originZone, finalFlag, level)) {
                affected.clear();
                affectedEntities.clear();
            }
            return;
        }
        // Slow-path : zones multiples ou explosion chevauchante.
        affected.removeIf(pos -> guard.isZoneDenying(level, pos, finalFlag));
        affectedEntities.removeIf(entity ->
            guard.isZoneDenying(level, entity.blockPosition(), finalFlag));
    }

    /** Renvoie true si tous les blocs+entites affectes sont contenus dans la bbox de la zone. */
    private static boolean explosionBboxInsideZone(
            java.util.List<net.minecraft.core.BlockPos> affected,
            java.util.List<Entity> affectedEntities,
            ProtectedZone zone) {
        for (var pos : affected) {
            if (pos.getX() < zone.minX() || pos.getX() > zone.maxX()
             || pos.getY() < zone.minY() || pos.getY() > zone.maxY()
             || pos.getZ() < zone.minZ() || pos.getZ() > zone.maxZ()) return false;
        }
        for (var e : affectedEntities) {
            var p = e.blockPosition();
            if (p.getX() < zone.minX() || p.getX() > zone.maxX()
             || p.getY() < zone.minY() || p.getY() > zone.maxY()
             || p.getZ() < zone.minZ() || p.getZ() > zone.maxZ()) return false;
        }
        return true;
    }

    public void onAnimalJoinLevel(EntityJoinLevelEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;
        if (!(event.getEntity() instanceof Animal animal)) return;
        if (!com.arcadia.arcadiaguard.helper.FlagMixinHelper.hasAnyZoneInDim(level)) return;
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
        // P2 : fast-path O(1) — skip l'animal si aucune zone dans la dim.
        if (!com.arcadia.arcadiaguard.helper.FlagMixinHelper.hasAnyZoneInDim(level)) return;
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
