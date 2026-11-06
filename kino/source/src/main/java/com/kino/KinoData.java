package com.kino;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Данные игрока для мода (переживают выход из игры). */
public final class KinoData {
    private KinoData() {}

    public static CompoundTag of(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains("kino")) persisted.put("kino", new CompoundTag());
        return persisted.getCompound("kino");
    }

    public static boolean isOff(Player p) { return of(p).getBoolean("off"); }
    public static void setOff(Player p, boolean off) { of(p).putBoolean("off", off); }

    public static long getLong(Player p, String key) { return of(p).getLong(key); }
    public static void setLong(Player p, String key, long v) { of(p).putLong(key, v); }

    public static int getInt(Player p, String key) { return of(p).getInt(key); }
    public static void addInt(Player p, String key) { of(p).putInt(key, of(p).getInt(key) + 1); }
}
