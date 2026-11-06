package com.smenazhanra.client;

import com.smenazhanra.Genre;
import com.smenazhanra.ModSounds;
import com.smenazhanra.client.screen.*;
import com.smenazhanra.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Состояние клиента и реакция на сообщения сервера. */
public class ClientHooks {
    public static final Map<UUID, Boolean> IN_GENRE = new HashMap<>();
    public static final Map<UUID, Boolean> CRIMINAL = new HashMap<>();

    private enum Mode { NONE, COUNTDOWN, RETURN }

    private static Mode mode = Mode.NONE;
    private static long modeStart;
    private static Genre pending;
    private static int lastSecondSound = -1;

    // ------------------------------------------------------------------ сообщения

    public static void onCountdown(String genreId) {
        pending = Genre.byId(genreId);
        mode = Mode.COUNTDOWN;
        modeStart = System.currentTimeMillis();
        lastSecondSound = -1;
        NoteMusic.sfx(ModSounds.CHANNEL_SWITCH.get(), 1f, 1f);
    }

    public static void onOpen(Net.Open msg) {
        Genre g = Genre.byId(msg.genre());
        if (g == null) return;
        mode = Mode.NONE;
        Minecraft mc = Minecraft.getInstance();
        GenreContext ctx = new GenreContext(g, msg.seconds(), msg.allowSkip(), msg.stats(), msg.seed(),
                mc.player != null ? mc.player.getGameProfile().getName() : "Игрок");
        mc.getMusicManager().stopPlaying();
        NoteMusic.sfx(ModSounds.CHANNEL_SWITCH.get(), 1.2f, 1f);
        mc.setScreen(switch (g) {
            case NOVELLA -> new NovellaScreen(ctx);
            case SUD -> new CourtScreen(ctx);
            case RITM -> new RhythmScreen(ctx);
            case VIKTORINA -> new QuizScreen(ctx);
            case FIGHTING -> new FightScreen(ctx);
        });
    }

    public static void onForceClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GenreScreen gs) gs.forceClose();
        else if (mode == Mode.COUNTDOWN) mode = Mode.NONE;
    }

    public static void onStatus(UUID id, boolean inGenre, boolean criminal) {
        IN_GENRE.put(id, inGenre);
        CRIMINAL.put(id, criminal);
    }

    public static void startReturn() {
        mode = Mode.RETURN;
        modeStart = System.currentTimeMillis();
        NoteMusic.sfx(ModSounds.CHANNEL_SWITCH.get(), 0.9f, 1f);
    }

    public static void reset() {
        IN_GENRE.clear();
        CRIMINAL.clear();
        mode = Mode.NONE;
    }

    // ------------------------------------------------------------------ экран поверх игры

    public static boolean overlayActive() {
        return mode != Mode.NONE;
    }

    public static void renderOverlay(GuiGraphics g, int w, int h) {
        if (mode == Mode.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        long el = System.currentTimeMillis() - modeStart;
        if (mode == Mode.COUNTDOWN) {
            if (el > 9000) { mode = Mode.NONE; return; } // сервер так и не ответил
            int left = (int) Math.max(1, 5 - el / 1000);
            int sec = (int) (el / 1000);
            if (sec != lastSecondSound && sec < 5) {
                lastSecondSound = sec;
                NoteMusic.sfx(SoundEvents.NOTE_BLOCK_HAT.value(), 0.6f, 0.8f);
                if (sec > 0) NoteMusic.sfx(ModSounds.CHANNEL_SWITCH.get(), 1f + sec * 0.1f, 0.45f);
            }
            float inSec = (el % 1000) / 1000f;
            float alpha = 0.25f + 0.1f * sec + (inSec < 0.25f ? 0.5f : 0f);
            if (el > 4700) alpha = 1f;
            StaticNoise.draw(g, w, h, alpha);
            g.fill(0, h / 2 - 52, w, h / 2 + 64, 0xB0000000);
            big(g, mc, "СМЕНА ЖАНРА...", w / 2, h / 2 - 40, 3f, 0xFFFFFF);
            if (el < 5000) big(g, mc, left + "...", w / 2, h / 2, 5f, 0xFF5555);
            if (el > 3000 && pending != null)
                g.drawCenteredString(mc.font, "Переключаемся на канал: " + pending.title, w / 2, h / 2 + 50, 0xFFFF55);
        } else {
            if (el > 2600) { mode = Mode.NONE; return; }
            float alpha = el < 600 ? 1f : 1f - (el - 600) / 2000f;
            StaticNoise.draw(g, w, h, alpha);
            if (el < 2200) g.fill(0, h / 2 - 22, w, h / 2 + 22, (int) (Math.min(1f, alpha) * 0xB0) << 24);
            if (el < 2200) big(g, mc, "ВОЗВРАТ В MINECRAFT", w / 2, h / 2 - 12, 3f, 0x55FF55);
        }
    }

    static void big(GuiGraphics g, Minecraft mc, String text, int cx, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 400);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(mc.font, text, 0, 0, color);
        g.pose().popPose();
    }
}
