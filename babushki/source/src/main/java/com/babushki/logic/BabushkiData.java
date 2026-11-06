package com.babushki.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Всё, что надо помнить между запусками: репутация, где дом, где лавочка, когда что дарили. */
public class BabushkiData extends SavedData {
    public static final String NAME = "babushki";

    public boolean enabled = true;
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public static class PlayerData {
        public String name = "";
        public int rep = 0;

        public boolean firstSleepDone = false;
        public long spawnDueDay = -1;
        @Nullable public BlockPos home;

        public boolean benchEverPlaced = false;
        public boolean benchExists = false;
        @Nullable public BlockPos benchPos;
        public Direction benchFacing = Direction.NORTH;
        public long moveDueDay = -1;

        public long lastGreetDay = -1;
        public long lastSocksDay = -1;
        public long lastScarfDay = -1;
        public long lastAvoskaDay = -1;
        public long lastGossipDay = -1;
        public long lastNightReturnDay = -1;
        public long lastPieTick = -1_000_000;
        public long lastJamTick = -1_000_000;
        public long giftBanUntil = -1;
        public boolean awayAtNight = false;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putInt("rep", rep);
            t.putBoolean("firstSleep", firstSleepDone);
            t.putLong("spawnDue", spawnDueDay);
            if (home != null) t.putLong("home", home.asLong());
            t.putBoolean("benchEver", benchEverPlaced);
            t.putBoolean("benchExists", benchExists);
            if (benchPos != null) t.putLong("bench", benchPos.asLong());
            t.putInt("benchFacing", benchFacing.get2DDataValue());
            t.putLong("moveDue", moveDueDay);
            t.putLong("greet", lastGreetDay);
            t.putLong("socks", lastSocksDay);
            t.putLong("scarf", lastScarfDay);
            t.putLong("avoska", lastAvoskaDay);
            t.putLong("gossip", lastGossipDay);
            t.putLong("nightRet", lastNightReturnDay);
            t.putLong("pie", lastPieTick);
            t.putLong("jam", lastJamTick);
            t.putLong("ban", giftBanUntil);
            t.putBoolean("away", awayAtNight);
            return t;
        }

        static PlayerData load(CompoundTag t) {
            PlayerData d = new PlayerData();
            d.name = t.getString("name");
            d.rep = t.getInt("rep");
            d.firstSleepDone = t.getBoolean("firstSleep");
            d.spawnDueDay = t.getLong("spawnDue");
            if (t.contains("home")) d.home = BlockPos.of(t.getLong("home"));
            d.benchEverPlaced = t.getBoolean("benchEver");
            d.benchExists = t.getBoolean("benchExists");
            if (t.contains("bench")) d.benchPos = BlockPos.of(t.getLong("bench"));
            d.benchFacing = Direction.from2DDataValue(t.getInt("benchFacing"));
            d.moveDueDay = t.getLong("moveDue");
            d.lastGreetDay = t.getLong("greet");
            d.lastSocksDay = t.getLong("socks");
            d.lastScarfDay = t.getLong("scarf");
            d.lastAvoskaDay = t.getLong("avoska");
            d.lastGossipDay = t.getLong("gossip");
            d.lastNightReturnDay = t.getLong("nightRet");
            d.lastPieTick = t.getLong("pie");
            d.lastJamTick = t.getLong("jam");
            d.giftBanUntil = t.getLong("ban");
            d.awayAtNight = t.getBoolean("away");
            return d;
        }
    }

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id, k -> { setDirty(); return new PlayerData(); });
    }

    public Map<UUID, PlayerData> all() { return players; }

    public static BabushkiData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BabushkiData::load, BabushkiData::new, NAME);
    }

    public static BabushkiData load(CompoundTag tag) {
        BabushkiData d = new BabushkiData();
        d.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            try {
                d.players.put(p.getUUID("id"), PlayerData.load(p));
            } catch (Exception ignored) {}
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("enabled", enabled);
        ListTag list = new ListTag();
        players.forEach((id, pd) -> {
            CompoundTag t = pd.save();
            t.putUUID("id", id);
            list.add(t);
        });
        tag.put("players", list);
        return tag;
    }
}
