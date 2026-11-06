package com.smenazhanra.client.screen;

import com.smenazhanra.client.NoteMusic;
import com.smenazhanra.client.Songs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** 🎵 Ритм-игра «Копай в такт»: 4 дорожки, клавиши D F J K. */
public class RhythmScreen extends GenreScreen {
    private static final int[] KEYS = {GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K};
    private static final String[] KEY_NAMES = {"D", "F", "J", "K"};
    private static final String[] BLOCK_NAMES = {"Земля", "Камень", "Уголь", "Железо", "Золото", "Алмаз"};
    private static final int LANE_X = 100, LANE_W = 30, HIT_Y = 148, SPAWN_Y = -16;
    private static final double TRAVEL = 1.4, LEAD = 2.5;
    private static final double PERFECT = 0.06, GOOD = 0.13, MISS = 0.16;

    private static class Block {
        final double time; final int lane; final int type; final float pitch;
        boolean done;
        Block(double t, int l, int ty, float p) { time = t; lane = l; type = ty; pitch = p; }
    }

    private final List<Block> blocks = new ArrayList<>();
    private final NoteMusic clock;
    private boolean bgStarted;
    private final double length;
    private int score, combo, maxCombo, perfects, goods, misses;
    private final int[] caught = new int[6];
    private String judge = "";
    private int judgeColor;
    private long judgeAt;
    private final long[] laneFlash = new long[4];
    private boolean results;
    private long resultsAt;

    public RhythmScreen(GenreContext ctx) {
        super(ctx);
        music = Songs.ritmBackground();
        clock = new NoteMusic(128);
        length = ctx.seconds();
        buildBeatmap();
    }

    /** Блоки появляются на нотах мелодии — дорожка зависит от высоты ноты. */
    private void buildBeatmap() {
        NoteMusic melody = Songs.ritmMelody();
        double loopSec = melody.lengthBeats() * 60.0 / melody.bpm();
        int loop = 0;
        double lastTime = -1;
        while (loop * loopSec < length) {
            for (NoteMusic.Note n : melody.notes()) {
                double t = loop * loopSec + n.beat() * 60.0 / melody.bpm();
                if (t > length - 1.0) break;
                if (t - lastTime < 0.2) continue;
                boolean onBeat = Math.abs(n.beat() - Math.round(n.beat())) < 1e-6;
                double progress = t / length;
                // в начале только сильные доли, дальше всё плотнее
                if (progress < 0.3 && !onBeat) continue;
                if (progress < 0.3 && Math.round(n.beat()) % 2 == 1 && rnd.nextBoolean()) continue;
                if (!onBeat && rnd.nextFloat() > progress) continue;
                int key = (int) Math.round(12 + 12 * Math.log(n.pitch()) / Math.log(2));
                int lane = Math.max(0, Math.min(3, (key - 9) * 4 / 14));
                blocks.add(new Block(t, lane, randomType(), n.pitch()));
                lastTime = t;
            }
            loop++;
        }
    }

    private int randomType() {
        int r = rnd.nextInt(100);
        if (r < 28) return 0;
        if (r < 52) return 1;
        if (r < 70) return 2;
        if (r < 84) return 3;
        if (r < 93) return 4;
        return 5;
    }

    @Override
    protected void onStart() {
        clock.start();
        music.stop(); // фоновый трек запустится после отсчёта
    }

    @Override
    protected boolean showTimer() { return false; }

    private double now() {
        return clock.secondsNow() - LEAD;
    }

    @Override
    protected void tickGame() {
        double t = now();
        if (!results) {
            for (Block b : blocks) {
                if (!b.done && t - b.time > MISS) {
                    b.done = true;
                    misses++;
                    combo = 0;
                    setJudge("МИМО", 0xFF5555);
                }
            }
            if (t >= length) {
                results = true;
                resultsAt = System.currentTimeMillis();
                music.stop();
                sfx(high() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, 1f, 0.8f);
            }
        } else if (System.currentTimeMillis() - resultsAt > 8000) {
            send();
        }
    }

