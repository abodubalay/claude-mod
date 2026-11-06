package com.kitsmotrit;

import com.kitsmotrit.entity.MaidWhale;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Клик левой кнопкой по киту. Кит висит в 25–30 блоках — рукой не дотянуться,
 * поэтому клиент сообщает «ударил, глядя на кита», а сервер проверяет взгляд сам.
 */
public final class Network {
    private Network() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KitMod.MODID, "main"), () -> VERSION, VERSION::equals,
            Boolean.getBoolean("kitsmotrit.debug") ? NetworkRegistry.acceptMissingOr(VERSION) : VERSION::equals); // debug: для автотестов

    public static void register() {
        CHANNEL.registerMessage(0, PunchPacket.class, (p, b) -> {}, b -> new PunchPacket(), Network::handlePunch,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public record PunchPacket() {}

    private static void handlePunch(PunchPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null) return;
            MaidWhale w = MaidWhale.ofPlayer(player);
            if (w != null && w.isLookedAtBy(player)) w.onPunched(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
