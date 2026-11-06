package com.babushki;

import com.babushki.block.BenchBlock;
import com.babushki.command.BabushkiCommand;
import com.babushki.item.KnittedArmorItem;
import com.babushki.logic.BabushkiData;
import com.babushki.logic.BabushkiManager;
import com.babushki.logic.Gifts;
import com.babushki.logic.Gossip;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ForgeEvents {
    private BabushkiManager mgr() { return BabushkiManager.get(); }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent e) { mgr().onServerStart(e.getServer()); }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent e) { mgr().onServerStop(); }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) mgr().tick(e.getServer());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) { BabushkiCommand.register(e.getDispatcher()); }

    private boolean enabled(ServerPlayer p) {
        return BabushkiData.get(p.server).enabled;
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && enabled(p)) {
            mgr().onBlockBroken(p, e.getPos(), e.getState());
            if (e.getState().is(Blocks.DIAMOND_ORE) || e.getState().is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                Gossip.remember(p.getGameProfile().getName(), "алмазы нашёл! Теперь, небось, и здороваться перестанет!");
            }
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getPlacedBlock().getBlock() instanceof BenchBlock) {
            mgr().onBenchPlaced(p, e.getPos(), e.getPlacedBlock());
        }
    }

    @SubscribeEvent
    public void onToss(ItemTossEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && enabled(p) && Gifts.isGift(e.getEntity().getItem())) {
            ItemStack stack = e.getEntity().getItem();
            mgr().onGiftTossed(p, stack);
            e.getEntity().setItem(stack);
        }
    }

    @SubscribeEvent
    public void onEat(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntity() instanceof ServerPlayer p && enabled(p)
                && e.getItem().is(ModRegistry.PIROZHOK.get()) && Gifts.isGift(e.getItem())) {
            mgr().onGiftPieEaten(p, e.getItem());
        }
    }

    @SubscribeEvent
    public void onEquip(LivingEquipmentChangeEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !enabled(p)) return;
        EquipmentSlot slot = e.getSlot();
        if (slot != EquipmentSlot.FEET && slot != EquipmentSlot.HEAD) return;
        ItemStack to = e.getTo();
        if (to.getItem() instanceof KnittedArmorItem && Gifts.isGift(to) && !Gifts.wornRewarded(to)) {
            mgr().onGiftWorn(p, slot);
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && enabled(p) && e.getTarget() instanceof Animal a) {
            mgr().onAnimalHit(p, a instanceof Chicken);
        }
    }

    /** «Хулиган»: жители торгуют хуже — бабушки их «настроили». */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof Villager v)) return;
        if (!enabled(p) || BabushkiManager.tier(mgr().getRep(p)) != BabushkiManager.Tier.HOOLIGAN) return;
        if (v.isTrading() || v.isBaby()) return;
        VillagerProfession prof = v.getVillagerData().getProfession();
        if (prof == VillagerProfession.NONE || prof == VillagerProfession.NITWIT || v.getOffers().isEmpty()) return;
        int percent = BabushkiConfig.HOOLIGAN_PRICE_PERCENT.get();
        if (percent <= 0) return;
        for (MerchantOffer offer : v.getOffers()) {
            int base = offer.getBaseCostA().getCount();
            int extra = Math.max(1, Math.round(base * percent / 100f));
            offer.addToSpecialPriceDiff(extra);
        }
        p.displayClientMessage(Component.literal("Житель косится: «Мне тут бабушки про тебя такое рассказали...»")
                .withStyle(s -> s.withColor(0xFF7777)), true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Gossip.onPlayerDeath(p, e.getSource());
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent e) {
        var display = e.getAdvancement().getDisplay();
        if (display != null && display.shouldAnnounceChat()) {
            Gossip.remember(e.getEntity().getGameProfile().getName(),
                    "грамоту получил — «" + display.getTitle().getString() + "»! Ишь, отличник!");
        }
    }
}