    @Override
    protected void onTimeUp() { /* у ритм-игры свой таймер */ }

    private boolean high() {
        int total = perfects + goods + misses;
        return total > 0 && (perfects + goods) >= 10 && (perfects + goods) / (double) total >= 0.6;
    }

    private void send() {
        if (high()) finish(0, caught[5], caught[4], caught[3], caught[2]);
        else finish(1);
    }

    private void setJudge(String s, int c) {
        judge = s;
        judgeColor = c;
        judgeAt = System.currentTimeMillis();
    }

    @Override
    protected boolean keyGame(int key) {
        if (results) {
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) send();
            return true;
        }
        for (int lane = 0; lane < 4; lane++) {
            if (KEYS[lane] != key) continue;
            laneFlash[lane] = System.currentTimeMillis();
            hit(lane);
            return true;
        }
        return false;
    }

    private void hit(int lane) {
        double t = now();
        Block best = null;
        for (Block b : blocks) {
            if (b.done || b.lane != lane) continue;
            if (Math.abs(t - b.time) <= GOOD && (best == null || Math.abs(t - b.time) < Math.abs(t - best.time))) best = b;
        }
        if (best == null) {
            // нажал «в пустоту» — это промах (иначе можно просто барабанить по всем клавишам)
            misses++;
            combo = 0;
            setJudge("МИМО", 0xFF5555);
            sfx(SoundEvents.NOTE_BLOCK_HAT.value(), 0.5f, 0.3f);
            return;
        }
        best.done = true;
        double dt = Math.abs(t - best.time);
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        float mult = 1 + Math.min(combo, 40) / 20f;
        if (dt <= PERFECT) { perfects++; score += (int) (300 * mult); setJudge("ОТЛИЧНО!", 0x55FFFF); }
        else { goods++; score += (int) (100 * mult); setJudge("ХОРОШО", 0x55FF55); }
        caught[best.type]++;
        sfx(SoundEvents.NOTE_BLOCK_PLING.value(), best.pitch, 0.6f);
        sfx(best.type == 5 ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.STONE_BREAK, 1.2f, 0.35f);
    }

    @Override
    protected void clickGame(int mx, int my) {
        if (results) send();
    }

    // ------------------------------------------------------------------ рисование
    @Override
    protected void renderGame(GuiGraphics g, int mx, int my, float pt) {
        double t = now();
        if (!bgStarted && t >= 0) { bgStarted = true; music.start(); }
        sprite(g, tex("ritm_bg"), 0, 0, VW, VH, 320, 180);
        // пульсация колонок в такт
        double beat = Math.max(0, t) * 128 / 60.0;
        float pulse = (float) (1 - (beat - Math.floor(beat)));
        g.fill(0, 0, VW, VH, ((int) (pulse * 30) << 24) | 0xFF55CC);

        // дорожки
        g.fill(LANE_X - 2, 0, LANE_X + LANE_W * 4 + 2, VH, 0xC0101018);
        long ms = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            int x = LANE_X + i * LANE_W;
            g.fill(x, 0, x + 1, VH, 0x40FFFFFF);
            long since = ms - laneFlash[i];
            if (since < 150) g.fill(x + 1, 0, x + LANE_W, HIT_Y + 8, (int) ((150 - since) / 150f * 0x50) << 24 | 0xFFFFFF);
        }
        g.fill(LANE_X, HIT_Y, LANE_X + LANE_W * 4, HIT_Y + 2, 0xFFFFFF55);

        // падающие блоки
        for (Block b : blocks) {
            if (b.done) continue;
            double dt = b.time - t;
            if (dt > TRAVEL || dt < -MISS) continue;
            int y = (int) (HIT_Y - 8 - dt / TRAVEL * (HIT_Y - 8 - SPAWN_Y));
            int x = LANE_X + b.lane * LANE_W + (LANE_W - 22) / 2;
            sprite16(g, b.type, x, y, 22);
        }

        // клавиши
        for (int i = 0; i < 4; i++) {
            int x = LANE_X + i * LANE_W;
            boolean pressed = ms - laneFlash[i] < 120;
            box(g, x + 4, HIT_Y + 6, LANE_W - 8, 16, pressed ? 0xFFFFFF55 : 0xFF303040, 0xFFFFFFFF);
            g.drawCenteredString(font, KEY_NAMES[i], x + LANE_W / 2, HIT_Y + 10, pressed ? 0x000000 : 0xFFFFFF);
        }

        // счёт
        g.drawString(font, "Счёт: " + score, 6, 6, 0xFFFFFF, true);
        g.drawString(font, "Комбо: " + combo, 6, 18, combo >= 10 ? 0xFFFF55 : 0xFFFFFF, true);
        g.drawString(font, "Добыча:", 236, 6, 0xFFFFFF, true);
        for (int i = 2; i < 6; i++) {
            sprite16(g, i, 236, 18 + (i - 2) * 14, 12);
            g.drawString(font, BLOCK_NAMES[i] + ": " + caught[i], 252, 20 + (i - 2) * 14, 0xFFFFFF, true);
        }
        int left = (int) Math.max(0, Math.ceil(length - Math.max(0, t)));
        g.drawString(font, "Осталось: " + left + " с", 236, 80, left <= 10 ? 0xFF5555 : 0xFFFFFF, true);

        long jt = ms - judgeAt;
        if (jt < 500 && !judge.isEmpty()) big(g, judge, LANE_X + LANE_W * 2, 90 - (int) (jt / 50), 1.6f, judgeColor);
        if (combo >= 5 && !results) big(g, combo + " КОМБО", LANE_X + LANE_W * 2, 60, 1.2f, 0xFFDD55);

        if (t < 0) {
            big(g, "Копай в такт!", VW / 2, 40, 2f, 0xFFFF55);
            big(g, String.valueOf((int) Math.ceil(-t)), VW / 2, 70, 3f, 0xFFFFFF);
            g.drawCenteredString(font, "Жми D F J K, когда блок на жёлтой линии", VW / 2, 105, 0xFFFFFF);
        }

        if (results) {
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200); // поверх всего остального текста
            box(g, 50, 30, 220, 120, 0xFF101020, 0xFFFFFF55);
            big(g, high() ? "ОТЛИЧНЫЙ РИТМ!" : "Ритма нет — копай руками", VW / 2, 38, high() ? 1.6f : 1.2f, high() ? 0x55FFFF : 0xFF8888);
            int total = perfects + goods + misses;
            int acc = total == 0 ? 0 : (perfects + goods) * 100 / total;
            g.drawCenteredString(font, "Счёт: " + score + "   Точность: " + acc + "%", VW / 2, 62, 0xFFFFFF);
            g.drawCenteredString(font, "Отлично: " + perfects + "  Хорошо: " + goods + "  Мимо: " + misses + "  Макс. комбо: " + maxCombo, VW / 2, 76, 0xDDDDDD);
            if (high()) g.drawCenteredString(font, "В инвентарь: алмазы " + caught[5] + ", золото " + caught[4] + ", железо " + caught[3] + ", уголь " + caught[2], VW / 2, 96, 0x55FF55);
            else g.drawCenteredString(font, "Награда: немного булыжника", VW / 2, 96, 0xAAAAAA);
            g.drawCenteredString(font, "(нажми, чтобы вернуться)", VW / 2, 130, 0xAAAAAA);
            g.pose().popPose();
        }
    }

    private void sprite16(GuiGraphics g, int type, int x, int y, int size) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(tex("blocks"), x, y, size, size, type * 16, 0, 16, 16, 96, 16);
    }
}
