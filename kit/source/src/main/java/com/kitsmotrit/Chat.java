package com.kitsmotrit;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Chat {
    private Chat() {}

    /** Сообщение кита игроку (можно выключить в конфиге). */
    public static void whale(ServerPlayer p, String text) {
        if (p != null && KitConfig.CHAT.get()) p.sendSystemMessage(Component.literal("§b<Кит>§r " + text));
    }
}
