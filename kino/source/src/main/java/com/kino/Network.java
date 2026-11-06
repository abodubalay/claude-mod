package com.kino;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class Network {
    private Network() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(KinoMod.MODID, "main"),
            () -> VERSION, VERSION::equals,
            Boolean.getBoolean("kino.debug") ? NetworkRegistry.acceptMissingOr(VERSION) : VERSION::equals); // debug: для автотестов

    /** Кат-сцена: титр, мелкий подтитр (может быть пустым), длительность в тиках. */
    public record Scene(String title, String subtitle, int ticks) {}
    /** Пропуск кат-сцены (клиент → сервер). */
    public record Skip() {}
    /** Реакция ситкома. */
    public record Sitcom(int type) {}
    /** Титры: ник, кто снимался (id мобов и сколько), смерти, убитые курицы. */
    public record Credits(String player, List<String> cast, List<Integer> counts, int deaths, int chickens) {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, Scene.class, (m, b) -> { b.writeUtf(m.title); b.writeUtf(m.subtitle); b.writeVarInt(m.ticks); },
                b -> new Scene(b.readUtf(), b.readUtf(), b.readVarInt()),
                (m, ctx) -> client(ctx, () -> com.kino.client.ClientKino.startScene(m)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Skip.class, (m, b) -> {}, b -> new Skip(), (m, ctx) -> {
            var p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> { if (p != null) Cutscenes.end(p); });
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Sitcom.class, (m, b) -> b.writeVarInt(m.type), b -> new Sitcom(b.readVarInt()),
                (m, ctx) -> client(ctx, () -> com.kino.client.ClientKino.sitcom(m.type)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Credits.class, (m, b) -> {
            b.writeUtf(m.player);
            b.writeVarInt(m.cast.size());
            for (int i = 0; i < m.cast.size(); i++) { b.writeUtf(m.cast.get(i)); b.writeVarInt(m.counts.get(i)); }
            b.writeVarInt(m.deaths); b.writeVarInt(m.chickens);
        }, b -> {
            String player = b.readUtf();
            int n = b.readVarInt();
            List<String> cast = new ArrayList<>(); List<Integer> counts = new ArrayList<>();
            for (int i = 0; i < n; i++) { cast.add(b.readUtf()); counts.add(b.readVarInt()); }
            return new Credits(player, cast, counts, b.readVarInt(), b.readVarInt());
        }, (m, ctx) -> client(ctx, () -> com.kino.client.ClientKino.credits(m)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void client(Supplier<NetworkEvent.Context> ctx, Runnable r) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> r));
        ctx.get().setPacketHandled(true);
    }
}
