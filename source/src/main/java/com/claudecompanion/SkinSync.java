package com.claudecompanion;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Свой скин напарника: хозяин мира кладёт PNG 64x64 в config/claudecompanion/skin.png,
 * сервер рассылает его всем игрокам при входе — друзьям ничего копировать не нужно.
 */
public final class SkinSync {
    private SkinSync() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ClaudeCompanion.MODID, "skin"), () -> VERSION, VERSION::equals,
            Boolean.getBoolean("claudecompanion.debug") ? NetworkRegistry.acceptMissingOr(VERSION) : VERSION::equals);

    public static Path skinFile() {
        return FMLPaths.CONFIGDIR.get().resolve("claudecompanion").resolve("skin.png");
    }

    public static void register() {
        CHANNEL.registerMessage(0, SkinPacket.class, SkinPacket::encode, SkinPacket::decode, SkinPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Отправить скин игроку (если файл есть). */
    public static void sendTo(ServerPlayer player) {
        try {
            Path f = skinFile();
            if (!Files.exists(f)) return;
            byte[] data = Files.readAllBytes(f);
            if (data.length > 200_000) {
                ClaudeCompanion.LOG.warn("skin.png слишком большой ({} байт), пропускаю", data.length);
                return;
            }
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkinPacket(data));
        } catch (Exception e) {
            ClaudeCompanion.LOG.warn("Не удалось отправить скин", e);
        }
    }

    public record SkinPacket(byte[] png) {
        static void encode(SkinPacket p, FriendlyByteBuf buf) { buf.writeByteArray(p.png); }
        static SkinPacket decode(FriendlyByteBuf buf) { return new SkinPacket(buf.readByteArray(200_000)); }
        static void handle(SkinPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.claudecompanion.client.ClientSkin.apply(p.png)));
            ctx.get().setPacketHandled(true);
        }
    }
}
