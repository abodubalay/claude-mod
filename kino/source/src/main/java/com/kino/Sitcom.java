package com.kino;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Реакции зала. Одна реакция на событие, не чаще раза в 3 минуты (общая пауза). */
public final class Sitcom {
    private Sitcom() {}

    public static final int LAUGH = 0, LAUGH_OOH = 1, OOH_CHUCKLE = 2, CHEER = 3, AWW = 4, APPLAUSE = 5, BOO = 6;

    public static boolean isApplause(int type) { return type == CHEER || type == APPLAUSE; }

    public static void react(ServerPlayer p, int type, boolean force) {
        if (KinoData.isOff(p) && !force) return;
        if (isApplause(type) ? !KinoConfig.APPLAUSE.get() : !KinoConfig.LAUGHTER.get()) { if (!force) return; }
        long now = System.currentTimeMillis();
        if (!force && now - KinoData.getLong(p, "lastSitcom") < KinoConfig.minutes(KinoConfig.SITCOM_COOLDOWN_MIN)) return;
        KinoData.setLong(p, "lastSitcom", now);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Network.Sitcom(type));
    }
}
