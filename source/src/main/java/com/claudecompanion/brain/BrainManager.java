package com.claudecompanion.brain;

import com.claudecompanion.entity.CompanionEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Связывает напарников в мире с их «мозгами». */
public final class BrainManager {
    private BrainManager() {}

    private static final Map<UUID, Brain> BRAINS = new ConcurrentHashMap<>();
    private static final Map<UUID, CompanionEntity> LOADED = new ConcurrentHashMap<>();

    public static void track(CompanionEntity e) { LOADED.put(e.getUUID(), e); }
    public static void untrack(CompanionEntity e) { LOADED.remove(e.getUUID()); }

    public static Brain brain(CompanionEntity e) {
        return BRAINS.computeIfAbsent(e.getUUID(), id -> new Brain(e.getServer(), e));
    }

    public static void notify(CompanionEntity e, String text) {
        if (e.getServer() != null) brain(e).notify(text);
    }

    public static void remove(CompanionEntity e) {
        untrack(e);
        Brain b = BRAINS.remove(e.getUUID());
        if (b != null) b.close();
    }

    public static void closeAll() {
        BRAINS.values().forEach(Brain::close);
        BRAINS.clear();
        LOADED.clear();
    }

    /** Живые напарники этого игрока. */
    public static List<CompanionEntity> ownedBy(UUID player) {
        List<CompanionEntity> out = new ArrayList<>();
        for (CompanionEntity e : LOADED.values())
            if (e.isAlive() && player.equals(e.getOwnerId())) out.add(e);
        return out;
    }
}
