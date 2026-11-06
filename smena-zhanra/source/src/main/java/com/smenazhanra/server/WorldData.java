package com.smenazhanra.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Сохраняется вместе с миром: включена ли автоматическая смена жанра (/zhanr on|off). */
public class WorldData extends SavedData {
    public boolean autoEnabled = true;

    public static WorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorldData::load, WorldData::new, "smenazhanra");
    }

    static WorldData load(CompoundTag tag) {
        WorldData d = new WorldData();
        d.autoEnabled = !tag.contains("auto") || tag.getBoolean("auto");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("auto", autoEnabled);
        return tag;
    }
}
