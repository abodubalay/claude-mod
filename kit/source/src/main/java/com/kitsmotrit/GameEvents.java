package com.kitsmotrit;

import com.kitsmotrit.entity.MaidWhale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameEvents {
    /** Когда у игрока следующая проверка шанса (в тиках сервера). */
    private final Map<UUID, Long> nextCheck = new HashMap<>();

    /** Курицам и коровам — желание смотреть на кита (важнее, чем смотреть на игрока). */
    @SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.world.entity.animal.Chicken
                || event.getEntity() instanceof net.minecraft.world.entity.animal.Cow) {
            net.minecraft.world.entity.Mob m = (net.minecraft.world.entity.Mob) event.getEntity();
            m.goalSelector.addGoal(1, new com.kitsmotrit.entity.LookAtWhaleGoal(m));
        }
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        KitCommand.register(event.getDispatcher());
    }

    /** Раз в минуту у каждого игрока — шанс 5% (+1% за очко симпатии, максимум 20%). */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        long now = p.server.getTickCount();
        long interval = KitConfig.CHECK_INTERVAL.get() * 20L;
        Long next = nextCheck.get(p.getUUID());
        if (next == null) { nextCheck.put(p.getUUID(), now + interval); return; }
        if (now < next) return;
        nextCheck.put(p.getUUID(), now + interval);
        if (Simpatia.isOff(p) || !canSpawnFor(p) || MaidWhale.ofPlayer(p) != null) return;
        if (p.getRandom().nextDouble() * 100 < Simpatia.chance(p)) MaidWhale.spawnFor(p);
    }

    /** Только на поверхности под открытым небом: не в пещере, не в доме, не под водой. */
    public static boolean canSpawnFor(ServerPlayer p) {
        Level lvl = p.level();
        if (!lvl.dimensionType().hasSkyLight() || lvl.dimensionType().hasCeiling()) return false;
        if (p.isSpectator() || p.isUnderWater() || p.isInWater()) return false;
        BlockPos head = BlockPos.containing(p.getEyePosition());
        return lvl.canSeeSky(head);
    }

    /** Подарок цветка: ПКМ любым цветком, пока кит рядом (в воздух или по блоку). */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (tryGift(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryGift(event.getEntity(), event.getItemStack())) event.setCanceled(true); // цветок не ставится, а дарится
    }

    private boolean tryGift(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!stack.is(ItemTags.FLOWERS)) return false;
        if (!(player instanceof ServerPlayer p)) {
            return false;
        }
        MaidWhale w = MaidWhale.ofPlayer(p);
        if (w == null || w.distanceTo(p) > 64) return false;
        int state = w.getState();
        if (state == MaidWhale.SHY || state == MaidWhale.FLEE || state == MaidWhale.LEAVE) return false;
        if (!p.getAbilities().instabuild) stack.shrink(1);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        w.onFlower(p);
        return true;
    }
}
