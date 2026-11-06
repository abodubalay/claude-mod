package com.smenazhanra.server;

import com.smenazhanra.Config;
import com.smenazhanra.Genre;
import com.smenazhanra.StatKeys;
import com.smenazhanra.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Главная логика: таймер, предупреждение, запуск, заморозка мобов, конец мини-игры. */
public class GenreManager {
    public static final String DATA = "smenazhanra";
    public static final String FROZEN_TAG = "smenazhanra_frozen";
    public static final int WARNING_TICKS = 100; // 5 секунд
    /** Запас времени сверх длительности, после которого сервер сам закроет мини-игру. */
    private static final int GRACE_TICKS = 30 * 20;
    private static final double FREEZE_RADIUS = 32;

    public enum Phase { WARNING, PLAYING }

    public static class Session {
        final Genre genre;
        Phase phase = Phase.WARNING;
        int ticks = WARNING_TICKS;
        Vec3 pos;
        final Set<UUID> frozen = new HashSet<>();

        Session(Genre g) { genre = g; }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Короткая защита после возврата (пока на экране помехи). */
    private static final Map<UUID, Long> GRACE = new HashMap<>();
    private static final int GRACE_AFTER_TICKS = 60;

    // ------------------------------------------------------------------ данные игрока

    /** Постоянные данные игрока (сохраняются в файле игрока, переносятся после смерти). */
    public static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(DATA)) root.put(DATA, new CompoundTag());
        return root.getCompound(DATA);
    }

    public static int intervalTicks() {
        return Config.INTERVAL_MINUTES.get() * 60 * 20;
    }

    public static boolean isPlaying(Player p) {
        Session s = SESSIONS.get(p.getUUID());
        return s != null && s.phase == Phase.PLAYING;
    }

    /** Неуязвимость: во время отсчёта, мини-игры и 3 секунды после возврата. */
    public static boolean isProtected(ServerPlayer p) {
        if (SESSIONS.containsKey(p.getUUID())) return true;
        Long until = GRACE.get(p.getUUID());
        return until != null && until > p.server.overworld().getGameTime();
    }

    public static boolean hasSession(Player p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    public static boolean isFrozenByAnyone(UUID mob) {
        for (Session s : SESSIONS.values()) if (s.frozen.contains(mob)) return true;
        return false;
    }

    public static Genre randomGenre(RandomSource r, boolean onlyEnabled) {
        List<Genre> list = new ArrayList<>();
        for (Genre g : Genre.values()) if (!onlyEnabled || Config.isEnabled(g)) list.add(g);
        if (list.isEmpty()) return null;
        return list.get(r.nextInt(list.size()));
    }

    // ------------------------------------------------------------------ запуск

    /** Начинает 5-секундное предупреждение, после которого откроется мини-игра. */
    public static boolean begin(ServerPlayer p, Genre g) {
        if (SESSIONS.containsKey(p.getUUID())) return false;
        Session s = new Session(g);
        SESSIONS.put(p.getUUID(), s);
        freezeMobs(p, s);
        Net.toPlayer(p, new Net.Countdown(g.id));
        return true;
    }

    private static void startPlaying(ServerPlayer p, Session s) {
        s.phase = Phase.PLAYING;
        int seconds = Config.durationSeconds(s.genre);
        s.ticks = seconds * 20 + GRACE_TICKS;
        s.pos = p.position();
        p.stopRiding();
        freezeMobs(p, s);
        Net.toPlayer(p, new Net.Open(s.genre.id, seconds, Config.ALLOW_SKIP.get(), collectStats(p), p.getRandom().nextLong()));
        broadcastStatus(p);
    }

    /** Реальная статистика игрока для обвинений в суде. */
    private static int[] collectStats(ServerPlayer p) {
        ServerStatsCounter st = p.getStats();
        int[] v = new int[StatKeys.COUNT];
        v[StatKeys.CHICKENS] = st.getValue(Stats.ENTITY_KILLED.get(EntityType.CHICKEN));
        v[StatKeys.PIGS] = st.getValue(Stats.ENTITY_KILLED.get(EntityType.PIG));
        v[StatKeys.COWS] = st.getValue(Stats.ENTITY_KILLED.get(EntityType.COW));
        v[StatKeys.VILLAGERS] = st.getValue(Stats.ENTITY_KILLED.get(EntityType.VILLAGER));
        v[StatKeys.CREEPERS] = st.getValue(Stats.ENTITY_KILLED.get(EntityType.CREEPER));
        v[StatKeys.KILLED_BY_CREEPER] = st.getValue(Stats.ENTITY_KILLED_BY.get(EntityType.CREEPER));
        v[StatKeys.DEATHS] = st.getValue(Stats.CUSTOM.get(Stats.DEATHS));
        v[StatKeys.JUMPS] = st.getValue(Stats.CUSTOM.get(Stats.JUMP));
        v[StatKeys.FISH] = st.getValue(Stats.CUSTOM.get(Stats.FISH_CAUGHT));
        v[StatKeys.TRADES] = st.getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER));
        v[StatKeys.SLEEP] = st.getValue(Stats.CUSTOM.get(Stats.SLEEP_IN_BED));
        v[StatKeys.PLAY_HOURS] = st.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 72000;
        int mined = 0;
        for (Block b : BuiltInRegistries.BLOCK) mined += st.getValue(Stats.BLOCK_MINED.get(b));
        v[StatKeys.MINED] = mined;
        v[StatKeys.DIRT] = st.getValue(Stats.BLOCK_MINED.get(Blocks.DIRT)) + st.getValue(Stats.BLOCK_MINED.get(Blocks.GRASS_BLOCK));
        v[StatKeys.LAVA_DEATHS] = data(p).getInt("lavaDeaths");
        return v;
    }

    // ------------------------------------------------------------------ каждый тик

    public static void tickPlayer(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) {
            if (s.phase == Phase.WARNING) {
                if (p.tickCount % 10 == 0) freezeMobs(p, s);
                if (--s.ticks <= 0) startPlaying(p, s);
            } else {
                holdInPlace(p, s);
                if (p.tickCount % 10 == 0) freezeMobs(p, s);
                if (--s.ticks <= 0) forceEnd(p, "Время мини-игры вышло.");
            }
        } else {
            WorldData wd = WorldData.get(p.server);
            if (wd.autoEnabled && !p.isSpectator()) {
                CompoundTag d = data(p);
                int left = d.contains("next") ? d.getInt("next") : intervalTicks();
                left = Math.min(left, intervalTicks()); // если интервал в конфиге уменьшили
                left--;
                if (left <= 0) {
                    if (p.isAlive() && !p.isSleeping()) {
                        Genre g = randomGenre(p.getRandom(), true);
                        if (g != null) begin(p, g);
                        left = intervalTicks();
                    } else {
                        left = 20; // подождём, пока игрок оживёт / проснётся
                    }
                }
                d.putInt("next", left);
            }
        }
        Punishments.tick(p);
    }

    /** Тело игрока стоит на месте. */
    private static void holdInPlace(ServerPlayer p, Session s) {
        p.fallDistance = 0;
        p.setAirSupply(p.getMaxAirSupply());
        p.clearFire();
        if (s.pos != null && p.position().distanceToSqr(s.pos) > 0.04) {
            p.teleportTo(s.pos.x, s.pos.y, s.pos.z);
            p.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Мобы вокруг замирают (NoAI) — помечаем их, чтобы потом точно разморозить. */
    private static void freezeMobs(ServerPlayer p, Session s) {
        AABB box = p.getBoundingBox().inflate(FREEZE_RADIUS);
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, box)) {
            if (!m.isNoAi()) {
                m.setNoAi(true);
                m.addTag(FROZEN_TAG);
                m.setDeltaMovement(Vec3.ZERO);
                s.frozen.add(m.getUUID());
            }
            if (m.getTarget() == p) m.setTarget(null);
        }
    }

    private static void unfreeze(ServerPlayer p, Session s) {
        ServerLevel lvl = p.serverLevel();
        for (UUID id : s.frozen) {
            Entity e = lvl.getEntity(id);
            if (e instanceof Mob m) unfreezeMob(m);
        }
        s.frozen.clear();
    }

    public static void unfreezeMob(Mob m) {
        if (m.getTags().contains(FROZEN_TAG)) {
            m.removeTag(FROZEN_TAG);
            m.setNoAi(false);
        }
    }

    // ------------------------------------------------------------------ конец

    private static Session end(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s != null) {
            unfreeze(p, s);
            broadcastStatus(p);
            GRACE.put(p.getUUID(), p.server.overworld().getGameTime() + GRACE_AFTER_TICKS);
        }
        return s;
    }

    /** Мини-игра закончилась нормально — выдаём результат. */
    public static void finish(ServerPlayer p, Genre g, int outcome, int[] values) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase != Phase.PLAYING || s.genre != g) return;
        end(p);
        Rewards.apply(p, g, outcome, values);
    }

    /** Игрок сбежал по Esc (coward) или экран закрылся сам. */
    public static void skip(ServerPlayer p, boolean coward) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.phase != Phase.PLAYING) return;
        end(p);
        if (coward) {
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("[Смена жанра] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("Трус сбежал из жанра! ").withStyle(ChatFormatting.RED))
                    .append(Component.literal("(" + p.getGameProfile().getName() + ")").withStyle(ChatFormatting.GRAY)), false);
            CompoundTag d = data(p);
            int sooner = Config.COWARD_MINUTES.get() * 60 * 20;
            d.putInt("next", Math.min(d.contains("next") ? d.getInt("next") : intervalTicks(), sooner));
        }
    }

    /** Принудительное завершение (таймаут, смерть, выход). */
    public static void forceEnd(ServerPlayer p, String reason) {
        Session s = end(p);
        if (s != null) {
            Net.toPlayer(p, new Net.ForceClose());
            if (reason != null) p.sendSystemMessage(Component.literal("[Смена жанра] " + reason).withStyle(ChatFormatting.GRAY));
        }
    }

    public static void onLogout(ServerPlayer p) {
        end(p);
    }

    public static void broadcastStatus(ServerPlayer p) {
        Net.toAll(new Net.Status(p.getUUID(), isPlaying(p), Punishments.isCriminal(p)));
    }

    /** При входе нового игрока — рассказать ему про всех остальных. */
    public static void sendAllStatuses(ServerPlayer to) {
        for (ServerPlayer o : to.server.getPlayerList().getPlayers()) {
            Net.toPlayer(to, new Net.Status(o.getUUID(), isPlaying(o), Punishments.isCriminal(o)));
        }
    }

    public static void clearAll() {
        SESSIONS.clear();
        GRACE.clear();
    }
}
