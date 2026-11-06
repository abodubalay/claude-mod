package com.babushki.logic;

import com.babushki.BabushkiConfig;
import com.babushki.ModRegistry;
import com.babushki.block.BenchBlock;
import com.babushki.entity.Anim;
import com.babushki.entity.BabushkaEntity;
import com.babushki.entity.Role;
import com.babushki.item.KnittedArmorItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Мозг мода: когда бабушкам появляться, что говорить и что дарить. */
public class BabushkiManager {
    public static final Logger LOG = LogUtils.getLogger();
    private static final BabushkiManager INSTANCE = new BabushkiManager();
    public static BabushkiManager get() { return INSTANCE; }

    public enum Tier { HOOLIGAN, NORMAL, VNUCHOK }
    public enum GiftType { PIE, SOCKS, SCARF, JAM, AVOSKA }

    public static Tier tier(int rep) {
        if (rep <= -4) return Tier.HOOLIGAN;
        if (rep >= 4) return Tier.VNUCHOK;
        return Tier.NORMAL;
    }

    public static String tierName(int rep) {
        return switch (tier(rep)) {
            case HOOLIGAN -> "Хулиган";
            case VNUCHOK -> "Внучок";
            default -> "Обычный";
        };
    }

    /** Пара бабушек одного игрока. */
    public static class Pair {
        final UUID owner;
        final BabushkaEntity valya, zina;
        long lastSpeak = -1000;
        boolean leaving;
        Pair(UUID owner, BabushkaEntity v, BabushkaEntity z) { this.owner = owner; this.valya = v; this.zina = z; }
        public BabushkaEntity get(Role r) { return r == Role.VALYA ? valya : zina; }
        boolean alive() { return valya.isAlive() && zina.isAlive() && !valya.isRemoved() && !zina.isRemoved(); }
        Vec3 center() { return valya.position().add(zina.position()).scale(0.5); }
        double distTo(Player p) { return Math.min(valya.distanceTo(p), zina.distanceTo(p)); }
        void discard() { valya.discard(); zina.discard(); }
    }

    /** Временное (не сохраняемое) состояние игрока. */
    private static class Temp {
        long lastComment = -100000;
        long lastNightComment = -100000;
        long lastAnimalHit = -100000;
        boolean animalWasChicken;
        long lastDig = -100000;
        long lastHit = -100000;
        long lastClickTalk = -100000;
        long lastNoBench = -100000;
        boolean wasSleeping;
        boolean wasCrouching;
        int spotFailures;
    }

    private final Map<UUID, Pair> pairs = new HashMap<>();
    private final Map<UUID, Temp> temps = new HashMap<>();
    private final Random rnd = new Random();
    private MinecraftServer server;
    private int tickCounter;

    private Temp temp(UUID id) { return temps.computeIfAbsent(id, k -> new Temp()); }

    private BabushkiData data() { return BabushkiData.get(server); }

    private ServerLevel overworld() { return server.overworld(); }

    private long now() { return overworld().getGameTime(); }

    private long day() { return overworld().getDayTime() / 24000L; }

    private long timeOfDay() { return overworld().getDayTime() % 24000L; }

    /** Бабушки сидят с 6:00 до ~18:20. */
    public boolean isDaytime() { return timeOfDay() < 12300; }

    private static boolean dayDue(long due, long day) {
        return due >= 0 && (day >= due || day < due - 30);
    }

    public void onServerStart(MinecraftServer s) {
        server = s;
        pairs.clear();
        temps.clear();
        Scheduler.clear();
    }

    public void onServerStop() {
        pairs.clear();
        temps.clear();
        Scheduler.clear();
        server = null;
    }

    // ==================================================================
    //                               ТИК
    // ==================================================================
    public void tick(MinecraftServer s) {
        if (server == null) server = s;
        Scheduler.tick();
        BabushkiData data = data();

        // Приветствие (присесть) проверяем каждый тик, чтобы не пропустить короткое нажатие Shift.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Temp t = temp(p.getUUID());
            boolean crouch = p.isShiftKeyDown();
            if (crouch && !t.wasCrouching && data.enabled) onCrouch(p);
            t.wasCrouching = crouch;
        }

        if (++tickCounter % 20 != 0) return;

        if (!data.enabled) {
            discardAll();
            return;
        }

