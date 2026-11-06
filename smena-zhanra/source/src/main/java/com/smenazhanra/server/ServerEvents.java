package com.smenazhanra.server;

import com.smenazhanra.SmenaZhanra;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SmenaZhanra.MODID)
public class ServerEvents {

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        ModCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer sp) GenreManager.tickPlayer(sp);
    }

    /** Во время мини-игры игрок неуязвим (кроме /kill и пустоты). Свинка-тян неуязвима всегда. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        boolean protectedEntity = (e.getEntity() instanceof ServerPlayer sp && GenreManager.isProtected(sp))
                || (e.getEntity() instanceof Pig pig && Punishments.isPet(pig));
        if (!protectedEntity) return;
        if (e.getEntity() instanceof Pig && e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            e.setCanceled(true); // свинью из пустоты спасём телепортом
            return;
        }
        if (!e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
    }

    /** Мобы не выбирают целью игрока, который в другом жанре. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (e.getNewTarget() instanceof ServerPlayer sp && GenreManager.isProtected(sp)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof Pig pig && Punishments.isPet(pig)
                && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
            pig.setHealth(pig.getMaxHealth());
            return;
        }
        if (e.getEntity() instanceof ServerPlayer sp) {
            if (e.getSource().is(DamageTypes.LAVA)) {
                var d = GenreManager.data(sp);
                d.putInt("lavaDeaths", d.getInt("lavaDeaths") + 1);
            }
            if (GenreManager.hasSession(sp)) GenreManager.forceEnd(sp, "Мини-игра прервана.");
        }
    }

    /** Молния не превращает Свинку-тян в зомби-пиглина. */
    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent e) {
        if (e.getEntity() instanceof Pig pig && Punishments.isPet(pig)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent e) {
        if (e.getEntity() instanceof Pig pig && !pig.level().isClientSide && Punishments.isPet(pig)) Punishments.tickPet(pig);
    }

    /** Если мир закрылся во время мини-игры — при загрузке размораживаем «забытых» мобов. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide && e.getEntity() instanceof Mob m
                && m.getTags().contains(GenreManager.FROZEN_TAG) && !GenreManager.isFrozenByAnyone(m.getUUID())) {
            GenreManager.unfreezeMob(m);
        }
    }

    /** Данные мода переживают смерть игрока. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        var old = e.getOriginal().getPersistentData();
        if (old.contains(GenreManager.DATA)) e.getEntity().getPersistentData().put(GenreManager.DATA, old.getCompound(GenreManager.DATA).copy());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            GenreManager.sendAllStatuses(sp);
            GenreManager.broadcastStatus(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) GenreManager.onLogout(sp);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && GenreManager.hasSession(sp)) GenreManager.forceEnd(sp, "Мини-игра прервана.");
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers()) GenreManager.onLogout(sp);
        GenreManager.clearAll();
    }
}
