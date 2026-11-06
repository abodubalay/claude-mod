package com.smenazhanra.client.screen;

import com.smenazhanra.ModSounds;
import com.smenazhanra.client.Songs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** 🎤 Викторина «Кто хочет стать изумрудным миллионером». */
public class QuizScreen extends GenreScreen {
    private static final int[] PRIZES = {1, 2, 5, 10, 25};
    private static final String[] LETTERS = {"A", "B", "C", "D"};

    private enum St { ASK, CONFIRM, REVEAL, END }

    private final List<QuizQuestions.Q> questions;
    private int qi;
    private String[] answers = new String[4];
    private int correct;
    private final boolean[] removed = new boolean[4];
    private St st = St.ASK;
    private int selected = -1;
    private int stateTicks;
    private int won;
    private boolean usedAudience, usedFifty, usedChicken;
    private int[] audience;
    private String chickenSays;
    private String host = "";
    private int applause;
    private boolean lost;

    public QuizScreen(GenreContext ctx) {
        super(ctx);
        music = Songs.viktorina();
        questions = QuizQuestions.pick(rnd);
    }

    @Override
    protected void onStart() {
        host = "Добро пожаловать на шоу, " + ctx.playerName() + "! Первый вопрос!";
        sfx(ModSounds.APPLAUSE.get(), 1f, 0.8f);
        applause = 40;
        loadQuestion();
    }

    private void loadQuestion() {
        QuizQuestions.Q q = questions.get(qi);
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(order, rnd);
        for (int i = 0; i < 4; i++) {
            answers[i] = q.answers()[order.get(i)];
            if (order.get(i) == 0) correct = i;
        }
        Arrays.fill(removed, false);
        audience = null;
        chickenSays = null;
        selected = -1;
        st = St.ASK;
    }

    // ------------------------------------------------------------------ подсказки
    private void helpAudience() {
        if (usedAudience || st != St.ASK) return;
        usedAudience = true;
        audience = new int[4];
        // мобы иногда врут!
        int favourite = rnd.nextInt(10) < 7 ? correct : randomWrong();
        int rest = 100;
        int top = 40 + rnd.nextInt(30);
        audience[favourite] = top;
        rest -= top;
        for (int i = 0; i < 4; i++) {
            if (i == favourite || removed[i]) continue;
            int v = rnd.nextInt(rest + 1);
            audience[i] = v;
            rest -= v;
        }
        audience[favourite] += rest;
        for (int i = 0; i < 4; i++) if (removed[i]) audience[i] = 0;
        host = "Зал проголосовал! Но помните: мобы иногда врут...";
        sfx(SoundEvents.ZOMBIE_AMBIENT, 1.2f, 0.5f);
        sfx(SoundEvents.CREEPER_PRIMED, 1.5f, 0.3f);
    }

    private int randomWrong() {
        List<Integer> w = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (i != correct && !removed[i]) w.add(i);
        return w.get(rnd.nextInt(w.size()));
    }

    private void fiftyFifty() {
        if (usedFifty || st != St.ASK) return;
        usedFifty = true;
        int keep = randomWrong();
        for (int i = 0; i < 4; i++) if (i != correct && i != keep) removed[i] = true;
        if (audience != null) for (int i = 0; i < 4; i++) if (removed[i]) audience[i] = 0;
        host = "Компьютер, убери два неправильных ответа!";
        sfx(SoundEvents.NOTE_BLOCK_BIT.value(), 0.6f, 0.8f);
    }

    private void callChicken() {
        if (usedChicken || st != St.ASK) return;
        usedChicken = true;
        int hint = rnd.nextInt(10) < 6 ? correct : randomWrong();
        StringBuilder sb = new StringBuilder("Алло? ... ");
        for (int i = 0; i <= hint; i++) sb.append(i == 0 ? "Ко" : "-ко");
        sb.append("! Ко.");
        chickenSays = sb.toString();
        host = "Хм... Что же она имела в виду?";
        sfx(SoundEvents.CHICKEN_AMBIENT, 1f, 1f);
    }

    private void takeMoney() {
        if (st != St.ASK || won == 0) return;
        host = "Мудрое решение! Вы забираете " + won + " изумрудов!";
        sfx(ModSounds.APPLAUSE.get(), 1f, 1f);
        applause = 50;
        st = St.END;
    }

