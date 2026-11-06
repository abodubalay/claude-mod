package com.kino;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/** Эпичные кат-сцены: редко, коротко, безопасно, пропускаются любой клавишей. */
public final class Cutscenes {
    private Cutscenes() {}

    public enum Action { DIRT, BREAD, TABLE, TREE, SLEEP, DOOR, ANIMAL, DIAMOND }

    private static final Map<Action, List<String>> TITLES = new EnumMap<>(Action.class);
    static {
        TITLES.put(Action.DIRT, List.of("ЛЕГЕНДА РОДИЛАСЬ", "ЗЕМЛЯ ЗАПОМНИТ ЭТОТ ДЕНЬ", "ОДИН БЛОК. ОДНА СУДЬБА", "ОН КОПНУЛ. И МИР ВЗДРОГНУЛ"));
        TITLES.put(Action.BREAD, List.of("ОН ЕЛ ХЛЕБ. И ЭТО ИЗМЕНИЛО ВСЁ", "ГОЛОД ОТСТУПИЛ. НА ВРЕМЯ", "ПОСЛЕДНИЙ БАТОН", "ХЛЕБ. ЗЕРНО. ВЕЧНОСТЬ"));
        TITLES.put(Action.TABLE, List.of("И СОЗДАЛ ОН ВЕРСТАК", "НАЧАЛО ИМПЕРИИ", "ЧЕТЫРЕ ДОСКИ. ОДНА МЕЧТА", "ТАК НАЧИНАЛИСЬ ЛЕГЕНДЫ"));
        TITLES.put(Action.TREE, List.of("ДЕРЕВО ПАЛО. НО НЕ ЕГО ДУХ", "ЛЕС ЗАПОМНИТ ЕГО ИМЯ", "ОДИН УДАР. ОДНА ЛЕГЕНДА", "БРЕВНО. ИЛИ ЧТО-ТО БОЛЬШЕЕ?"));
        TITLES.put(Action.SLEEP, List.of("ДАЖЕ ГЕРОЯМ НУЖЕН ОТДЫХ", "НОЧЬ. ТИШИНА. ПОДУШКА", "ЗАВТРА ВСЁ РЕШИТСЯ", "СОН ГЕРОЯ"));
        TITLES.put(Action.DOOR, List.of("ЗА ЭТОЙ ДВЕРЬЮ... ДОМ", "ОН ОТКРЫЛ ДВЕРЬ. И СУДЬБУ", "ПОРОГ НОВОЙ ЭРЫ", "СКРИП, КОТОРЫЙ УСЛЫШАЛ МИР"));
        TITLES.put(Action.ANIMAL, List.of("ДРУЖБА, КОТОРАЯ ПЕРЕЖИВЁТ ВЕКА", "ДВА СЕРДЦА. ОДНА МОРКОВКА", "ВЕРНОСТЬ НЕ КУПИШЬ. ЕЁ КОРМЯТ", "НАЧАЛО ВЕЛИКОЙ ДРУЖБЫ"));
        TITLES.put(Action.DIAMOND, List.of("СУДЬБА ВЫБРАЛА ЕГО", "ДРАГОЦЕННОСТЬ. НАКОНЕЦ-ТО", "БЛЕСК, ИЗМЕНИВШИЙ ВСЁ", "ОН НАШЁЛ ЕГО. ОН НАШЁЛ АЛМАЗ"));
    }
    private static final String SUBTITLE = "...а потом он пошёл копать дальше";
    private static final Random RANDOM = new Random();

    /** Кто сейчас в кат-сцене: до какого тика сервера и какие мобы заморожены. */
    private record Active(long endTick, List<Mob> frozen) {}
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    public static boolean isInScene(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }

    /** Подходящее действие: проверяем паузу, шанс и безопасность. */
    public static void tryTrigger(ServerPlayer p, Action action) {
        if (!KinoConfig.CUTSCENES.get() || KinoData.isOff(p) || isInScene(p)) return;
        long now = System.currentTimeMillis();
        if (action == Action.DIAMOND) { // алмаз — всегда, но не чаще раза в 10 минут
            if (now - KinoData.getLong(p, "lastDiamondScene") < KinoConfig.minutes(KinoConfig.DIAMOND_COOLDOWN_MIN)) return;
        } else {
            if (now - KinoData.getLong(p, "lastScene") < KinoConfig.minutes(KinoConfig.SCENE_COOLDOWN_MIN)) return;
            if (RANDOM.nextDouble() * 100 >= KinoConfig.SCENE_CHANCE.get()) return;
        }
        if (!isSafe(p, action)) { KinoMod.LOG.info("[Кино] {}: кат-сцена ({}) пропущена — сейчас опасно", p.getGameProfile().getName(), action); return; }
        start(p, action);
    }

    /** Не мешаем, если опасно: враги рядом, бой, падение, лава, меньше 6 сердец. */
    public static boolean isSafe(ServerPlayer p, Action action) {
        if (p.getHealth() < 12f) return false;
        if (p.isInLava() || p.isOnFire()) return false;
        if (action != Action.SLEEP && !p.onGround() && (p.fallDistance > 1.5f || p.getDeltaMovement().y < -0.4)) return false;
        if (p.tickCount - p.getLastHurtByMobTimestamp() < 200 && p.getLastHurtByMob() != null) return false;
        if (p.tickCount - p.getLastHurtMobTimestamp() < 200 && p.getLastHurtMob() != null) return false;
        return p.level().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(16), m -> m.isAlive()).isEmpty();
    }

    /** Запустить кат-сцену (команда /kino scene — без проверок). */
    public static void start(ServerPlayer p, Action action) {
        List<String> titles = TITLES.get(action);
        String title = titles.get(RANDOM.nextInt(titles.size()));
        String sub = RANDOM.nextDouble() < 0.2 ? SUBTITLE : "";
        int ticks = 60 + RANDOM.nextInt(41); // 3–5 секунд
        long now = System.currentTimeMillis();
        KinoData.setLong(p, action == Action.DIAMOND ? "lastDiamondScene" : "lastScene", now);
        if (action == Action.DIAMOND) KinoData.setLong(p, "lastScene", Math.max(KinoData.getLong(p, "lastScene"), now));

        // мобы вокруг замирают
        List<Mob> frozen = new ArrayList<>();
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.isAlive() && !m.isNoAi())) {
            m.setNoAi(true);
            frozen.add(m);
        }
        ACTIVE.put(p.getUUID(), new Active(p.server.getTickCount() + ticks + 5, frozen));
        KinoMod.LOG.info("[Кино] {}: кат-сцена ({}) «{}»", p.getGameProfile().getName(), action, title);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Network.Scene(title, sub, ticks));
    }

    public static Action randomAction() {
        Action[] a = Action.values();
        return a[RANDOM.nextInt(a.length)];
    }

    /** Конец кат-сцены (по времени или пропуск). */
    public static void end(ServerPlayer p) {
        Active a = ACTIVE.remove(p.getUUID());
        if (a == null) return;
        for (Mob m : a.frozen) if (m.isAlive()) m.setNoAi(false);
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = server.getTickCount();
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            Active a = ACTIVE.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) { // вышел — размораживаем
                for (Mob m : a.frozen) if (m.isAlive()) m.setNoAi(false);
                ACTIVE.remove(id);
            } else if (now >= a.endTick) end(p);
        }
    }
}
