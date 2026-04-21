package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

/** Catch-all handler for flags that don't fit elsewhere. */
public final class FlagEventHandler {

    private final GuardService guard;

    public FlagEventHandler(GuardService guard) { this.guard = guard; }

    // ── RightClickBlock : interactions avec blocs ───────────────────────────────

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player)) return;

        BlockPos pos = event.getPos();
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        BlockEntity be = level.getBlockEntity(pos);

        // Flag spécifique selon le type de bloc (ordre du plus spécifique au plus général).
        BooleanFlag specific = null;
        String specificMsg = null;
        if (block instanceof DoorBlock)        { specific = BuiltinFlags.DOOR;     specificMsg = "door"; }
        else if (block instanceof TrapDoorBlock) { specific = BuiltinFlags.TRAPDOOR; specificMsg = "trapdoor"; }
        else if (block instanceof ButtonBlock)   { specific = BuiltinFlags.BUTTON;   specificMsg = "button"; }
        else if (block instanceof LeverBlock)    { specific = BuiltinFlags.LEVER;    specificMsg = "lever"; }
        else if (block instanceof FenceGateBlock){ specific = BuiltinFlags.GATE;     specificMsg = "gate"; }

        if (specific != null && deny(player, pos, specific, specificMsg)) {
            event.setCanceled(true);
            return;
        }

        // S-H16 T3 : blocs de mods qui agissent comme des leviers mais n'etendent pas LeverBlock.
        // Create (handcrank, clutch, gearshift, lever), Supplementaries (sconce_lever),
        // Design'n'Decor. Matching strict via endsWith pour eviter les faux positifs
        // (ex: sconce_lever_holder ou gearshift_cluster).
        ResourceLocation blockIdForLever = BuiltInRegistries.BLOCK.getKey(block);
        if (blockIdForLever != null) {
            String ns = blockIdForLever.getNamespace();
            String path = blockIdForLever.getPath();
            boolean isModLever =
                ("create".equals(ns) && (path.endsWith("handcrank") || path.endsWith("clutch")
                    || path.endsWith("gearshift") || path.endsWith("lever")))
                || ("supplementaries".equals(ns) && path.endsWith("lever"))
                || ("design_decor".equals(ns) && path.endsWith("lever"));
            if (isModLever && deny(player, pos, BuiltinFlags.LEVER, "lever")) {
                event.setCanceled(true);
                return;
            }
        }

        // S-H17 T4 : Macaw's garage door (n'etend pas DoorBlock vanilla).
        // endsWith strict pour ne pas matcher garage_door_button / garage_door_trim / etc.
        if (blockIdForLever != null && "mcwdoors".equals(blockIdForLever.getNamespace())
                && blockIdForLever.getPath().endsWith("garage_door")
                && deny(player, pos, BuiltinFlags.DOOR, "door")) {
            event.setCanceled(true);
            return;
        }

        // ARS_ADDITIONS_SCROLL : parchemins Ars Additions utilisés sur un bloc (use() overridé côté serveur).
        // Vérifié AVANT BLOCK_INTERACT générique pour garantir le message spécifique
        // et la prise en compte du flag même quand block_interact=true.
        ItemStack stack = event.getItemStack();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null && "ars_additions".equals(itemId.getNamespace())
                && deny(player, pos, BuiltinFlags.ARS_ADDITIONS_SCROLL, "ars_additions_scroll")) {
            event.setCanceled(true);
            return;
        }

        // Conteneurs : block entity qui expose Container ou MenuProvider.
        if (be instanceof Container || be instanceof MenuProvider) {
            if (deny(player, pos, BuiltinFlags.CONTAINER_ACCESS, "container_access")) {
                event.setCanceled(true);
                return;
            }
        }

        // BLOCK_INTERACT générique (couvre tout le reste).
        // Exception pose : si le joueur tient un BlockItem ET le bloc n'est pas interagissable,
        // laisser passer → BlockEvent.EntityPlaceEvent (flag block_place) décide.
        // Les blocs interagissables sans Container/MenuProvider sont listés explicitement
        // pour éviter que tenir un BlockItem ne contourne le flag (ex: dormir dans un lit).
        boolean holdingBlockItem = stack.getItem() instanceof BlockItem;
        boolean isInteractiveBlock = block instanceof BedBlock
                || block instanceof NoteBlock
                || block instanceof ComposterBlock
                || block instanceof AbstractCauldronBlock
                || block instanceof CakeBlock;
        if ((!holdingBlockItem || isInteractiveBlock)
                && deny(player, pos, BuiltinFlags.BLOCK_INTERACT, "block_interact")) {
            event.setCanceled(true);
            return;
        }

        // VEHICLE_PLACE : poser un boat/minecart.
        if ((stack.getItem() instanceof BoatItem || stack.getItem() instanceof MinecartItem)
                && deny(player, event.getPos().relative(event.getFace()), BuiltinFlags.VEHICLE_PLACE, "vehicle_place")) {
            event.setCanceled(true);
            return;
        }

        // WAYSTONE_USE : blocs waystones
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId != null && "waystones".equals(blockId.getNamespace())
                && deny(player, pos, BuiltinFlags.WAYSTONE_USE, "waystone_use")) {
            event.setCanceled(true);
            return;
        }

        // RECHISELED_USE : blocs rechiseled (interfaces de design de blocs)
        if (blockId != null && "rechiseled".equals(blockId.getNamespace())
                && deny(player, pos, BuiltinFlags.RECHISELED_USE, "rechiseled_use")) {
            event.setCanceled(true);
            return;
        }
    }

    // ── RightClickItem : téléportations, throwables ─────────────────────────────

    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player)) return;

        ItemStack stack = event.getItemStack();
        BlockPos pos = player.blockPosition();

        if (stack.getItem() instanceof EnderpearlItem
                && deny(player, pos, BuiltinFlags.ENDER_PEARL, "ender_pearl")) {
            event.setCanceled(true);
            return;
        }
        if (stack.getItem() instanceof ChorusFruitItem
                && deny(player, pos, BuiltinFlags.CHORUS_FRUIT, "chorus_fruit")) {
            event.setCanceled(true);
            return;
        }

        // ARS_ADDITIONS_SCROLL : parchemins Ars Additions (namespace ars_additions)
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null && "ars_additions".equals(itemId.getNamespace())
                && deny(player, pos, BuiltinFlags.ARS_ADDITIONS_SCROLL, "ars_additions_scroll")) {
            event.setCanceled(true);
            return;
        }

        // ITEM_THROW : projectiles consommables (œufs, snowballs, potions, tridents).
        if ((stack.getItem() instanceof EggItem
                || stack.getItem() instanceof SnowballItem
                || stack.getItem() instanceof ThrowablePotionItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof ProjectileItem)
                && !(stack.getItem() instanceof EnderpearlItem) // déjà couvert au-dessus
                && deny(player, pos, BuiltinFlags.ITEM_THROW, "item_throw")) {
            event.setCanceled(true);
        }
    }

    // ── Attaque d'entités ───────────────────────────────────────────────────────

    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player)) return;

        Entity target = event.getTarget();
        BlockPos pos = target.blockPosition();
        BooleanFlag flag = null;
        String action = "attack";

        if (target instanceof Animal) { flag = BuiltinFlags.ATTACK_ANIMALS; action = "attack_animal"; }
        else if (target instanceof Monster) { flag = BuiltinFlags.ATTACK_MONSTERS; action = "attack_monster"; }
        else if (target instanceof Boat || target instanceof AbstractMinecart) {
            flag = BuiltinFlags.VEHICLE_DESTROY; action = "vehicle_destroy";
        }

        if (flag != null && deny(player, pos, flag, action)) event.setCanceled(true);
    }

    // ── Items : drop / pickup / xp ──────────────────────────────────────────────

    public void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player)) return;
        if (deny(player, player.blockPosition(), BuiltinFlags.ITEM_DROP, "item_drop")) {
            event.setCanceled(true);
            // Remettre l'item dans l'inventaire
            player.getInventory().add(event.getEntity().getItem());
        }
    }

    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player)) return;
        if (guard.isZoneDenying(player.level(), player.blockPosition(), BuiltinFlags.ITEM_PICKUP)) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    public void onExpDrop(LivingExperienceDropEvent event) {
        Entity attacker = event.getAttackingPlayer();
        Level level = event.getEntity().level();
        BlockPos pos = event.getEntity().blockPosition();
        if (attacker instanceof ServerPlayer sp && guard.shouldBypass(sp)) return;
        if (guard.isZoneDenying(level, pos, BuiltinFlags.EXP_DROP)) {
            event.setDroppedExperience(0);
            event.setCanceled(true);
        }
    }

    // ── Téléportations ──────────────────────────────────────────────────────────

    public void onEnderPearlTeleport(EntityTeleportEvent.EnderPearl event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player)) return;
        BlockPos dest = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        if (guard.isZoneDenying(player.level(), dest, BuiltinFlags.ENDER_PEARL)
                || guard.isZoneDenying(player.level(), player.blockPosition(), BuiltinFlags.ENDER_PEARL)) {
            event.setCanceled(true);
        }
    }

    public void onChorusFruitTeleport(EntityTeleportEvent.ChorusFruit event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player)) return;
        BlockPos dest = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        if (guard.isZoneDenying(player.level(), dest, BuiltinFlags.CHORUS_FRUIT)
                || guard.isZoneDenying(player.level(), player.blockPosition(), BuiltinFlags.CHORUS_FRUIT)) {
            event.setCanceled(true);
        }
    }

    // ── Croissance & propagation ────────────────────────────────────────────────

    public void onCropGrow(CropGrowEvent.Pre event) {
        if (guard.isZoneDenying((Level) event.getLevel(), event.getPos(), BuiltinFlags.CROP_GROWTH)) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    public void onBonemeal(BonemealEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;
        BlockPos pos = event.getPos();
        if (guard.isZoneDenying(level, pos, BuiltinFlags.CROP_GROWTH)) {
            event.setCanceled(true);
            return;
        }
        // Les vines (overworld, nether, cave) sont des GrowingPlantBlock : bonemeal sur body
        // délègue à la head, donc on check VINE_GROWTH en plus.
        Block block = event.getState().getBlock();
        if (block instanceof GrowingPlantBlock
                && guard.isZoneDenying(level, pos, BuiltinFlags.VINE_GROWTH)) {
            event.setCanceled(true);
        }
    }

    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        Level level = (Level) event.getLevel();
        if (guard.isZoneDenying(level, event.getPos(), BuiltinFlags.FARMLAND_TRAMPLE)) {
            event.setCanceled(true);
        }
    }

    public void onTreeGrow(BlockGrowFeatureEvent event) {
        Level level = (Level) event.getLevel();
        if (guard.isZoneDenying(level, event.getPos(), BuiltinFlags.TREE_GROWTH)) {
            event.setCanceled(true);
        }
    }

    public void onVehicleJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Boat) && !(entity instanceof AbstractMinecart)) return;
        Level level = (Level) event.getLevel();
        if (guard.isZoneDenying(level, entity.blockPosition(), BuiltinFlags.VEHICLE_PLACE)) {
            event.setCanceled(true);
        }
    }

    public void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        // La lave crée du feu/obsidienne ; l'eau crée de l'obsidienne au contact de lave.
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState newState = event.getNewState();
        if (newState.getBlock() instanceof BaseFireBlock
                && guard.isZoneDenying(level, pos, BuiltinFlags.LAVA_FIRE)) {
            event.setNewState(level.getBlockState(pos));
        }
    }

    public void onBlockEntityPlace(BlockEvent.EntityPlaceEvent event) {
        // Pose de feu par environnement (null entity) → FIRE_SPREAD
        if (event.getEntity() != null) return;
        Level level = (Level) event.getLevel();
        if (event.getPlacedBlock().getBlock() instanceof BaseFireBlock
                && guard.isZoneDenying(level, event.getPos(), BuiltinFlags.FIRE_SPREAD)) {
            event.setCanceled(true);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Retourne true si le joueur est dans une zone où {@code flag} est explicitement false. */
    private boolean deny(ServerPlayer player, BlockPos pos, BooleanFlag flag, String actionName) {
        Optional<ProtectedZone> zoneOpt = guard.zoneManager().checkZone(player, pos);
        if (zoneOpt.isEmpty()) return false;
        if (!guard.isZoneDenying(zoneOpt.get(), flag, player.serverLevel())) return false;
        player.displayClientMessage(Component.translatable("arcadiaguard.message." + actionName)
            .withStyle(ChatFormatting.RED), true);
        return true;
    }
}
