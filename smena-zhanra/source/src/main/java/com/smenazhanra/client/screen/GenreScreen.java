package com.smenazhanra.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.smenazhanra.SmenaZhanra;
import com.smenazhanra.client.ClientHooks;
import com.smenazhanra.client.NoteMusic;
import com.smenazhanra.client.StaticNoise;
import com.smenazhanra.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

/**
 * Общая основа всех мини-игр: виртуальный холст 320x180 (растягивается на весь экран),
 * таймер, побег по Esc, музыка, помехи при входе.
 */
public abstract class GenreScreen extends Screen {
    public static final int VW = 320, VH = 180;

    protected final GenreContext ctx;
    protected final Random rnd;
    protected NoteMusic music;
    protected float scale = 1;
    protected float offX, offY;
    protected int ticks;
    protected long openedAt;
    protected boolean finished;
    private String toast;
    private int toastTicks;

    protected GenreScreen(GenreContext ctx) {
        super(Component.literal(ctx.genre().title));
        this.ctx = ctx;
        this.rnd = new Random(ctx.seed());
    }

    public static ResourceLocation tex(String name) {
        return new ResourceLocation(SmenaZhanra.MODID, "textures/gui/" + name + ".png");
    }

    @Override
    protected void init() {
        scale = Math.min(width / (float) VW, height / (float) VH);
        offX = (width - VW * scale) / 2f;
        offY = (height - VH * scale) / 2f;
        if (openedAt == 0) {
            openedAt = System.currentTimeMillis();
            if (music != null) music.start();
            onStart();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // мир продолжает жить (таймер, мобы стоят замороженные)
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    protected int secondsLeft() {
        return Math.max(0, ctx.seconds() - (int) ((System.currentTimeMillis() - openedAt) / 1000));
    }

    // ------------------------------------------------------------------ что делают наследники
    protected void onStart() {}
    protected abstract void renderGame(GuiGraphics g, int mx, int my, float pt);
    protected void tickGame() {}
    protected void onTimeUp() {}
    protected boolean keyGame(int key) { return false; }
    protected boolean keyUpGame(int key) { return false; }
    protected void clickGame(int mx, int my) {}
    /** Показывать ли таймер сверху справа. */
    protected boolean showTimer() { return true; }

    // ------------------------------------------------------------------ движок

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (music != null) music.update();
        g.fill(0, 0, width, height, 0xFF000000);
        g.pose().pushPose();
        g.pose().translate(offX, offY, 0);
        g.pose().scale(scale, scale, 1f);
        int vx = (int) ((mouseX - offX) / scale), vy = (int) ((mouseY - offY) / scale);
        g.enableScissor((int) offX, (int) offY, (int) (offX + VW * scale), (int) (offY + VH * scale));
        renderGame(g, vx, vy, pt);
        g.disableScissor();
        if (showTimer()) {
            int s = secondsLeft();
            String t = "Время: " + s / 60 + ":" + String.format("%02d", s % 60);
            box(g, VW - font.width(t) - 8, 2, font.width(t) + 6, 12, 0xAA000000, 0xFFFFFFFF);
            g.drawString(font, t, VW - font.width(t) - 5, 4, s <= 10 ? 0xFF5555 : 0xFFFFFF, false);
        }
        String hint = ctx.allowSkip() ? "Esc — сбежать (позорно)" : "Сбежать нельзя!";
        g.drawString(font, hint, 3, VH - 9, 0x88FFFFFF, false);
        if (toast != null && toastTicks > 0) {
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            int w = font.width(toast) + 10;
            box(g, VW / 2 - w / 2, 60, w, 16, 0xF0000000, 0xFFFF5555);
            g.drawCenteredString(font, toast, VW / 2, 64, 0xFFFFFF);
            g.pose().popPose();
        }
        g.pose().popPose();
        // помехи при входе в жанр
        long el = System.currentTimeMillis() - openedAt;
        if (el < 900) StaticNoise.draw(g, width, height, 1f - el / 900f);
    }

    @Override
    public void tick() {
        ticks++;
        Minecraft.getInstance().getMusicManager().stopPlaying();
        if (toastTicks > 0) toastTicks--;
        if (!finished) {
            tickGame();
            if (!finished && secondsLeft() <= 0) onTimeUp();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            trySkip();
            return true;
        }
        return keyGame(key) || super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        return keyUpGame(key) || super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) clickGame((int) ((mx - offX) / scale), (int) ((my - offY) / scale));
        return true;
    }

    protected void toast(String text) {
        toast = text;
        toastTicks = 50;
    }

    private void trySkip() {
        if (finished) return;
        if (!ctx.allowSkip()) {
            toast("Сбежать нельзя — так настроено на сервере!");
            return;
        }
        finished = true;
        Net.CH.sendToServer(new Net.Skip(true));
        close();
    }

    /** Мини-игра закончилась — отправляем итог серверу. */
    protected void finish(int outcome, int... values) {
        if (finished) return;
        finished = true;
        Net.CH.sendToServer(new Net.Result(ctx.genre().id, outcome, values));
        close();
    }

    /** Сервер закрыл игру. */
    public void forceClose() {
        finished = true;
        close();
    }

    private void close() {
        if (music != null) music.stop();
        Minecraft.getInstance().setScreen(null);
        ClientHooks.startReturn();
    }

    @Override
    public void removed() {
        if (music != null) music.stop();
        if (!finished) {
            // экран закрылся сам (например, его перекрыл другой мод) — сообщим серверу без позора
            finished = true;
            if (Minecraft.getInstance().getConnection() != null) Net.CH.sendToServer(new Net.Skip(false));
        }
    }

    // ------------------------------------------------------------------ помощники рисования

    protected static void sprite(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }

    /** Кадр из полоски кадров. flip — отразить по горизонтали. */
    protected static void frame(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                                int frame, int fw, int fh, int texW, int texH, boolean flip) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        if (flip) {
            // зеркальный спрайт: отключаем отсечение граней, иначе перевёрнутый квадрат не рисуется
            RenderSystem.disableCull();
            g.pose().translate(x + w, y, 0);
            g.pose().scale(-1, 1, 1);
            g.blit(tex, 0, 0, w, h, frame * fw, 0, fw, fh, texW, texH);
            RenderSystem.enableCull();
        } else {
            g.blit(tex, x, y, w, h, frame * fw, 0, fw, fh, texW, texH);
        }
        g.pose().popPose();
    }

    protected static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    protected boolean button(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, int baseColor) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        box(g, x, y, w, h, hover ? (baseColor | 0xFF000000) : (baseColor & 0x00FFFFFF) | 0xCC000000, hover ? 0xFFFFFF55 : 0xFFFFFFFF);
        List<FormattedCharSequence> lines = font.split(Component.literal(label), w - 6);
        int ty = y + (h - lines.size() * 9) / 2 + 1;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x + (w - font.width(l)) / 2, ty, 0xFFFFFF, true);
            ty += 9;
        }
        return hover;
    }

    protected static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected int text(GuiGraphics g, String s, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(s), w);
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x, y, color, true);
            y += 10;
        }
        return lines.size() * 10;
    }

    protected void big(GuiGraphics g, String s, int cx, int y, float sc, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        // округляем до целого числа экранных пикселей — иначе крупные буквы «рвутся»
        sc = Math.max(1, Math.round(scale * sc)) / scale;
        g.pose().scale(sc, sc, 1);
        g.drawCenteredString(font, s, 0, 0, color);
        g.pose().popPose();
    }

    protected static void sfx(net.minecraft.sounds.SoundEvent s, float pitch, float vol) {
        NoteMusic.sfx(s, pitch, vol);
    }
}