        long day = day();
        boolean daytime = isDaytime();
        ServerLevel ow = overworld();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BabushkiData.PlayerData d = data.get(p.getUUID());
            if (!p.getGameProfile().getName().equals(d.name)) { d.name = p.getGameProfile().getName(); data.setDirty(); }
            trackSleep(p, d, day, data);
            trackHome(p, d, day, data);
            if (p.level() != ow) continue;
            handleBench(p, d, day, daytime, data);
            handlePair(p, d, daytime);
            if (!daytime) handleNight(p, d, day, data);
            else if (d.awayAtNight) { d.awayAtNight = false; data.setDirty(); }
        }

        // Убираем пары, чьи хозяева ушли с сервера или из мира.
        Iterator<Map.Entry<UUID, Pair>> it = pairs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pair> e = it.next();
            Pair pair = e.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(e.getKey());
            if (!pair.alive() || owner == null || owner.level() != ow) {
                pair.discard();
                it.remove();
            }
        }

        if (daytime) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() == ow && !p.isSpectator()) tryComment(p, data, day);
            }
        }
    }

    private void discardAll() {
        for (Pair p : pairs.values()) p.discard();
        pairs.clear();
    }

    // ==================================================================
    //                   Сон, дом, лавочка, появление
    // ==================================================================
    private void trackSleep(ServerPlayer p, BabushkiData.PlayerData d, long day, BabushkiData data) {
        Temp t = temp(p.getUUID());
        if (p.isSleeping()) {
            t.wasSleeping = true;
            p.getSleepingPos().ifPresent(pos -> {
                if (p.level() == overworld() && (d.home == null || !d.home.equals(pos)) && !d.firstSleepDone) {
                    d.home = pos.immutable();
                    data.setDirty();
                }
            });
        } else if (t.wasSleeping) {
            t.wasSleeping = false;
            if (!d.firstSleepDone && p.level() == overworld()) {
                d.firstSleepDone = true;
                d.spawnDueDay = day + BabushkiConfig.SPAWN_DELAY_DAYS.get();
                if (d.home == null) d.home = p.blockPosition();
                data.setDirty();
                p.sendSystemMessage(Component.literal("Где-то во дворе зашептались: «Новенький! Надо бы присмотреться...»")
                        .withStyle(s -> s.withColor(0xAAAAAA).withItalic(true)));
            }
        }
    }

    private void trackHome(ServerPlayer p, BabushkiData.PlayerData d, long day, BabushkiData data) {
        if (!d.firstSleepDone) return;
        BlockPos rp = p.getRespawnPosition();
        if (rp == null || p.getRespawnDimension() != Level.OVERWORLD) return;
        if (d.home != null && d.home.equals(rp)) return;
        d.home = rp.immutable();
        if (d.benchEverPlaced && d.benchExists && d.benchPos != null && d.benchPos.distSqr(rp) > 24 * 24) {
            d.moveDueDay = day + 1;
            p.sendSystemMessage(Component.literal("Бабушки узнали, что ты переехал. Завтра переберутся поближе...")
                    .withStyle(s -> s.withColor(0xAAAAAA).withItalic(true)));
        }
        data.setDirty();
    }

    private void handleBench(ServerPlayer p, BabushkiData.PlayerData d, long day, boolean daytime, BabushkiData data) {
        ServerLevel level = overworld();
        // Проверка: на месте ли лавочка.
        if (d.benchExists && d.benchPos != null && level.isLoaded(d.benchPos)) {
            BlockState st = level.getBlockState(d.benchPos);
            if (!(st.getBlock() instanceof BenchBlock) || st.getValue(BenchBlock.PART) != BenchBlock.Part.LEFT) {
                d.benchExists = false;
                if (d.moveDueDay < 0) d.moveDueDay = day + 1;
                data.setDirty();
            }
        }
        boolean active = d.firstSleepDone && dayDue(d.spawnDueDay, day);
        if (!active || !daytime || d.home == null) return;
        boolean needNew = !d.benchEverPlaced || dayDue(d.moveDueDay, day);
        if (!needNew) return;
        if (p.blockPosition().distSqr(d.home) > 96 * 96 || !level.isLoaded(d.home)) return;
        Temp t = temp(p.getUUID());
        if (t.spotFailures > 0 && tickCounter % 600 != 0) return; // не ищем место слишком часто

        // Убираем старую лавочку (переезд).
        if (d.benchExists && d.benchPos != null) {
            BlockState st = level.getBlockState(d.benchPos);
            if (st.getBlock() instanceof BenchBlock) {
                BlockPos right = d.benchPos.relative(BenchBlock.partnerDirection(st));
                level.setBlock(right, Blocks.AIR.defaultBlockState(), 3 | 32);
                level.setBlock(d.benchPos, Blocks.AIR.defaultBlockState(), 3 | 32);
            }
            d.benchExists = false;
        }

        Spot spot = findSpot(level, d.home, 5, 10, t.spotFailures >= 3);
        if (spot == null) { t.spotFailures++; return; }
        t.spotFailures = 0;
        placeBench(level, spot, d, data);
        Pair old = pairs.remove(p.getUUID());
        if (old != null) old.discard();
    }

    private void placeBench(ServerLevel level, Spot spot, BabushkiData.PlayerData d, BabushkiData data) {
        BenchBlock.placeWhole(level, spot.pos, spot.facing, ModRegistry.BENCH.get());
        d.benchPos = spot.pos.immutable();
        d.benchFacing = spot.facing;
        d.benchExists = true;
        d.benchEverPlaced = true;
        d.moveDueDay = -1;
        data.setDirty();
    }

    public record Spot(BlockPos pos, Direction facing) {}

    /**
     * Ищет ровное место под лавочку (2 блока) в кольце minR..maxR вокруг центра.
     * Лавочка смотрит в сторону центра (дома).
     */
    @Nullable
    public static Spot findSpot(ServerLevel level, BlockPos center, int minR, int maxR, boolean relaxed) {
        List<Spot> good = new ArrayList<>();
        List<Spot> covered = new ArrayList<>();
        int dy = relaxed ? 8 : 4;
        for (int dx = -maxR; dx <= maxR; dx++) {
            for (int dz = -maxR; dz <= maxR; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < minR || dist > maxR) continue;
                int x = center.getX() + dx, z = center.getZ() + dz;
                // Ищем сверху вниз первую клетку, где можно стоять.
                BlockPos pos = null;
                for (int y = center.getY() + dy; y >= center.getY() - dy; y--) {
                    BlockPos c = new BlockPos(x, y, z);
                    if (canStand(level, c)) { pos = c; break; }
                }
                if (pos == null) continue;
                Direction facing = Math.abs(dx) > Math.abs(dz)
                        ? (dx > 0 ? Direction.WEST : Direction.EAST)
                        : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                BlockPos right = pos.relative(facing.getClockWise());
                if (!canStand(level, pos) || !canStand(level, right)) continue;
                if (!relaxed) {
                    // Ровная земля: перед лавочкой тоже можно стоять на той же высоте.
                    if (!canStand(level, pos.relative(facing)) || !canStand(level, right.relative(facing))) continue;
                }
                // Лучше под открытым небом (во дворе), а не под крышей.
                (level.canSeeSky(pos) && level.canSeeSky(right) ? good : covered).add(new Spot(pos, facing));
            }
        }
        if (good.isEmpty()) good = covered;
        if (good.isEmpty()) return null;
        // Предпочитаем землю на уровне дома (а не крышу или яму).
        int best = Integer.MAX_VALUE;
        for (Spot s : good) best = Math.min(best, Math.abs(s.pos.getY() - center.getY()));
        final int limit = best + 1;
        List<Spot> level0 = good.stream().filter(s -> Math.abs(s.pos.getY() - center.getY()) <= limit).toList();
        return level0.get(new Random().nextInt(level0.size()));
    }

    private static boolean canStand(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        BlockState at = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        boolean replaceable = at.isAir() || at.canBeReplaced();
        return replaceable && at.getFluidState().isEmpty()
                && (above.isAir() || above.canBeReplaced()) && above.getFluidState().isEmpty()
                && below.isFaceSturdy(level, pos.below(), Direction.UP)
                && !below.is(BlockTags.LEAVES) && below.getFluidState().isEmpty();
    }

    private void handlePair(ServerPlayer owner, BabushkiData.PlayerData d, boolean daytime) {
        Pair pair = pairs.get(owner.getUUID());
        if (pair != null && !pair.alive()) { pair.discard(); pairs.remove(owner.getUUID()); pair = null; }
        ServerLevel level = overworld();
        long day = day();
        boolean active = d.firstSleepDone && dayDue(d.spawnDueDay, day) && d.benchEverPlaced && d.benchPos != null;
        boolean near = active && owner.blockPosition().distSqr(d.benchPos) <= 80 * 80 && level.isLoaded(d.benchPos);
        boolean shouldExist = active && daytime && near;

        if (shouldExist && pair == null) {
            pair = spawnPair(level, owner.getUUID(), d);
            pairs.put(owner.getUUID(), pair);
        } else if (!shouldExist && pair != null && !pair.leaving) {
            if (!daytime && near && owner.distanceToSqr(pair.center()) < 40 * 40) {
                pair.leaving = true;
                Pair fp = pair;
                Lines.Dialogue dlg = Lines.pick(Lines.LEAVING, rnd);
                speak(fp, dlg, null, Speech.Mood.NEUTRAL);
                fp.valya.leaveSoon(80);
                fp.zina.leaveSoon(80);
            } else {
                pair.discard();
                pairs.remove(owner.getUUID());
            }
        } else if (pair != null && !pair.leaving) {
            BlockPos bench = d.benchExists ? d.benchPos : null;
            pair.valya.setHome(owner.getUUID(), bench, d.benchFacing);
            pair.zina.setHome(owner.getUUID(), bench, d.benchFacing);
        }
    }

    private Pair spawnPair(ServerLevel level, UUID owner, BabushkiData.PlayerData d) {
        BabushkaEntity v = new BabushkaEntity(ModRegistry.BABUSHKA.get(), level);
        BabushkaEntity z = new BabushkaEntity(ModRegistry.BABUSHKA.get(), level);
        v.setRole(Role.VALYA);
        z.setRole(Role.ZINA);
        BlockPos bench = d.benchExists ? d.benchPos : null;
        for (BabushkaEntity b : List.of(v, z)) {
            b.setHome(owner, bench, d.benchFacing);
            if (bench != null) {
                b.sitDown();
            } else {
                // Лавочки нет — стоят там, где она была.
                BlockPos at = b.getRole() == Role.VALYA ? d.benchPos : d.benchPos.relative(d.benchFacing.getClockWise());
                BlockPos stand = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
                b.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, d.benchFacing.toYRot(), 0);
            }
            level.addFreshEntity(b);
        }
        return new Pair(owner, v, z);
    }

    @Nullable
    public BabushkaEntity findPartner(BabushkaEntity b) {
        for (Pair p : pairs.values()) {
            if (p.valya == b) return p.zina;
            if (p.zina == b) return p.valya;
        }
        return null;
    }

    @Nullable
    private Pair pairOf(BabushkaEntity b) {
        for (Pair p : pairs.values()) if (p.valya == b || p.zina == b) return p;
        return null;
    }

    @Nullable
    public Pair nearestPair(Player p, double radius) {
        Pair best = null;
        double bestD = radius;
        for (Pair pair : pairs.values()) {
            if (!pair.alive() || pair.leaving || pair.valya.level() != p.level()) continue;
            double dd = pair.distTo(p);
            if (dd <= bestD) { bestD = dd; best = pair; }
        }
        return best;
    }

    // ==================================================================
    //                              Ночь
    // ==================================================================
    private void handleNight(ServerPlayer p, BabushkiData.PlayerData d, long day, BabushkiData data) {
        if (!d.benchEverPlaced || !dayDue(d.spawnDueDay, day)) return;
        BlockPos home = d.home != null ? d.home : d.benchPos;
        if (home == null) return;
        double dist = Math.sqrt(p.blockPosition().distSqr(home));
        Temp t = temp(p.getUUID());
        if (dist > 40 && !d.awayAtNight) { d.awayAtNight = true; data.setDirty(); }

        if (d.awayAtNight && dist < 16) {
            d.awayAtNight = false;
            if (d.lastNightReturnDay != day) {
                d.lastNightReturnDay = day;
                changeRep(p, -1, "пришёл домой ночью");
                if (BabushkiConfig.ENABLE_NIGHT_COMMENTS.get()) {
                    sayToPlayer(p, Lines.pick(Lines.NIGHT_RETURN, rnd), Speech.Mood.WINDOW);
                    t.lastNightComment = now();
                }
            }
            data.setDirty();
            return;
        }

        if (!BabushkiConfig.ENABLE_NIGHT_COMMENTS.get()) return;
        if (dist > 48 || !p.level().canSeeSky(p.blockPosition())) return;
        if (now() - t.lastNightComment < BabushkiConfig.NIGHT_COMMENT_COOLDOWN_SECONDS.get() * 20L) return;
        t.lastNightComment = now();
        if (rnd.nextDouble() < BabushkiConfig.NIGHT_COMMENT_CHANCE.get()) {
            sayToPlayer(p, Lines.pick(Lines.NIGHT_WINDOW, rnd), Speech.Mood.WINDOW);
        }
    }

    // ==================================================================
    //                            Разговоры
    // ==================================================================
    private int delay() { return BabushkiConfig.REPLY_DELAY_SECONDS.get() * 20; }

    private Collection<ServerPlayer> audience(Pair pair) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!pair.valya.isAlive() && !pair.zina.isAlive()) return out;
        double r = BabushkiConfig.TALK_RADIUS.get() + 8;
        Vec3 c = pair.center();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() == pair.valya.level() && p.position().distanceTo(c) <= r) out.add(p);
        }
        return out;
    }

    /** Сказать диалог от пары. Возвращает разобранные реплики. */
    private List<Speech.Resolved> speak(Pair pair, Lines.Dialogue d, @Nullable Role preferred, Speech.Mood mood) {
        Role first = Speech.firstSpeaker(d, preferred, rnd);
        List<Speech.Resolved> lines = Speech.resolve(d, first);
        Speech.say(lines, () -> audience(pair), pair::get, mood, delay());
        pair.lastSpeak = now() + (long) lines.size() * delay();
        return lines;
    }

    private void sayToPlayer(ServerPlayer p, Lines.Dialogue d, Speech.Mood mood) {
        Role first = Speech.firstSpeaker(d, null, rnd);
        UUID id = p.getUUID();
        Speech.say(Speech.resolve(d, first), () -> {
            ServerPlayer sp = server == null ? null : server.getPlayerList().getPlayer(id);
            return sp == null ? List.of() : List.of(sp);
        }, r -> null, mood, delay());
    }

    /** Ворчание с возможным ответом второй бабушки. */
    private void speakWithReply(Pair pair, Lines.Dialogue d, Speech.Mood mood) {
        if (d.lines().size() == 1 && (mood == Speech.Mood.SCOLD || mood == Speech.Mood.PRAISE)
                && rnd.nextDouble() < BabushkiConfig.REPLY_CHANCE.get()) {
            String reply = Lines.pick(mood == Speech.Mood.PRAISE ? Lines.REPLY_PRAISE : Lines.REPLY_SCOLD, rnd);
            d = new Lines.Dialogue(List.of(d.lines().get(0), "B:" + reply));
        }
        speak(pair, d, null, mood);
    }

    private void tryComment(ServerPlayer p, BabushkiData data, long day) {
        Pair pair = nearestPair(p, BabushkiConfig.TALK_RADIUS.get());
        if (pair == null) return;
        Temp t = temp(p.getUUID());
        long now = now();
        if (now - t.lastComment < BabushkiConfig.TALK_COOLDOWN_SECONDS.get() * 20L) return;
        if (now < pair.lastSpeak + 60) return;
        BabushkiData.PlayerData d = data.get(p.getUUID());
        Tier tier = tier(d.rep);

        // 1) Подарки
        if (BabushkiConfig.ENABLE_TREATS.get() && tier != Tier.HOOLIGAN && now >= d.giftBanUntil) {
            GiftType g = chooseGift(p, d, day, now);
            if (g != null && startGift(pair, p, g, false)) {
                t.lastComment = now;
                return;
            }
        }

        // 2) Сплетни раз в день
        if (BabushkiConfig.ENABLE_GOSSIP.get() && d.lastGossipDay != day && tier != Tier.HOOLIGAN
                && rnd.nextDouble() < BabushkiConfig.GOSSIP_CHANCE.get()) {
            d.lastGossipDay = day;
            data.setDirty();
            speak(pair, Gossip.make(p, rnd), null, Speech.Mood.CHAT);
            t.lastComment = now;
            return;
        }

        // 3) Ворчание по ситуации
        List<Choice> choices = new ArrayList<>();
        boolean noHelmet = p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        if (now - t.lastAnimalHit < 400)
            choices.add(new Choice(t.animalWasChicken ? Lines.CHICKEN_HIT : Lines.ANIMAL_HIT, Speech.Mood.SCOLD, 1000));
        if (now - t.lastDig < 200) choices.add(new Choice(Lines.DIGGING, Speech.Mood.SCOLD, 6));
        if (p.getHealth() <= 8) choices.add(new Choice(Lines.LOW_HEALTH, Speech.Mood.SCOLD, 5));
        if (p.getMainHandItem().getItem() instanceof SwordItem) choices.add(new Choice(Lines.SWORD, Speech.Mood.SCOLD, 5));
        if (p.level().isRainingAt(p.blockPosition().above())) choices.add(new Choice(Lines.RAIN, Speech.Mood.SCOLD, 5));
        if (noHelmet) choices.add(new Choice(Lines.NO_HELMET, Speech.Mood.SCOLD, 3));
        if (countArmor(p) >= 2) choices.add(new Choice(Lines.ARMOR, Speech.Mood.SCOLD, 3));
        if (countBlocks(p) >= 128) choices.add(new Choice(Lines.BLOCKS, Speech.Mood.SCOLD, 3));
        switch (tier) {
            case HOOLIGAN -> choices.add(new Choice(Lines.HOOLIGAN, Speech.Mood.SCOLD, 8));
            case VNUCHOK -> {
                choices.add(new Choice(Lines.VNUCHOK, Speech.Mood.PRAISE, 8));
                choices.add(new Choice(Lines.CHITCHAT, Speech.Mood.CHAT, 2));
            }
            default -> choices.add(new Choice(Lines.CHITCHAT, Speech.Mood.CHAT, 2));
        }
        choices.add(new Choice(Lines.PASSING, Speech.Mood.SCOLD, p.getDeltaMovement().horizontalDistanceSqr() > 0.001 ? 3 : 1));

        Choice c = weighted(choices);
        speakWithReply(pair, Lines.pick(c.pool, rnd), c.mood);
        t.lastComment = now;
    }

    private record Choice(List<Lines.Dialogue> pool, Speech.Mood mood, int weight) {}

    private Choice weighted(List<Choice> list) {
        int total = 0;
        for (Choice c : list) total += c.weight;
        int r = rnd.nextInt(Math.max(1, total));
        for (Choice c : list) { r -= c.weight; if (r < 0) return c; }
        return list.get(list.size() - 1);
    }

    private static int countArmor(Player p) {
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = p.getItemBySlot(s);
            if (st.getItem() instanceof ArmorItem && !(st.getItem() instanceof KnittedArmorItem)) n++;
        }
        return n;
    }

    private static int countBlocks(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof BlockItem) n += s.getCount();
        return n;
    }

    private boolean inSnow(ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        BlockState at = p.level().getBlockState(pos);
        BlockState below = p.level().getBlockState(pos.below());
        if (at.is(Blocks.SNOW) || at.is(Blocks.POWDER_SNOW) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW)
                || below.is(Blocks.SNOW)) return true;
        Biome biome = p.level().getBiome(pos).value();
        return biome.coldEnoughToSnow(pos) && p.level().isRaining();
    }

    @Nullable
    private GiftType chooseGift(ServerPlayer p, BabushkiData.PlayerData d, long day, long now) {
        int rep = d.rep;
        boolean hungry = p.getFoodData().getFoodLevel() < 10 || p.getHealth() <= 8;
        if (hungry && now - d.lastPieTick >= BabushkiConfig.PIE_COOLDOWN_MINUTES.get() * 1200L) {
            double chance = tier(rep) == Tier.VNUCHOK ? BabushkiConfig.PIE_CHANCE_VNUCHOK.get() : BabushkiConfig.PIE_CHANCE.get();
            if (rnd.nextDouble() < chance) return GiftType.PIE;
        }
        boolean noBoots = p.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        boolean cold = inSnow(p) || timeOfDay() >= 11000 || p.level().isRainingAt(p.blockPosition().above());
        if (rep >= 0 && noBoots && cold && d.lastSocksDay != day) return GiftType.SOCKS;
        if (rep >= 4 && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && d.lastScarfDay != day) return GiftType.SCARF;
        if (rep >= 10 && d.lastAvoskaDay != day) return GiftType.AVOSKA;
        if (rep >= 7 && now - d.lastJamTick >= BabushkiConfig.JAM_COOLDOWN_MINUTES.get() * 1200L
                && rnd.nextDouble() < BabushkiConfig.JAM_CHANCE.get()) return GiftType.JAM;
        return null;
    }

    /**
     * Бабушка говорит фразу и правда дарит: встаёт, подходит, протягивает руку, бросает подарок под ноги.
     * @param forced для команды /babushki gift — не трогаем кулдауны
     */
    public boolean startGift(Pair pair, ServerPlayer p, GiftType type, boolean forced) {
        Lines.Dialogue dlg = switch (type) {
            case PIE -> rnd.nextInt(7) == 0 ? Lines.PIE_WATCH : Lines.pick(Lines.PIE, rnd);
            case SOCKS -> Lines.pick(Lines.SOCKS, rnd);
            case SCARF -> Lines.pick(Lines.SCARF, rnd);
            case JAM -> Lines.pick(Lines.JAM, rnd);
            case AVOSKA -> Lines.pick(Lines.AVOSKA, rnd);
        };
        Role first = Speech.firstSpeaker(dlg, null, rnd);
        // Если первая бабушка занята — пусть начинает другая (кроме диалогов с явными ролями).
        if (pair.get(first).isBusy() && !dlg.lines().get(0).startsWith("V:") && !dlg.lines().get(0).startsWith("Z:")) {
            first = first.other();
        }
        List<Speech.Resolved> lines = Speech.resolve(dlg, first);
        // Дарит тот, к кому обратились (вторая реплика), иначе тот, кто говорит.
        Role giver = lines.size() >= 2 ? lines.get(1).speaker() : lines.get(0).speaker();
        BabushkaEntity giverEntity = pair.get(giver);
        if (giverEntity.isBusy()) return false;

        ItemStack item = switch (type) {
            case PIE -> new ItemStack(ModRegistry.PIROZHOK.get());
            case SOCKS -> new ItemStack(ModRegistry.SOCKS.get());
            case SCARF -> new ItemStack(ModRegistry.SCARF.get());
            case JAM -> new ItemStack(ModRegistry.JAM.get());
            case AVOSKA -> new ItemStack(ModRegistry.AVOSKA.get());
        };
        Gifts.mark(item, giver);
        boolean watch = dlg == Lines.PIE_WATCH;
        boolean ok = giverEntity.startGift(p, List.of(item), watch, () ->
                giverEntity.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6f, 1.2f));
        if (!ok) return false;
        Speech.say(lines, () -> audience(pair), pair::get, Speech.Mood.GIFT, delay());
        pair.lastSpeak = now() + (long) lines.size() * delay();

        if (!forced) {
            BabushkiData data = data();
            BabushkiData.PlayerData d = data.get(p.getUUID());
            long day = day();
            switch (type) {
                case PIE -> d.lastPieTick = now();
                case SOCKS -> d.lastSocksDay = day;
                case SCARF -> d.lastScarfDay = day;
                case JAM -> d.lastJamTick = now();
                case AVOSKA -> d.lastAvoskaDay = day;
            }
            data.setDirty();
        }
        return true;
    }

    // ==================================================================
    //                            Репутация
    // ==================================================================
    public void changeRep(ServerPlayer p, int delta, String reason) {
        BabushkiData data = data();
        BabushkiData.PlayerData d = data.get(p.getUUID());
        int before = d.rep;
        d.rep = Math.max(-10, Math.min(10, d.rep + delta));
        data.setDirty();
        if (d.rep == before) return;
        String sign = delta > 0 ? "+" + delta : String.valueOf(delta);
        int color = delta > 0 ? 0x55FF55 : 0xFF5555;
        p.displayClientMessage(Component.literal("Репутация у бабушек " + sign + " (" + reason + "): " + d.rep + " — " + tierName(d.rep))
                .withStyle(s -> s.withColor(color)), true);
        if (tier(before) != tier(d.rep)) {
            p.sendSystemMessage(Component.literal("Теперь бабушки считают тебя: «" + tierName(d.rep) + "»")
                    .withStyle(s -> s.withColor(color).withBold(true)));
        }
    }

    public void setRep(ServerPlayer p, int value) {
        BabushkiData data = data();
        data.get(p.getUUID()).rep = Math.max(-10, Math.min(10, value));
        data.setDirty();
    }

    public int getRep(ServerPlayer p) { return data().get(p.getUUID()).rep; }

    private void onCrouch(ServerPlayer p) {
        Pair pair = nearestPair(p, 4);
        if (pair == null) return;
        BabushkiData data = data();
        BabushkiData.PlayerData d = data.get(p.getUUID());
        long day = day();
        if (d.lastGreetDay == day) return;
        d.lastGreetDay = day;
        data.setDirty();
        changeRep(p, 1, "поздоровался");
        speak(pair, Lines.pick(Lines.GREETED, rnd), null, Speech.Mood.PRAISE);
        temp(p.getUUID()).lastComment = now();
    }

    public void onBabushkaHit(BabushkaEntity b, ServerPlayer p) {
        Temp t = temp(p.getUUID());
        if (now() - t.lastHit < 10) return;
        t.lastHit = now();
        changeRep(p, -3, "ударил бабушку");
        b.playAnim(Anim.FINGER_WAG, 50);
        b.getLookControl().setLookAt(p, 30f, 30f);
        Pair pair = pairOf(b);
        if (pair != null) speak(pair, Lines.pick(Lines.HIT, rnd), b.getRole(), Speech.Mood.SCOLD);
        t.lastComment = now();
    }

    public InteractionResult onBabushkaInteract(BabushkaEntity b, ServerPlayer p, ItemStack held) {
        Pair pair = pairOf(b);
        if (pair == null) return InteractionResult.PASS;
        Temp t = temp(p.getUUID());
        if (held.is(ModRegistry.PIROZHOK.get())) {
            if (!p.getAbilities().instabuild) held.shrink(1);
            changeRep(p, 2, "подарил пирожок");
            speak(pair, Lines.pick(Lines.GOT_PIE, rnd), b.getRole(), Speech.Mood.PRAISE);
            t.lastComment = now();
            return InteractionResult.CONSUME;
        }
        if (held.is(ItemTags.FLOWERS) || held.is(ItemTags.SMALL_FLOWERS) || held.is(ItemTags.TALL_FLOWERS)) {
            if (!p.getAbilities().instabuild) held.shrink(1);
            changeRep(p, 1, "подарил цветы");
            speak(pair, Lines.pick(Lines.GOT_FLOWERS, rnd), b.getRole(), Speech.Mood.PRAISE);
            t.lastComment = now();
            return InteractionResult.CONSUME;
        }
        if (now() - t.lastClickTalk > 60) {
            t.lastClickTalk = now();
            Tier tier = tier(getRep(p));
            List<Lines.Dialogue> pool = switch (tier) {
                case HOOLIGAN -> Lines.TALK_CLICK_HOOLIGAN;
                case VNUCHOK -> Lines.TALK_CLICK_VNUCHOK;
                default -> Lines.TALK_CLICK;
            };
            speak(pair, Lines.pick(pool, rnd), b.getRole(), tier == Tier.VNUCHOK ? Speech.Mood.PRAISE : Speech.Mood.SCOLD);
        }
        return InteractionResult.CONSUME;
    }

    public void onAnimalHit(ServerPlayer p, boolean chicken) {
        if (nearestPair(p, BabushkiConfig.TALK_RADIUS.get() * 2) == null) return;
        Temp t = temp(p.getUUID());
        t.lastAnimalHit = now();
        t.animalWasChicken = chicken;
    }

    public void onBlockBroken(ServerPlayer p, BlockPos pos, BlockState state) {
        if (server == null) return;
        // Сломали чью-то лавочку?
        if (state.getBlock() instanceof BenchBlock) {
            BlockPos left = BenchBlock.leftPos(pos, state);
            BabushkiData data = data();
            for (Map.Entry<UUID, BabushkiData.PlayerData> e : data.all().entrySet()) {
                BabushkiData.PlayerData d = e.getValue();
                if (d.benchExists && left.equals(d.benchPos)) {
                    d.benchExists = false;
                    d.moveDueDay = day() + 1;
                    data.setDirty();
                    changeRep(p, -2, "сломал лавочку");
                    Pair pair = pairs.get(e.getKey());
                    if (pair != null && pair.alive()) {
                        speak(pair, Lines.pick(Lines.BENCH_BROKEN, rnd), null, Speech.Mood.SCOLD);
                        pair.valya.setHome(e.getKey(), null, d.benchFacing);
                        pair.zina.setHome(e.getKey(), null, d.benchFacing);
                    }
                    temp(p.getUUID()).lastComment = now();
                }
            }
            return;
        }
        if (nearestPair(p, BabushkiConfig.TALK_RADIUS.get()) != null) temp(p.getUUID()).lastDig = now();
    }

    public void onBenchPlaced(ServerPlayer p, BlockPos pos, BlockState state) {
        if (server == null || p.level() != overworld()) return;
        if (state.getValue(BenchBlock.PART) != BenchBlock.Part.LEFT) return;
        BabushkiData data = data();
        BabushkiData.PlayerData d = data.get(p.getUUID());
        if (!d.firstSleepDone) return; // бабушек у этого игрока ещё нет
        d.benchPos = pos.immutable();
        d.benchFacing = state.getValue(BenchBlock.FACING);
        d.benchExists = true;
        d.benchEverPlaced = true;
        d.moveDueDay = -1;
        data.setDirty();
        Pair pair = pairs.get(p.getUUID());
        if (pair != null && pair.alive()) {
            for (BabushkaEntity b : List.of(pair.valya, pair.zina)) {
                b.setHome(p.getUUID(), d.benchPos, d.benchFacing);
                b.goToBench();
            }
            speak(pair, Lines.pick(Lines.NEW_BENCH, rnd), null, Speech.Mood.CHAT);
        }
    }

    public void onGiftTossed(ServerPlayer p, ItemStack stack) {
        Pair pair = nearestPair(p, 24);
        if (pair == null) return;
        Role giver = Gifts.giver(stack);
        Gifts.unmark(stack);
        BabushkiData data = data();
        BabushkiData.PlayerData d = data.get(p.getUUID());
        d.giftBanUntil = now() + 24000;
        data.setDirty();
        changeRep(p, -2, "выбросил подарок");
        speak(pair, Lines.THREW_GIFT, giver, Speech.Mood.SCOLD);
        BabushkaEntity b = pair.get(giver != null ? giver : Role.VALYA);
        b.playAnim(Anim.FINGER_WAG, 40);
        temp(p.getUUID()).lastComment = now();
    }

    public void onGiftPieEaten(ServerPlayer p, ItemStack stack) {
        Pair pair = nearestPair(p, BabushkiConfig.TALK_RADIUS.get());
        if (pair == null) return;
        for (BabushkaEntity b : List.of(pair.valya, pair.zina)) if (b.isWatching(p.getUUID())) b.markEaten();
        changeRep(p, 1, "съел пирожок при бабушке");
        speak(pair, Lines.ATE_PIE, Gifts.giver(stack), Speech.Mood.PRAISE);
        temp(p.getUUID()).lastComment = now();
    }

    public void onGiftWorn(ServerPlayer p, EquipmentSlot slot) {
        Pair pair = nearestPair(p, BabushkiConfig.TALK_RADIUS.get());
        if (pair == null) return;
        ItemStack worn = p.getItemBySlot(slot);
        Role giver = Gifts.giver(worn);
        Gifts.setWornRewarded(worn);
        changeRep(p, 1, slot == EquipmentSlot.FEET ? "надел носки" : "надел шарф");
        speak(pair, slot == EquipmentSlot.FEET ? Lines.WEARS_GIFT : Lines.WEARS_SCARF, giver, Speech.Mood.PRAISE);
        temp(p.getUUID()).lastComment = now();
    }

    // ==================================================================
    //                            Команды
    // ==================================================================
    public String commandSpawn(ServerPlayer p) {
        if (p.level() != overworld()) return "Бабушки живут только в обычном мире (не в Аду и не в Энде).";
        ServerLevel level = overworld();
        Spot spot = findSpot(level, p.blockPosition(), 2, 6, false);
        if (spot == null) spot = findSpot(level, p.blockPosition(), 2, 8, true);
        if (spot == null) return "Не нашлось ровного места рядом. Отойди на ровную площадку и попробуй ещё раз.";
        BabushkiData data = data();
        BabushkiData.PlayerData d = data.get(p.getUUID());
        // Старая лавочка остаётся стоять, бабушки просто пересядут на новую.
        d.firstSleepDone = true;
        d.spawnDueDay = day();
        if (d.home == null) d.home = p.blockPosition();
        placeBench(level, spot, d, data);
        Pair old = pairs.remove(p.getUUID());
        if (old != null) old.discard();
        if (!isDaytime()) {
            return "Лавочка поставлена! Но сейчас ночь — бабушки дома, придут утром (или напиши /time set day).";
        }
        return "Лавочка с бабушками поставлена рядом!";
    }

    public String commandGift(ServerPlayer p, @Nullable GiftType type) {
        Pair pair = nearestPair(p, 32);
        if (pair == null) return null;
        GiftType g = type != null ? type : GiftType.values()[rnd.nextInt(GiftType.values().length)];
        if (!startGift(pair, p, g, true)) return "";
        return g.name();
    }

    public boolean commandGossip(ServerPlayer p) {
        Pair pair = nearestPair(p, 32);
        if (pair == null) return false;
        speak(pair, Gossip.make(p, rnd), null, Speech.Mood.CHAT);
        return true;
    }

    public void setEnabled(boolean on) {
        BabushkiData data = data();
        data.enabled = on;
        data.setDirty();
        if (!on) discardAll();
    }
}
