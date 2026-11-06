package com.kino;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** Титры «конца серии»: кто снимался, сколько раз погиб каскадёр и т.д. */
public final class Credits {
    private Credits() {}

    /** Уже посчитанные мобы (чтобы одну корову не считать дважды). */
    private static final Map<UUID, Set<UUID>> SEEN = new HashMap<>();

    /** Раз в 5 секунд: кого игрок встретил рядом. */
    public static void scanCast(ServerPlayer p) {
        CompoundTag cast = KinoData.of(p).getCompound("cast");
        Set<UUID> seen = SEEN.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(20), Mob::isAlive)) {
            if (seen.size() > 2000 || !seen.add(m.getUUID())) continue;
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
            if (id == null) continue;
            String key = id.toString();
            if (cast.size() >= 40 && !cast.contains(key)) continue;
            cast.putInt(key, Math.min(999, cast.getInt(key) + 1));
        }
        KinoData.of(p).put("cast", cast);
    }

    public static long day(ServerPlayer p) {
        return p.level().getDayTime() / 24000L;
    }

    /** Пора ли показать титры (раз в N игровых дней). */
    public static boolean due(ServerPlayer p) {
        if (!KinoConfig.CREDITS.get() || KinoData.isOff(p)) return false;
        CompoundTag d = KinoData.of(p);
        if (!d.contains("lastCreditsDay")) { d.putLong("lastCreditsDay", day(p)); return false; }
        return day(p) - d.getLong("lastCreditsDay") >= KinoConfig.CREDITS_EVERY_DAYS.get();
    }

    public static void show(ServerPlayer p) {
        CompoundTag d = KinoData.of(p);
        CompoundTag cast = d.getCompound("cast");
        List<String> ids = new ArrayList<>(cast.getAllKeys());
        ids.sort((a, b) -> cast.getInt(b) - cast.getInt(a));
        if (ids.size() > 8) ids = ids.subList(0, 8);
        List<Integer> counts = new ArrayList<>();
        for (String id : ids) counts.add(cast.getInt(id));
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new Network.Credits(p.getGameProfile().getName(), ids, counts, d.getInt("deaths"), d.getInt("chickens")));
        // новая «серия»
        d.putLong("lastCreditsDay", day(p));
        d.put("cast", new CompoundTag());
        d.putInt("deaths", 0);
        d.putInt("chickens", 0);
        SEEN.remove(p.getUUID());
    }
}
