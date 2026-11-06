package com.smenazhanra;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Настройки мода. Файл появится после первого запуска: config/smenazhanra-common.toml
 */
public class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue INTERVAL_MINUTES;
    public static final ForgeConfigSpec.BooleanValue NOVELLA, SUD, RITM, VIKTORINA, FIGHTING;
    public static final ForgeConfigSpec.IntValue STORY_SECONDS, RHYTHM_SECONDS, FIGHT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SKIP;
    public static final ForgeConfigSpec.IntValue COWARD_MINUTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Как часто меняется жанр").push("timing");
        INTERVAL_MINUTES = b.comment("Через сколько минут игры происходит смена жанра (по умолчанию 20)")
                .defineInRange("intervalMinutes", 20, 1, 1440);
        COWARD_MINUTES = b.comment("Если сбежать из жанра по Esc, следующая смена придёт через столько минут")
                .defineInRange("cowardNextShiftMinutes", 5, 1, 1440);
        b.pop();

        b.comment("Какие жанры включены (false = жанр выключен)").push("genres");
        NOVELLA = b.comment("Визуальная новелла «Свинья моей мечты»").define("novella", true);
        SUD = b.comment("Суд «Крипер против игрока»").define("sud", true);
        RITM = b.comment("Ритм-игра «Копай в такт»").define("ritm", true);
        VIKTORINA = b.comment("Викторина «Кто хочет стать изумрудным миллионером»").define("viktorina", true);
        FIGHTING = b.comment("Файтинг «Куриный Комбат»").define("fighting", true);
        b.pop();

        b.comment("Длительность мини-игр в секундах").push("duration");
        STORY_SECONDS = b.comment("Максимум для новеллы, суда и викторины (когда время выйдет, игра закончится сама)")
                .defineInRange("storySeconds", 120, 30, 600);
        RHYTHM_SECONDS = b.comment("Длительность ритм-игры").defineInRange("rhythmSeconds", 60, 20, 300);
        FIGHT_SECONDS = b.comment("Длительность раунда файтинга").defineInRange("fightSeconds", 60, 20, 300);
        b.pop();

        b.push("rules");
        ALLOW_SKIP = b.comment("Можно ли сбежать из мини-игры клавишей Esc").define("allowEscSkip", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean isEnabled(Genre g) {
        return switch (g) {
            case NOVELLA -> NOVELLA.get();
            case SUD -> SUD.get();
            case RITM -> RITM.get();
            case VIKTORINA -> VIKTORINA.get();
            case FIGHTING -> FIGHTING.get();
        };
    }

    public static int durationSeconds(Genre g) {
        return switch (g) {
            case RITM -> RHYTHM_SECONDS.get();
            case FIGHTING -> FIGHT_SECONDS.get();
            default -> STORY_SECONDS.get();
        };
    }
}
