package com.babushki.logic;

import com.babushki.BabushkiConfig;
import com.babushki.BabushkiMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Сплетни: про других игроков (настоящие события), про мобов и про другие моды. */
public final class Gossip {
    private Gossip() {}

    private record Event(String name, String text) {}
    private static final LinkedList<Event> EVENTS = new LinkedList<>();

    private static Boolean whale, chicken, partner;

    // ---------- запоминаем события ----------
    public static void remember(String name, String text) {
        EVENTS.addFirst(new Event(name, text));
        while (EVENTS.size() > 25) EVENTS.removeLast();
    }

    public static void onPlayerDeath(Player p, DamageSource src) {
        Entity killer = src.getEntity();
        String t;
        if (src.is(DamageTypes.LAVA)) t = "опять в лаву упал! Прямо с головой!";
        else if (src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.ON_FIRE)) t = "погорел! Весь в саже ходил!";
        else if (src.is(DamageTypes.FALL)) t = "с высоты навернулся! Ой-ой-ой!";
        else if (src.is(DamageTypes.DROWN)) t = "утоп, говорят! Чуть-чуть не доплыл!";
        else if (src.is(DamageTypes.STARVE)) t = "с голоду помер! Не кормит никто!";
        else if (killer instanceof Creeper || src.is(DamageTypes.EXPLOSION) || src.is(DamageTypes.PLAYER_EXPLOSION)) t = "его крипер подорвал! Прямо у дома!";
        else if (killer instanceof Zombie) t = "от зомби не убежал! Ноги-то не казённые!";
        else if (killer instanceof Skeleton) t = "его скелет из лука подстрелил! Средь бела дня!";
        else if (killer instanceof Spider) t = "его паук покусал! Я ж говорила — дома сиди!";
        else if (killer instanceof Player k) t = "с " + k.getGameProfile().getName() + " подрался — и проиграл!";
        else t = "опять помер, говорят. Вот молодёжь пошла!";
        remember(p.getGameProfile().getName(), t);
    }

    // ---------- другие моды ----------
    private static boolean modMatches(List<? extends String> keywords) {
        for (IModInfo info : ModList.get().getMods()) {
            String id = info.getModId().toLowerCase(Locale.ROOT);
            if (id.equals(BabushkiMod.MODID) || id.equals("forge") || id.equals("minecraft")) continue;
            String name = info.getDisplayName().toLowerCase(Locale.ROOT);
            for (String k : keywords) {
                String kk = k.toLowerCase(Locale.ROOT);
                if (!kk.isEmpty() && (id.contains(kk) || name.contains(kk))) return true;
            }
        }
        return false;
    }

    private static void detectMods() {
        if (whale != null) return;
        whale = modMatches(BabushkiConfig.WHALE_MOD_KEYWORDS.get());
        chicken = modMatches(BabushkiConfig.CHICKEN_MOD_KEYWORDS.get());
        partner = modMatches(BabushkiConfig.PARTNER_MOD_KEYWORDS.get());
        BabushkiManager.LOG.info("Бабушки ищут другие моды: кит={}, курица={}, напарник={}", whale, chicken, partner);
    }

    // ---------- реплики ----------
    private static final List<String> REACTIONS = List.of(
            "B:Ой, батюшки!", "B:Да ты что!", "B:Я так и знала!", "B:Вот и я говорю!",
            "B:Тьфу, позорище!", "B:Ой, {n}, не говори!");

    private static final List<String> MADE_UP_NAMES = List.of(
            "Петя", "Колька с пятого", "Витька из сорок второй", "Михалыч", "Толик", "Серёжка-очкарик");

    private static final List<String> MADE_UP_EVENTS = List.of(
            "опять в лаву упал!",
            "с крыши свалился, когда дачу свою строил!",
            "корову в дом завёл! Совсем того!",
            "в шахте три дня сидел, мать с ума сходила!",
            "с жителем из-за изумрудов поругался!",
            "свинью в седле катал! Позорище!",
            "яму посреди двора вырыл! Говорит — бассейн!");

    private static final List<Lines.Dialogue> MOBS = Lines.pool(
            "A:Видала, крипер-то с соседней горы совсем распоясался.|B:Шипит и шипит! Никакого уважения!",
            "A:Зомби опять ночью под окнами шаркали.|B:Пьяные, небось.",
            "A:Эндермен этот длинный опять блоки таскает!|B:Ворует, точно ворует!",
            "A:Скелеты эти худющие... Хоть бы покормил их кто.|B:Одни кости, {n}, одни кости!",
            "A:Паук на стене висел, огромный! Глаза — во!|B:Ой, не говори, я аж перекрестилась.",
            "A:Жители в деревне опять цены задрали!|B:Изумруд за хлеб! Грабёж!",
            "A:Ведьма с болота опять зелья варит.|B:Я ей сразу сказала — не пущу!");

    private static final List<Lines.Dialogue> WHALE = Lines.pool(
            "A:Опять эта рыбина в небе висит, в фартуке, тьфу.|B:Кит это, {n}. Летающий.|A:Всё равно тьфу!",
            "A:Кит этот опять над домами плавает. Тень от него — всю картошку мне затенил!|B:Жалобу, жалобу писать!");

    private static final List<Lines.Dialogue> CHICKEN = Lines.pool(
            "A:Говорят, кто-то кур бьёт, вот и прилетела мамаша ихняя.|B:Курица эта главная! Глазищи — во!",
            "A:Курица эта, повелительница... Я ей зерна насыпала, так она кивнула. Как начальница!|B:Уважаемая птица.");

    private static final List<Lines.Dialogue> PARTNER = Lines.pool(
            "A:А с ним всё время этот ходит... напарник.|B:Подозрительный! Живут, говорят, вместе!",
            "A:Напарник-то его хоть вежливый.|B:Вежливый, да. Здоровается. Не то что этот.");

    /** Собрать сплетню для слушателя. */
    public static Lines.Dialogue make(ServerPlayer listener, Random r) {
        detectMods();
        List<Lines.Dialogue> modLines = new ArrayList<>();
        if (whale) modLines.addAll(WHALE);
        if (chicken) modLines.addAll(CHICKEN);
        if (partner) modLines.addAll(PARTNER);

        double roll = r.nextDouble();
        if (!modLines.isEmpty() && roll < 0.35) return Lines.pick(modLines, r);
        if (roll < 0.7) return aboutPeople(listener, r);
        return Lines.pick(MOBS, r);
    }

    private static Lines.Dialogue aboutPeople(ServerPlayer listener, Random r) {
        String me = listener.getGameProfile().getName();
        List<Event> real = new ArrayList<>();
        for (Event e : EVENTS) if (!e.name.equals(me)) real.add(e);

        String name, text;
        if (!real.isEmpty() && r.nextDouble() < 0.8) {
            Event e = Lines.pick(real, r);
            name = e.name; text = e.text;
        } else {
            List<String> names = new ArrayList<>();
            for (ServerPlayer p : listener.server.getPlayerList().getPlayers()) {
                if (!p.getGameProfile().getName().equals(me)) names.add(p.getGameProfile().getName());
            }
            name = !names.isEmpty() && r.nextBoolean() ? Lines.pick(names, r) : Lines.pick(MADE_UP_NAMES, r);
            text = Lines.pick(MADE_UP_EVENTS, r);
        }
        return Lines.Dialogue.of("A:А этот " + name + ", говорят, " + text, Lines.pick(REACTIONS, r));
    }
}
