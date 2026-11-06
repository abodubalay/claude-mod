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
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Свои скины напарников: хозяин мира кладёт PNG 64x64 в config/claudecompanion/skins/<нейросеть>.png
 * (claude.png, deepseek.png, grok.png...), сервер рассылает их всем игрокам при входе и по /claude skin.
 * Старый config/claudecompanion/skin.png считается скином Claude.
 */
public final class SkinSync {
    private SkinSync() {}

    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ClaudeCompanion.MODID, "skin"), () -> VERSION, VERSION::equals,
            Boolean.getBoolean("claudecompanion.debug") ? NetworkRegistry.acceptMissingOr(VERSION) : VERSION::equals);

    public static Path skinsDir() {
        return FMLPaths.CONFIGDIR.get().resolve("claudecompanion").resolve("skins");
    }

    private static Path legacySkin() {
        return FMLPaths.CONFIGDIR.get().resolve("claudecompanion").resolve("skin.png");
    }

    public static void register() {
        CHANNEL.registerMessage(0, SkinPacket.class, SkinPacket::encode, SkinPacket::decode, SkinPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Отправить все скины игроку. Возвращает, сколько отправлено. */
    public static int sendTo(ServerPlayer player) {
        int n = 0;
        try {
            Files.createDirectories(skinsDir());
            if (Files.exists(legacySkin()) && !Files.exists(skinsDir().resolve("claude.png")))
                n += send(player, "claude", legacySkin());
            try (Stream<Path> files = Files.list(skinsDir())) {
                for (Path f : (Iterable<Path>) files::iterator) {
                    String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".png")) n += send(player, name.substring(0, name.length() - 4), f);
                }
            }
        } catch (Exception e) {
            ClaudeCompanion.LOG.warn("Не удалось отправить скины", e);
        }
        return n;
    }

    private static int send(ServerPlayer player, String profile, Path file) throws Exception {
        byte[] data = Files.readAllBytes(file);
        if (data.length > 200_000) {
            ClaudeCompanion.LOG.warn("{} слишком большой ({} байт), пропускаю", file, data.length);
            return 0;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkinPacket(profile, data));
        return 1;
    }

    public record SkinPacket(String profile, byte[] png) {
        static void encode(SkinPacket p, FriendlyByteBuf buf) { buf.writeUtf(p.profile, 64); buf.writeByteArray(p.png); }
        static SkinPacket decode(FriendlyByteBuf buf) { return new SkinPacket(buf.readUtf(64), buf.readByteArray(200_000)); }
        static void handle(SkinPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.claudecompanion.client.ClientSkin.apply(p.profile, p.png)));
            ctx.get().setPacketHandled(true);
        }
    }
}