    // ------------------------------------------------------------------ ответ
    private void select(int i) {
        if (st != St.ASK || removed[i]) return;
        selected = i;
        st = St.CONFIRM;
        stateTicks = 0;
        host = "«" + answers[i] + "» — это ваш окончательный ответ?..";
        sfx(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 1f);
    }

    @Override
    protected void tickGame() {
        stateTicks++;
        if (applause > 0) applause--;
        if (st == St.CONFIRM) {
            if (stateTicks % 10 == 0) sfx(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.8f, 0.8f);
            if (stateTicks >= 40) reveal();
        } else if (st == St.REVEAL && stateTicks >= 45) {
            if (lost || qi >= questions.size() - 1) st = St.END;
            else {
                qi++;
                host = "Вопрос номер " + (qi + 1) + " на " + PRIZES[qi] + " изумрудов!";
                loadQuestion();
            }
        }
    }

    private void reveal() {
        st = St.REVEAL;
        stateTicks = 0;
        if (selected == correct) {
            won = PRIZES[qi];
            sfx(ModSounds.CORRECT.get(), 1f, 1f);
            sfx(ModSounds.APPLAUSE.get(), 1f, 0.8f);
            applause = 45;
            host = qi == questions.size() - 1
                    ? "НЕВЕРОЯТНО! Вы — изумрудный миллионер! 25 изумрудов!"
                    : "Правильно! У вас уже " + won + " изумр.";
        } else {
            lost = true;
            won = 0;
            sfx(ModSounds.BUZZER.get(), 1f, 1f);
            host = "Увы! Правильный ответ: «" + answers[correct] + "». Всё сгорает!";
        }
    }

    @Override
    protected void onTimeUp() {
        if (st == St.CONFIRM || st == St.REVEAL) return;
        finish(0, lost ? 0 : won);
    }

    @Override
    protected void clickGame(int mx, int my) {
        if (st == St.END) { finish(0, lost ? 0 : won); return; }
        if (st != St.ASK) return;
        for (int i = 0; i < 4; i++) if (inside(mx, my, ansX(i), ansY(i), 118, 20)) { select(i); return; }
        if (inside(mx, my, 70, 112, 58, 16)) helpAudience();
        if (inside(mx, my, 132, 112, 50, 16)) fiftyFifty();
        if (inside(mx, my, 186, 112, 62, 16)) callChicken();
        if (inside(mx, my, 252, 112, 60, 16)) takeMoney();
    }

