package com.kitsmotrit;

import java.util.*;

/**
 * Все фразы кита. Выдаются «мешком»: каждая по разу в случайном порядке,
 * повтор — только когда прошли все (свой мешок у каждого игрока).
 */
public final class Phrases {
    private Phrases() {}

    /** Смущение (обычные, из задания). */
    public static final List<String> SHY = List.of(
            "Н-не смотри на меня так!",
            "Я вовсе не за тобой следил! Просто мимо проплывал!",
            "Б-бака!",
            "Это не потому, что ты мне нравишься, понял?!",
            "Хмф! Подумаешь...",
            // новые, в духе тиктоковских цундере-мемов
            "Я НЕ цундере, понял?! Совсем не цундере!",
            "Н-не пойми меня неправильно!",
            "Бака! Бака-бака-бака!",
            "Я-я не краснею! Это закат так светит!",
            "Отвернись! Сейчас же! ...Ну ладно, можешь чуть-чуть посмотреть.",
            "Моё сердце стучит не из-за тебя! Это давление высоты!",
            "Ты что, дурак?! ...Ладно, не дурак. Немножко.",
            "Если кому-нибудь расскажешь — проглочу! Целиком!",
            "Я-я просто проплывал мимо... третий раз за день!",
            "Ня~ ...Ой. Ты ничего не слышал! НИЧЕГО!",
            "Я тебя не ждал. Ни капельки. Вот ни столечко.",
            "Хватит так мило смотреть! Это запрещено!",
            "Ничего я не смущаюсь! Киты вообще не смущаются!",
            "Это не слёзы! Это просто... море из глаз!",
            "Ну и смотри! Мне всё равно! ...Нет, не смотри!"
    );

    /** «Знакомый» (3–5) — добавляется к обычным. */
    public static final String ACQUAINTANCE = "Т-ты опять здесь? Ну... ладно, можешь остаться.";
    /** «Друг» (6–9) — добавляется к остальным. */
    public static final String FRIEND = "Я тут... просто проверить, не голодный ли ты";

    /** При появлении. */
    public static final List<String> GREET = List.of(
            "...",
            "*делает вид, что не смотрит*",
            "Я тут просто облака считаю. Не мешай.",
            "Хмф. Опять ты.",
            "*тихонько напевает и косится на тебя*"
    );

    /** Удар. */
    public static final List<String> PUNCH = List.of(
            "Ч-что ты делаешь, идиот?!",
            "Ай! Ты что творишь, бака?!",
            "Это вообще-то больно! ...Нет, не больно. Но обидно!",
            "Я запомню это! Навсегда! Ну... до завтра точно!"
    );

    /** Уход, если не смотреть. */
    public static final List<String> LEAVE = List.of(
            "...Я ещё вернусь. Н-не потому что хочу!",
            "Хмф! Мне пора. И не скучай! ...Ну, если только чуть-чуть.",
            "Я уплываю не потому, что ты на меня не смотришь! Просто дела!",
            "Пока не смотришь — уплыву. Вот так вот!"
    );
    /** Уход «лучшего друга». */
    public static final String BEST_FRIEND_LEAVE = "П-пока... Береги себя, бака.";
    public static final String FLOWER = "Э-это просто так! Не придумывай!";
    public static final String LEVEL_UP = "...Хм! Ты мне не нравишься! Совсем!";

    private static final Map<String, Deque<String>> BAGS = new HashMap<>();
    private static final Random RANDOM = new Random();

    /** Следующая фраза из мешка (без повторов, пока не выпадут все). */
    public static synchronized String next(UUID player, String bagName, List<String> pool) {
        String key = player + ":" + bagName + ":" + pool.size();
        Deque<String> bag = BAGS.get(key);
        if (bag == null || bag.isEmpty()) {
            List<String> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, RANDOM);
            bag = new ArrayDeque<>(shuffled);
            BAGS.put(key, bag);
        }
        return bag.poll();
    }

    public static List<String> shyPool(int level) {
        List<String> pool = new ArrayList<>(SHY);
        if (level >= 1) pool.add(ACQUAINTANCE);
        if (level >= 2) pool.add(FRIEND);
        return pool;
    }

    public static List<String> greetPool(int level) {
        List<String> pool = new ArrayList<>(GREET);
        if (level >= 1) pool.add(ACQUAINTANCE);
        if (level >= 2) pool.add(FRIEND);
        return pool;
    }
}
