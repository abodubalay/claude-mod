package com.kino;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/** Ловим «обычные действия» и важные моменты. */
public class ServerEvents {
    private final Map<UUID, Boolean> wasSleeping = new HashMap<>();
    private final Map<UUID, Boolean> wasAwayFromHome = new HashMap<>();

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) { KinoCommand.register(e.getDispatcher()); }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) Cutscenes.tick(e.getServer());
    }

    // ---------- кат-сцены ----------

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) return;
        BlockState s = e.getState();

        if (s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            Cutscenes.tryTrigger(p, Cutscenes.Action.DIAMOND);
            Sitcom.react(p, Sitcom.APPLAUSE, false);
        } else if (s.is(BlockTags.LOGS)) {
            Cutscenes.tryTrigger(p, Cutscenes.Action.TREE);
        } else if (s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT)
                || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.DIRT_PATH)) {
            Cutscenes.tryTrigger(p, Cutscenes.Action.DIRT);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getPlacedBlock().is(Blocks.CRAFTING_TABLE))
            Cutscenes.tryTrigger(p, Cutscenes.Action.TABLE);
    }

    @SubscribeEvent
    public void onEat(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getItem().is(Items.BREAD))
            Cutscenes.tryTrigger(p, Cutscenes.Action.BREAD);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        BlockState s = p.level().getBlockState(e.getPos());
        if (s.getBlock() instanceof DoorBlock && s.hasProperty(DoorBlock.OPEN) && !s.getValue(DoorBlock.OPEN)
                && !s.is(Blocks.IRON_DOOR))
            Cutscenes.tryTrigger(p, Cutscenes.Action.DOOR);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof Animal a)) return;
        boolean feed = a.isFood(e.getItemStack());
        boolean pet = e.getItemStack().isEmpty() && a instanceof TamableAnimal t && t.isOwnedBy(p);
        if (feed || pet) Cutscenes.tryTrigger(p, Cutscenes.Action.ANIMAL);
    }

    /** Лёг спать: раз в 3 игровых дня — титры, иначе возможна кат-сцена. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        boolean sleeping = p.isSleeping();
        if (sleeping && !wasSleeping.getOrDefault(p.getUUID(), false)) {
            if (Credits.due(p)) Credits.show(p);
            else Cutscenes.tryTrigger(p, Cutscenes.Action.SLEEP);
        }
        wasSleeping.put(p.getUUID(), sleeping);
        if (p.tickCount % 100 == 0) Credits.scanCast(p);
        if (p.tickCount % 20 == 0) checkHome(p);
    }

    /** Зашёл в свой дом (рядом с кроватью, под крышей) первый раз за игровой день — аплодисменты. */
    private void checkHome(ServerPlayer p) {
        BlockPos bed = p.getRespawnPosition();
        if (bed == null || p.getRespawnDimension() != p.level().dimension()) return;
        if (!(p.level().getBlockState(bed).getBlock() instanceof BedBlock)) return;
        double dist = Math.sqrt(p.blockPosition().distSqr(bed));
        if (dist > 16) { wasAwayFromHome.put(p.getUUID(), true); return; }
        if (dist < 6 && !p.level().canSeeSky(p.blockPosition().above()) && wasAwayFromHome.getOrDefault(p.getUUID(), false)) {
            wasAwayFromHome.put(p.getUUID(), false);
            long day = Credits.day(p);
            if (KinoData.getLong(p, "homeDay") != day + 1) { // +1, чтобы день 0 отличался от «ни разу»
                KinoData.setLong(p, "homeDay", day + 1);
                Sitcom.react(p, Sitcom.CHEER, false);
            }
        }
    }

    // ---------- ситком ----------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isCanceled()) return;
        if (e.getSource().is(DamageTypeTags.IS_FALL)) Sitcom.react(p, Sitcom.LAUGH, false); // и «упал в свою же яму» тоже
    }

    /** Неуязвим во время кат-сцены. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Cutscenes.isInScene(p)
                && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        DamageSource src = e.getSource();
        if (e.getEntity() instanceof ServerPlayer p) {
            KinoData.addInt(p, "deaths");
            Entity by = src.getEntity(), direct = src.getDirectEntity();
            if (by instanceof Creeper || direct instanceof Creeper) Sitcom.react(p, Sitcom.LAUGH_OOH, false);
            else if (src.is(DamageTypes.DROWN) || src.is(DamageTypes.LAVA) || src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.ON_FIRE))
                Sitcom.react(p, Sitcom.OOH_CHUCKLE, false);
            Cutscenes.end(p);
        } else if (e.getEntity() instanceof Chicken && src.getEntity() instanceof ServerPlayer p) {
            KinoData.addInt(p, "chickens");
        }
    }

    @SubscribeEvent
    public void onTame(AnimalTameEvent e) {
        if (e.getTamer() instanceof ServerPlayer p) Sitcom.react(p, Sitcom.AWW, false);
    }

    @SubscribeEvent
    public void onHitVillager(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getTarget() instanceof Villager) Sitcom.react(p, Sitcom.BOO, false);
    }
}
