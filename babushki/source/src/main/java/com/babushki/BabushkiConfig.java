package com.babushki;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Настройки мода. Файл появится в папке config/babushki-common.toml после первого запуска. */
public class BabushkiConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue TALK_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue TALK_RADIUS;
    public static final ForgeConfigSpec.DoubleValue REPLY_CHANCE;
    public static final ForgeConfigSpec.IntValue REPLY_DELAY_SECONDS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_GOSSIP;
    public static final ForgeConfigSpec.BooleanValue ENABLE_NIGHT_COMMENTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TREATS;

    public static final ForgeConfigSpec.DoubleValue PIE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue PIE_CHANCE_VNUCHOK;
    public static final ForgeConfigSpec.IntValue PIE_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.DoubleValue JAM_CHANCE;
    public static final ForgeConfigSpec.IntValue JAM_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.DoubleValue GOSSIP_CHANCE;

    public static final ForgeConfigSpec.DoubleValue NIGHT_COMMENT_CHANCE;
    public static final ForgeConfigSpec.IntValue NIGHT_COMMENT_COOLDOWN_SECONDS;

    public static final ForgeConfigSpec.IntValue SPAWN_DELAY_DAYS;
    public static final ForgeConfigSpec.IntValue HOOLIGAN_PRICE_PERCENT;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHALE_MOD_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHICKEN_MOD_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PARTNER_MOD_KEYWORDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Разговоры").push("talk");
        TALK_COOLDOWN_SECONDS = b.comment("Не чаще, чем раз в столько секунд бабушки комментируют одного игрока")
                .defineInRange("talkCooldownSeconds", 20, 3, 3600);
        TALK_RADIUS = b.comment("Радиус (в блоках), в котором бабушки замечают игрока")
                .defineInRange("talkRadius", 10, 3, 64);
        REPLY_CHANCE = b.comment("Шанс, что вторая бабушка ответит первой (0..1)")
                .defineInRange("replyChance", 0.5, 0.0, 1.0);
        REPLY_DELAY_SECONDS = b.comment("Через сколько секунд отвечает вторая бабушка")
                .defineInRange("replyDelaySeconds", 2, 1, 10);
        b.pop();

        b.comment("Что включено").push("features");
        ENABLE_GOSSIP = b.comment("Сплетни раз в игровой день").define("enableGossip", true);
        ENABLE_NIGHT_COMMENTS = b.comment("Ночные комментарии «из окна»").define("enableNightComments", true);
        ENABLE_TREATS = b.comment("Угощения и подарки").define("enableTreats", true);
        b.pop();

        b.comment("Шансы и частота подарков").push("treats");
        PIE_CHANCE = b.comment("Шанс пирожка, когда игрок голодный или раненый (обычная репутация)")
                .defineInRange("pieChance", 0.3, 0.0, 1.0);
        PIE_CHANCE_VNUCHOK = b.comment("Шанс пирожка на репутации «Внучок»")
                .defineInRange("pieChanceVnuchok", 0.7, 0.0, 1.0);
        PIE_COOLDOWN_MINUTES = b.comment("Пирожок не чаще, чем раз в столько минут")
                .defineInRange("pieCooldownMinutes", 10, 0, 1440);
        JAM_CHANCE = b.comment("Шанс варенья «просто так» (репутация +7 и выше), проверяется при каждом разговоре")
                .defineInRange("jamChance", 0.15, 0.0, 1.0);
        JAM_COOLDOWN_MINUTES = b.comment("Варенье не чаще, чем раз в столько минут")
                .defineInRange("jamCooldownMinutes", 10, 0, 1440);
        GOSSIP_CHANCE = b.comment("Шанс начать сплетню при разговоре (если сегодня ещё не сплетничали)")
                .defineInRange("gossipChance", 0.35, 0.0, 1.0);
        b.pop();

        b.comment("Ночь").push("night");
        NIGHT_COMMENT_CHANCE = b.comment("Шанс, что бабушка выглянет из окна, когда игрок ночью на улице возле дома")
                .defineInRange("nightCommentChance", 0.35, 0.0, 1.0);
        NIGHT_COMMENT_COOLDOWN_SECONDS = b.comment("Не чаще, чем раз в столько секунд")
                .defineInRange("nightCommentCooldownSeconds", 60, 5, 3600);
        b.pop();

        b.comment("Разное").push("misc");
        SPAWN_DELAY_DAYS = b.comment("Через сколько игровых суток после первого сна появляются бабушки (0 = в то же утро)")
                .defineInRange("spawnDelayDays", 1, 0, 30);
        HOOLIGAN_PRICE_PERCENT = b.comment("На сколько процентов дороже торгуют жители с «Хулиганом»")
                .defineInRange("hooliganPricePercent", 25, 0, 300);
        WHALE_MOD_KEYWORDS = b.comment("Слова в id или названии мода про Кита (для сплетен)")
                .defineList("whaleModKeywords", List.of("whale", "kit", "кит"), o -> o instanceof String);
        CHICKEN_MOD_KEYWORDS = b.comment("Слова в id или названии мода про Курицу-повелительницу")
                .defineList("chickenModKeywords", List.of("chicken", "kurica", "kuritsa", "курица", "povelitel"), o -> o instanceof String);
        PARTNER_MOD_KEYWORDS = b.comment("Слова в id или названии мода про напарника")
                .defineList("partnerModKeywords", List.of("partner", "naparnik", "companion", "напарник"), o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }
}
