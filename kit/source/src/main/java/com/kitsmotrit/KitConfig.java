package com.kitsmotrit;

import net.minecraftforge.common.ForgeConfigSpec;

/** Настройки: config/kitsmotrit-common.toml. */
public final class KitConfig {
    private KitConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue BASE_CHANCE, MAX_CHANCE, GAZE_SECONDS, WHALE_SCALE;
    public static final ForgeConfigSpec.IntValue CHECK_INTERVAL, STAY_MIN, STAY_MAX, STAY_MAX_TOTAL;
    public static final ForgeConfigSpec.BooleanValue SOUND, SKY_DARKENING, CHAT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Кит смотрит").push("kit");
        BASE_CHANCE = b.comment("Базовый шанс появления кита при каждой проверке, %").defineInRange("baseChance", 5.0, 0.0, 100.0);
        CHECK_INTERVAL = b.comment("Как часто проверять шанс, секунд").defineInRange("checkIntervalSeconds", 60, 5, 3600);
        MAX_CHANCE = b.comment("Максимальный шанс с учётом симпатии, %").defineInRange("maxChance", 20.0, 0.0, 100.0);
        STAY_MIN = b.comment("Сколько кит висит, если на него не смотреть: от (секунд)").defineInRange("stayMinSeconds", 30, 5, 3600);
        STAY_MAX = b.comment("...до (секунд)").defineInRange("stayMaxSeconds", 60, 5, 3600);
        STAY_MAX_TOTAL = b.comment("Максимальное время с учётом симпатии, секунд").defineInRange("stayMaxTotalSeconds", 120, 5, 3600);
        GAZE_SECONDS = b.comment("Сколько секунд смотреть на кита, чтобы он смутился").defineInRange("gazeSeconds", 3.0, 0.5, 60.0);
        WHALE_SCALE = b.comment("Размер кита (1.0 — около 20 блоков в длину)").defineInRange("whaleScale", 1.0, 0.2, 3.0);
        SOUND = b.comment("Звук пения при появлении").define("sound", true);
        SKY_DARKENING = b.comment("Небо темнеет, пока кит рядом").define("skyDarkening", true);
        CHAT = b.comment("Сообщения кита в чат").define("chatMessages", true);
        b.pop();
        SPEC = b.build();
    }
}
