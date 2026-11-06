package com.kitsmotrit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Симпатия кита к игроку и выключатель появлений. Хранится в данных игрока — переживает выход из игры. */
public final class Simpatia {
    private Simpatia() {}

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int get(Player p) { return data(p).getInt("kit_simpatia"); }
    public static void set(Player p, int v) { data(p).putInt("kit_simpatia", Math.max(0, v)); }

    public static boolean isOff(Player p) { return data(p).getBoolean("kit_off"); }
    public static void setOff(Player p, boolean off) { data(p).putBoolean("kit_off", off); }

    /** 0 — «Незнакомец», 1 — «Знакомый», 2 — «Друг», 3 — «Лучший друг». */
    public static int level(int s) { return s >= 10 ? 3 : s >= 6 ? 2 : s >= 3 ? 1 : 0; }

    public static String levelName(int s) {
        return switch (level(s)) { case 3 -> "Лучший друг"; case 2 -> "Друг"; case 1 -> "Знакомый"; default -> "Незнакомец"; };
    }

    /** Шанс появления, %: база + 1% за очко, не больше максимума. */
    public static double chance(Player p) {
        return Math.min(KitConfig.MAX_CHANCE.get(), KitConfig.BASE_CHANCE.get() + get(p));
    }
}
