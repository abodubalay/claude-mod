package com.kino;

import net.minecraftforge.common.ForgeConfigSpec;

/** Настройки: config/kino-common.toml. Можно менять без пересборки мода. */
public final class KinoConfig {
    private KinoConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SCENE_COOLDOWN_MIN, SCENE_CHANCE, DIAMOND_COOLDOWN_MIN, SITCOM_COOLDOWN_MIN, SITCOM_VOLUME;
    public static final ForgeConfigSpec.IntValue CREDITS_EVERY_DAYS;
    public static final ForgeConfigSpec.BooleanValue CUTSCENES, LAUGHTER, APPLAUSE, CREDITS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Кино").push("kino");
        SCENE_COOLDOWN_MIN = b.comment("Пауза между кат-сценами, минут реального времени").defineInRange("sceneCooldownMinutes", 20.0, 0.0, 1440.0);
        SCENE_CHANCE = b.comment("Шанс кат-сцены на подходящее действие после паузы, %").defineInRange("sceneChancePercent", 10.0, 0.0, 100.0);
        DIAMOND_COOLDOWN_MIN = b.comment("Кат-сцена за алмаз — не чаще, минут").defineInRange("diamondCooldownMinutes", 10.0, 0.0, 1440.0);
        SITCOM_COOLDOWN_MIN = b.comment("Пауза между реакциями ситкома (смех, аплодисменты...), минут").defineInRange("sitcomCooldownMinutes", 3.0, 0.0, 1440.0);
        CREDITS_EVERY_DAYS = b.comment("Титры — раз в столько игровых дней (когда ложишься спать)").defineInRange("creditsEveryDays", 3, 1, 1000);
        SITCOM_VOLUME = b.comment("Громкость звуков ситкома (0.0–1.0)").defineInRange("sitcomVolume", 1.0, 0.0, 1.0);
        CUTSCENES = b.comment("Кат-сцены").define("cutscenes", true);
        LAUGHTER = b.comment("Закадровый смех и прочие голоса зала («ооо», «ааа», «уууу»)").define("laughter", true);
        APPLAUSE = b.comment("Аплодисменты").define("applause", true);
        CREDITS = b.comment("Титры в конце «серии»").define("credits", true);
        b.pop();
        SPEC = b.build();
    }

    public static long minutes(ForgeConfigSpec.DoubleValue v) {
        return (long) (v.get() * 60_000);
    }
}
