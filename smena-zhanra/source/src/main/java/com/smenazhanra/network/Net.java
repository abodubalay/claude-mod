package com.smenazhanra.network;

import com.smenazhanra.Genre;
import com.smenazhanra.SmenaZhanra;
import com.smenazhanra.server.GenreManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сетевые сообщения. acceptMissingOr — чтобы клиент с модом мог заходить на серверы без мода и наоборот.
 */
public class Net {
    private static final String VERSION = "1";
    public static SimpleChannel CH;

    public static void init() {
        CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(SmenaZhanra.MODID, "main"), () -> VERSION,
                NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
        int id = 0;
        CH.messageBuilder(Countdown.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Countdown::write).decoder(Countdown::read).consumerMainThread(Countdown::handle).add();
        CH.messageBuilder(Open.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Open::write).decoder(Open::read).consumerMainThread(Open::handle).add();
        CH.messageBuilder(ForceClose.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ForceClose::write).decoder(ForceClose::read).consumerMainThread(ForceClose::handle).add();
        CH.messageBuilder(Status.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Status::write).decoder(Status::read).consumerMainThread(Status::handle).add();
        CH.messageBuilder(Result.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Result::write).decoder(Result::read).consumerMainThread(Result::handle).add();
        CH.messageBuilder(Skip.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Skip::write).decoder(Skip::read).consumerMainThread(Skip::handle).add();
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static void toAll(Object msg) {
        CH.send(PacketDistributor.ALL.noArg(), msg);
    }

    // ------------------------------------------------------------------ сервер -> клиент

    /** «СМЕНА ЖАНРА... 5... 4...» */
    public record Countdown(String genre) {
        void write(FriendlyByteBuf b) { b.writeUtf(genre); }
        static Countdown read(FriendlyByteBuf b) { return new Countdown(b.readUtf()); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.smenazhanra.client.ClientHooks.onCountdown(genre));
        }
    }

    /** Открыть экран мини-игры. */
    public record Open(String genre, int seconds, boolean allowSkip, int[] stats, long seed) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(genre); b.writeVarInt(seconds); b.writeBoolean(allowSkip); b.writeVarIntArray(stats); b.writeLong(seed);
        }
        static Open read(FriendlyByteBuf b) {
            return new Open(b.readUtf(), b.readVarInt(), b.readBoolean(), b.readVarIntArray(), b.readLong());
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.smenazhanra.client.ClientHooks.onOpen(this));
        }
    }

    /** Сервер закрывает мини-игру (время вышло и т.п.). */
    public record ForceClose() {
        void write(FriendlyByteBuf b) {}
        static ForceClose read(FriendlyByteBuf b) { return new ForceClose(); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.smenazhanra.client.ClientHooks.onForceClose());
        }
    }

    /** Состояние игрока для всех: «в другом жанре» и «ПРЕСТУПНИК». */
    public record Status(UUID player, boolean inGenre, boolean criminal) {
        void write(FriendlyByteBuf b) { b.writeUUID(player); b.writeBoolean(inGenre); b.writeBoolean(criminal); }
        static Status read(FriendlyByteBuf b) { return new Status(b.readUUID(), b.readBoolean(), b.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.smenazhanra.client.ClientHooks.onStatus(player, inGenre, criminal));
        }
    }

    // ------------------------------------------------------------------ клиент -> сервер

    /** Итог мини-игры. */
    public record Result(String genre, int outcome, int[] values) {
        void write(FriendlyByteBuf b) { b.writeUtf(genre); b.writeVarInt(outcome); b.writeVarIntArray(values); }
        static Result read(FriendlyByteBuf b) { return new Result(b.readUtf(), b.readVarInt(), b.readVarIntArray()); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            Genre g = Genre.byId(genre);
            if (p != null && g != null) GenreManager.finish(p, g, outcome, values);
        }
    }

    /** Побег из жанра (coward = нажал Esc). */
    public record Skip(boolean coward) {
        void write(FriendlyByteBuf b) { b.writeBoolean(coward); }
        static Skip read(FriendlyByteBuf b) { return new Skip(b.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) GenreManager.skip(p, coward);
        }
    }
}