    @Override
    protected boolean keyGame(int key) {
        if (st == St.END && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER)) { finish(0, lost ? 0 : won); return true; }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) { select(key - GLFW.GLFW_KEY_1); return true; }
        return false;
    }

    private static int ansX(int i) { return i % 2 == 0 ? 70 : 192; }
    private static int ansY(int i) { return i < 2 ? 64 : 88; }

    // ------------------------------------------------------------------ рисование
    @Override
    protected void renderGame(GuiGraphics g, int mx, int my, float pt) {
        sprite(g, tex("quiz_bg"), 0, 0, VW, VH, 320, 180);
        // мигающие лампы
        if ((ticks / 5) % 2 == 0) g.fill(0, 0, VW, 10, 0x20FFFF88);
        // ведущий
        int hb = (ticks / 8) % 2;
        sprite(g, tex("host"), 10, 52 + hb, 48, 80, 48, 80);
        g.drawCenteredString(font, "Ведущий", 34, 134, 0xFFDD55);

        // лестница призов
        g.drawString(font, "Приз:", 70, 4, 0xFFFFFF, true);
        int lx = 100;
        for (int i = 0; i < PRIZES.length; i++) {
            String s = String.valueOf(PRIZES[i]);
            int w = font.width(s) + 6;
            boolean cur = i == qi && !lost && st != St.END;
            boolean done = won >= PRIZES[i] && !lost;
            box(g, lx, 2, w, 12, cur ? 0xFFFFAA00 : done ? 0xFF228833 : 0xAA101030, 0xFFFFFFFF);
            g.drawString(font, s, lx + 3, 4, cur ? 0x000000 : 0xFFFFFF, false);
            lx += w + 4;
        }
        g.drawString(font, "изумрудов", lx, 4, 0x55FF55, true);

        // вопрос
        QuizQuestions.Q q = questions.get(qi);
        box(g, 70, 18, 242, 42, 0xE0100830, 0xFF55AAFF);
        g.drawString(font, "Вопрос " + (qi + 1) + " из 5", 76, 22, 0x88CCFF, false);
        text(g, q.text(), 76, 34, 230, 0xFFFFFF);

        // ответы
        for (int i = 0; i < 4; i++) {
            if (removed[i]) {
                box(g, ansX(i), ansY(i), 118, 20, 0x80101010, 0xFF404040);
                continue;
            }
            int col = 0x201060;
            if (st == St.CONFIRM && i == selected) col = (ticks / 4) % 2 == 0 ? 0xFF8800 : 0xAA5500;
            if (st == St.REVEAL || st == St.END) {
                if (i == correct) col = 0x22AA33;
                else if (i == selected) col = 0xCC2222;
            }
            boolean hover = st == St.ASK && inside(mx, my, ansX(i), ansY(i), 118, 20);
            box(g, ansX(i), ansY(i), 118, 20, (hover ? 0xFF4030A0 : 0xEE000000 | col), hover ? 0xFFFFFF55 : 0xFFFFFFFF);
            String label = LETTERS[i] + ": " + answers[i];
            var lines = font.split(net.minecraft.network.chat.Component.literal(label), 112);
            int ty = ansY(i) + (20 - lines.size() * 9) / 2 + 1;
            for (var l : lines) { g.drawString(font, l, ansX(i) + 4, ty, 0xFFFFFF, true); ty += 9; }
        }

        // подсказки
        if (st == St.ASK) {
            lifeline(g, 70, 58, "Помощь зала", usedAudience, mx, my);
            lifeline(g, 132, 50, "50 на 50", usedFifty, mx, my);
            lifeline(g, 186, 62, "Звонок курице", usedChicken, mx, my);
            if (won > 0) button(g, 252, 112, 60, 16, "Забрать " + won, mx, my, 0x206020);
        }

        boolean panel = (audience != null || chickenSays != null) && st == St.ASK;
        // голосование зала
        if (audience != null && st == St.ASK) {
            box(g, 196, 131, 116, 27, 0xEE000000, 0xFFFFFFFF);
            for (int i = 0; i < 4; i++) {
                int bx = 200 + i * 28;
                int h = audience[i] * 14 / 100;
                g.fill(bx, 150 - h, bx + 8, 150, 0xFF55AAFF);
                g.drawString(font, LETTERS[i], bx, 150, 0xFFFFFF, false);
                g.drawString(font, audience[i] + "%", bx + 10, 142, 0xCCCCCC, false);
            }
        } else if (chickenSays != null && st == St.ASK) {
            // звонок курице
            box(g, 196, 131, 116, 27, 0xEEFFFFFF, 0xFF000000);
            sprite(g, tex("chicken"), 199, 136, 14, 15, 14, 15);
            text(g, chickenSays, 215, 135, 94, 0x303030);
        }

        // ведущий говорит
        box(g, 70, 131, panel ? 122 : 242, 27, 0xDD000000, 0xFFFFDD55);
        text(g, host, 74, 135, panel ? 114 : 234, 0xFFFFFF);

        // зрители-мобы хлопают
        int jump = applause > 0 ? (ticks % 4 < 2 ? 3 : 0) : 0;
        sprite(g, tex("audience"), 0, 160 - jump, 320, 34, 320, 34);

        if (st == St.END) {
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200); // поверх текста ответов
            box(g, 70, 40, 242, 70, 0xFF100830, 0xFFFFDD55);
            int prize = lost ? 0 : won;
            big(g, prize > 0 ? "ВЫИГРЫШ: " + prize + " ИЗУМРУДОВ" : "ВСЁ СГОРЕЛО", 191, 56, 1.4f, prize > 0 ? 0x55FF55 : 0xFF5555);
            g.drawCenteredString(font, prize > 0 ? "Изумруды уже летят в твой инвентарь!" : "Зато ты был на телевидении!", 191, 78, 0xFFFFFF);
            g.drawCenteredString(font, "(нажми, чтобы вернуться)", 191, 94, 0xAAAAAA);
            g.pose().popPose();
        }
    }

    private void lifeline(GuiGraphics g, int x, int w, String name, boolean used, int mx, int my) {
        if (used) {
            box(g, x, 112, w, 16, 0x80202020, 0xFF606060);
            g.drawCenteredString(font, "—", x + w / 2, 116, 0x808080);
        } else {
            button(g, x, 112, w, 16, name, mx, my, 0x603080);
        }
    }
}
