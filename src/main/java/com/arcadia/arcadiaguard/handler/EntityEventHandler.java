package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import net.minecraft.core.BlockPos;
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

        // H-P2: single zone lookup; subsequent flag checks use the already-resolved zone.
        // Si hors zone, on peut quand meme declencher sur un dim flag -> fallback sur la
        // variante level-aware isZoneDenying(level, pos, flag) qui gere ce cas.
        BlockPos vpos = victim.blockPosition();
        var zoneIZoneOpt = guard.zoneManager().findZoneContaining(level, vpos);
        ProtectedZone zone = zoneIZoneOpt.map(z -> (ProtectedZone) z).orElse(null);
        String zoneName = zone != null ? zone.name() : "(dimension)";

        Entity attacker = event.getSource().getEntity();
        if (victim instanceof Player) {
            if (isDenyingHere(zone, level, vpos, BuiltinFlags.INVINCIBLE)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zoneName, vpos, BuiltinFlags.INVINCIBLE, "invincible");
                }
                return;
            }
            if (attacker instanceof ServerPlayer attackerSp) {
                if (isDenyingHere(zone, level, vpos, BuiltinFlags.PVP)) {
                    event.setCanceled(true);
                    guard.auditDenied(attackerSp, zoneName, vpos, BuiltinFlags.PVP, "pvp");
                    return;
                }
            }
            if (isDenyingHere(zone, level, vpos, BuiltinFlags.PLAYER_DAMAGE)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zoneName, vpos, BuiltinFlags.PLAYER_DAMAGE, "player_damage");
                }
            }
        } else if (victim instanceof Animal) {
            // Convention GUI : "ON" (vert) = value=false = protection active
            // → on utilise isZoneDenying comme tous les autres flags (cohérence sémantique).
            if (isDenyingHere(zone, level, vpos, BuiltinFlags.ANIMAL_INVINCIBLE)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zoneName, vpos, BuiltinFlags.ANIMAL_INVINCIBLE, "animal_invincible");
                }
                return;
            }
            if (isDenyingHere(zone, level, vpos, BuiltinFlags.MOB_DAMAGE)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zoneName, vpos, BuiltinFlags.MOB_DAMAGE, "mob_damage");
                }
            }
        } else if (victim instanceof Monster) {
            if (isDenyingHere(zone, level, vpos, BuiltinFlags.MOB_DAMAGE)) {
                event.setCanceled(true);
                if (attacker instanceof ServerPlayer sp) {
                    guard.auditDenied(sp, zoneName, vpos, BuiltinFlags.MOB_DAMAGE, "mob_damage");
                }
            }
        }
    }

    /**
     * Helper : teste un flag a la position donnee en utilisant la zone deja resolue si dispo,
     * sinon fait un fallback dim via la variante level-aware. Evite un 2e findZone quand on a
     * deja la zone en main.
     */
    private boolean isDenyingHere(ProtectedZone zone, Level level, BlockPos pos, BooleanFlag flag) {
        return zone != null
            ? guard.isZoneDenying(zone, flag, level)
            : guard.isZoneDenying(level, pos, flag);
    }

    public void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        // R3 : bypass OP/membre pour cohrence avec les autres flags — sinon un OP en
        // mode survie ne peut pas tester fall_damage sans desactiver le flag.
        if (guard.shouldBypass(player)) return;
        BlockPos pos = player.blockPosition();
        // Fallback dim : isZoneDenying(level, pos, flag) gere le cas hors zone.
        if (guard.isZoneDenying(level, pos, BuiltinFlags.FALL_DAMAGE)) {
            event.setCanceled(true);
            String zoneName = guard.zoneManager().findZoneContaining(level, pos)
                .map(z -> ((ProtectedZone) z).name()).orElse("(dimension)");
            guard.auditDenied(player, zoneName, pos, BuiltinFlags.FALL_DAMAGE, "fall_damage");
        }
    }

    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;
        Mob entity = event.getEntity();
        net.minecraft.core.BlockPos pos = entity.blockPosition();

        // 1) Blocage global : mob-spawn=deny stoppe toute apparition.
        if (guard.isZoneDenying(level, pos, BuiltinFlags.MOB_SPAWN)) {
            event.setSpawnCancelled(true);
            return;
        }

        // 2) Blacklist par id : mob-spawn-list contient l'id du mob (ou un wildcard namespace).
        var entityKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityKey != null) {
            java.util.List<String> blacklist = guard.resolveListAt(level, pos, BuiltinFlags.MOB_SPAWN_LIST);
            if (!blacklist.isEmpty() && matchesMobList(blacklist, entityKey)) {
                event.setSpawnCancelled(true);
                return;
            }
        }

        // 3) Flag par categorie (monster/animal/villager).
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

        if (guard.isZoneDenying(level, pos, typeFlag)) {
            event.setSpawnCancelled(true);
        }
    }

    /**
     * Match un id de mob contre une liste d'entrees. Supporte :
     *   - match exact : "minecraft:zombie"
     *   - wildcard namespace : "mutantmonsters:*" -> tous les mobs du namespace
     *   - wildcard path : "*:zombie" -> zombie de n'importe quel mod
     *   - path seul : "zombie" -> traite comme "minecraft:zombie"
     */
    private static boolean matchesMobList(java.util.List<String> list, net.minecraft.resources.ResourceLocation key) {
        String ns = key.getNamespace();
        String path = key.getPath();
        String full = ns + ":" + path;
        for (String entry : list) {
            if (entry == null || entry.isEmpty()) continue;
            String e = entry.trim().toLowerCase(java.util.Locale.ROOT);
            if (e.isEmpty()) continue;
            // Pas de ':' -> interprete comme minecraft:<path>
            if (!e.contains(":")) e = "minecraft:" + e;
            int sep = e.indexOf(':');
            String en = e.substring(0, sep);
            String ep = e.substring(sep + 1);
            if ("*".equals(en) && "*".equals(ep)) return true;
            if ("*".equals(en) && ep.equalsIgnoreCase(path)) return true;
            if (en.equalsIgnoreCase(ns) && "*".equals(ep)) return true;
            if (e.equalsIgnoreCase(full)) return true;
        }
        return false;
    }

    public void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        var affected = event.getAffectedBlocks();
        var affectedEntities = event.getAffectedEntities();
        if (affected.isEmpty() && affectedEntities.isEmpty()) return;
        // P3 : fast-path — explosions dans des dimensions sans zone passent vanilla
        // (gros gain sur mega-TNT avec 10k+ blocs affectes).
        if (!com.arcadia.arcadiaguard.helper.FlagMixinHelper.hasAnyRuleInDim(level)) return;

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
        if (!com.arcadia.arcadiaguard.helper.FlagMixinHelper.hasAnyRuleInDim(level)) return;
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
        if (!com.arcadia.arcadiaguard.helper.FlagMixinHelper.hasAnyRuleInDim(level)) return;
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
