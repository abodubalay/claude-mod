package com.smenazhanra.client.screen;

import com.smenazhanra.ModSounds;
import com.smenazhanra.StatKeys;
import com.smenazhanra.client.Songs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** ⚖️ Суд «Крипер против [ник игрока]». */
public class CourtScreen extends GenreScreen {

    record Response(int delta, String speaker, String text) {}

    private static final String[] DEFENSES = {"Это была самооборона", "Я не помню", "Протестую!", "Курицы сами виноваты"};
    private static final Response[][] RESPONSES = {
        {new Response(-15, "Судья", "Хм, крипер и правда выглядит угрожающе. Принято."),
         new Response(10, "Судья", "Самооборона? От КУРИЦЫ?!"),
         new Response(-10, "Судья", "Справедливо. Все знают, что они что-то замышляют.")},
        {new Response(5, "Судья", "Удобно. Очень удобно."),
         new Response(-10, "Судья", "Я тоже ничего не помню. Что мы вообще тут делаем?"),
         new Response(10, "Крипер", "Зато я всё помню! Тссссс!")},
        {new Response(-20, "Судья", "Протест принят! ...А против чего мы протестуем?"),
         new Response(15, "Судья", "Протест отклонён! Нельзя протестовать против всего подряд!"),
         new Response(-5, "Судья", "Звучит убедительно. Громко, по крайней мере.")},
        {new Response(15, "Курицы", "КО-КО-КО!!! (зал возмущён)"),
         new Response(-15, "Судья", "Вообще-то да. Вы видели их глаза?"),
         new Response(5, "Свидетель-курица", "...(упала в обморок от такой наглости)")},
    };
    private static final String[] WITNESS = {
        "Ко-ко. Ко-ко-ко! Я видела всё!",
        "Ко... КО! (показывает крылом на подсудимого)",
        "Ко-ко-ко-ко. Ко. (свидетель плачет)",
        "Кудах-тах-тах! Он брал яйца без спроса!",
        "Ко? Ко-ко? (свидетель забыл, зачем пришёл)",
        "КО-КО-КО-КО-КО! (переводчик: «без комментариев»)",
    };
    private static final String[] PUNISH_NAMES = {
        "один день носить РОЗОВУЮ БРОНЮ!",
        "один день курицы ходят за тобой и осуждающе кудахчут!",
        "один день ходить с надписью «ПРЕСТУПНИК»!"
    };

    private enum St { SAY, DEFENSE, SLEEP, DONE }

    private St st = St.SAY;
    private final Deque<String[]> queue = new ArrayDeque<>();
    private Runnable after;
    private String speaker = "", full = "", shown = "";
    private float typed;
    private final List<String> accusations;
    private int round;
    private final int sleepRound;
    private int sleepStart;
    private int guilt = 50;
    private int verdict = -1; // 0 оправдан, 1 виновен
    private int punish, emeralds;
    private int gavelAnim;
    private final List<float[]> confetti = new ArrayList<>();

    public CourtScreen(GenreContext ctx) {
        super(ctx);
        music = Songs.sud();
        accusations = buildAccusations();
        sleepRound = rnd.nextInt(10) < 7 ? rnd.nextInt(3) : -1; // в 70% судов судья засыпает
        punish = rnd.nextInt(3);
        emeralds = 3 + rnd.nextInt(4);
    }

    private List<String> buildAccusations() {
        List<String> real = new ArrayList<>();
        add(real, StatKeys.CHICKENS, "Подсудимый убил %d куриц!");
        add(real, StatKeys.PIGS, "Подсудимый превратил %d свиней в отбивные!");
        add(real, StatKeys.COWS, "Подсудимый лишил жизни %d коров. Молоко плачет!");
        add(real, StatKeys.VILLAGERS, "Подсудимый обидел %d жителей! Хмм!");
        add(real, StatKeys.MINED, "Подсудимый выкопал %d блоков чужой земли!");
        add(real, StatKeys.DIRT, "Подсудимый выкопал %d блоков земли. Это была чья-то лужайка!");
        add(real, StatKeys.LAVA_DEATHS, "Подсудимый %d раз умирал в лаве, позоря всех!");
        add(real, StatKeys.DEATHS, "Подсудимый умирал %d раз. Это подозрительно!");
        add(real, StatKeys.CREEPERS, "Подсудимый убил %d моих родственников! Тсссс!");
        add(real, StatKeys.KILLED_BY_CREEPER, "Подсудимый %d раз стоял рядом, когда я взрывался. Провокация!");
        add(real, StatKeys.JUMPS, "Подсудимый прыгнул %d раз. Он издевается над гравитацией!");
        add(real, StatKeys.FISH, "Подсудимый выловил %d рыб без лицензии!");
        add(real, StatKeys.TRADES, "Подсудимый %d раз сбивал цены у жителей!");
        add(real, StatKeys.SLEEP, "Подсудимый спал %d раз, пока монстры работали!");
        add(real, StatKeys.PLAY_HOURS, "Подсудимый провёл в игре %d часов. Мама волнуется!");
        Collections.shuffle(real, rnd);
        List<String> fake = new ArrayList<>(List.of(
            "Подсудимый ни разу не сказал криперу «здравствуйте»!",
            "Подсудимый построил дом из земли. Это оскорбление архитектуры!",
            "Подсудимый смотрел на закат без разрешения!",
            "Подсудимый носит в кармане 64 факела. Зачем ему столько света?!",
            "Подсудимый назвал меня «зелёным огурцом»!"));
        Collections.shuffle(fake, rnd);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (i < 2 && !real.isEmpty()) out.add(real.remove(0));
            else if (!real.isEmpty() && rnd.nextBoolean()) out.add(real.remove(0));
            else out.add(fake.remove(0));
        }
        return out;
    }

    private void add(List<String> list, int key, String fmt) {
        int v = ctx.stat(key);
        if (v > 0) list.add(String.format(fmt, v));
    }

    // ------------------------------------------------------------------ сценарий
    private void script(Runnable then, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) queue.add(new String[]{pairs[i], pairs[i + 1]});
        after = then;
        st = St.SAY;
        next();
    }

    private void next() {
        String[] l = queue.poll();
        if (l == null) {
            Runnable r = after;
            after = null;
            if (r != null) r.run();
            return;
        }
        speaker = l[0];
        full = l[1];
        shown = "";
        typed = 0;
        switch (speaker) {
            case "Крипер" -> sfx(SoundEvents.CREEPER_PRIMED, 1.2f, 0.5f);
            case "Свидетель-курица", "Курицы" -> sfx(SoundEvents.CHICKEN_AMBIENT, 1f, 1f);
            case "Судья" -> sfx(SoundEvents.VILLAGER_AMBIENT, 0.9f, 0.8f);
            default -> {}
        }
    }

    @Override
    protected void onStart() {
        bang();
        script(() -> startRound(0),
            "Судья", "Тишина в зале! Слушается дело «Крипер против " + ctx.playerName() + "»!",
            "Крипер", "Ваша честь, я требую справедливости! Тссс!");
    }

    private void startRound(int r) {
        round = r;
        List<String> lines = new ArrayList<>(List.of("Крипер", "Обвинение №" + (r + 1) + ": " + accusations.get(r)));
        if (rnd.nextInt(10) < 6) {
            lines.add("Судья"); lines.add("Вызывается свидетель-курица!");
            lines.add("Свидетель-курица"); lines.add(WITNESS[rnd.nextInt(WITNESS.length)]);
        }
        script(() -> {
            if (r == sleepRound) fallAsleep();
            else st = St.DEFENSE;
        }, lines.toArray(new String[0]));
    }

    private void fallAsleep() {
        st = St.SLEEP;
        sleepStart = ticks;
        speaker = "Судья";
        full = shown = "Хррр... Ззззз... (Судья уснул! Кликни по судье, чтобы разбудить!)";
        sfx(SoundEvents.VILLAGER_AMBIENT, 0.5f, 0.6f);
    }

    private void wake() {
        bang();
        boolean quick = ticks - sleepStart < 100;
        if (quick) guilt -= 5;
        script(() -> st = St.DEFENSE, "Судья", quick
                ? "А? Что? Я не спал! Я... обдумывал. Спасибо, подсудимый, учту."
                : "Ммм?! Кто посмел так долго меня не будить?! Продолжаем!");
        if (!quick) guilt += 5;
    }

    private void defend(int i) {
        Response r = RESPONSES[i][rnd.nextInt(RESPONSES[i].length)];
        guilt = Math.max(0, Math.min(100, guilt + r.delta));
        if (r.delta < 0) sfx(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.3f, 0.6f);
        else sfx(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6f, 0.8f);
        script(() -> {
            if (round + 1 < 3) startRound(round + 1);
            else verdict();
        }, "Ты", DEFENSES[i], r.speaker, r.text);
    }

    private void verdict() {
        verdict = guilt >= 50 ? 1 : 0;
        bang();
        if (verdict == 0) {
            sfx(ModSounds.APPLAUSE.get(), 1f, 1f);
            sfx(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1f, 1f);
            for (int i = 0; i < 120; i++)
                confetti.add(new float[]{rnd.nextInt(VW), -rnd.nextInt(120), rnd.nextFloat() - 0.5f, 0.6f + rnd.nextFloat(),
                        net.minecraft.util.Mth.hsvToRgb(rnd.nextFloat(), 0.8f, 1f) & 0xFFFFFF});
            script(() -> { st = St.DONE; },
                "Судья", "Суд постановил: НЕВИНОВЕН!",
                "Крипер", "Это несправедливо! Тссс... пшшш...",
                "Судья", "Подсудимому выплатить " + emeralds + " изумрудов за моральный ущерб!");
        } else {
            sfx(SoundEvents.VILLAGER_NO, 0.8f, 1f);
            script(() -> { st = St.DONE; },
                "Судья", "Суд постановил: ВИНОВЕН!",
                "Судья", "Приговор: " + PUNISH_NAMES[punish],
                "Курицы", "Ко-ко-ко! (одобрительно)");
        }
    }

    private void bang() {
        gavelAnim = 8;
        sfx(ModSounds.GAVEL.get(), 1f, 1f);
    }

    // ------------------------------------------------------------------ ввод
    private boolean typing() { return shown.length() < full.length(); }

    @Override
    protected void tickGame() {
        if (st == St.SAY && typing()) {
            typed += 1.8f;
            shown = full.substring(0, Math.min(full.length(), (int) typed));
        }
        if (gavelAnim > 0) gavelAnim--;
        if (st == St.SLEEP && (ticks - sleepStart) % 40 == 0) sfx(SoundEvents.VILLAGER_AMBIENT, 0.5f, 0.4f);
        if (st == St.SLEEP && ticks - sleepStart > 200) wake(); // проснулся сам через 10 сек
        for (float[] c : confetti) { c[0] += c[2]; c[1] += c[3]; }
    }

    private void click() {
        if (st == St.SAY) {
            if (typing()) { typed = full.length(); shown = full; }
            else next();
        } else if (st == St.DONE) {
            finish(verdict, verdict == 0 ? emeralds : punish);
        }
    }

    @Override
    protected void clickGame(int mx, int my) {
        if (st == St.SLEEP) {
            if (inside(mx, my, 132, 20, 56, 72)) wake();
            return;
        }
        if (st == St.DEFENSE) {
            for (int i = 0; i < 4; i++) if (inside(mx, my, defX(i), defY(i), 150, 15)) { defend(i); return; }
            return;
        }
        click();
    }

    private static int defX(int i) { return i % 2 == 0 ? 8 : 162; }
    private static int defY(int i) { return i < 2 ? 86 : 102; }

    @Override
    protected boolean keyGame(int key) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) { click(); return true; }
        if (st == St.DEFENSE && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) { defend(key - GLFW.GLFW_KEY_1); return true; }
        return false;
    }

    @Override
    protected void onTimeUp() {
        if (verdict < 0) verdict = guilt >= 50 ? 1 : 0;
        finish(verdict, verdict == 0 ? emeralds : punish);
    }

    // ------------------------------------------------------------------ рисование
    @Override
    protected void renderGame(GuiGraphics g, int mx, int my, float pt) {
        sprite(g, tex("court_bg"), 0, 0, VW, VH, 320, 180);
        // судья
        boolean sleeping = st == St.SLEEP;
        int jy = 20 + (gavelAnim > 0 ? 1 : 0);
        sprite(g, tex(sleeping ? "judge_sleep" : "judge"), 132, jy, 56, 72, 56, 72);
        if (sleeping) {
            for (int i = 0; i < 3; i++) {
                int t = (ticks + i * 15) % 45;
                g.drawString(font, "Z", 180 + t / 3 + i * 4, 30 - t / 2, 0xFFFFFF, true);
            }
            if (inside(mx, my, 132, 20, 56, 72)) box(g, 131, 19, 58, 74, 0x30FFFF00, 0xFFFFFF55);
        }
        if (st != St.DEFENSE && verdict < 0) g.drawCenteredString(font, "Судья", 160, 92, 0xFFDD88);
        // крипер-истец
        int cbob = (ticks / 6) % 2;
        sprite(g, tex("creeper_tie"), 36, 52 - cbob, 36, 60, 36, 60);
        g.drawCenteredString(font, "Истец", 54, 42, 0x88FF88);
        // свидетели-курицы
        for (int i = 0; i < 3; i++) {
            int hop = ("Свидетель-курица".equals(speaker) || "Курицы".equals(speaker)) && (ticks / 4 + i) % 3 == 0 ? 2 : 0;
            sprite(g, tex("chicken"), 246 + i * 24, 72 - hop, 28, 30, 14, 15);
        }
        g.drawCenteredString(font, "Свидетели", 274, 62, 0xFFFFFF);

        // шкала виновности
        box(g, 92, 2, 136, 14, 0xAA000000, 0xFFFFFFFF);
        g.fill(94, 9, 94 + (int) (132 * guilt / 100f), 14, guilt >= 50 ? 0xFFDD3333 : 0xFF33BB33);
        g.drawCenteredString(font, "Виновность: " + guilt + "%", 160, 3, 0xFFFFFF);

        // кнопки защиты
        if (st == St.DEFENSE) {
            g.drawCenteredString(font, "Выбери слово защиты:", 160, 76, 0xFFFF55);
            for (int i = 0; i < 4; i++) button(g, defX(i), defY(i), 150, 15, (i + 1) + ". " + DEFENSES[i], mx, my, 0x404080);
        }

        // окно реплики
        g.flush();
        box(g, 6, 120, 308, 50, 0xE0101010, 0xFFC8A060);
        int nameW = font.width(speaker) + 10;
        box(g, 12, 113, nameW, 11, 0xFFC8A060, 0xFFFFFFFF);
        g.drawString(font, speaker, 17, 115, 0x000000, false);
        text(g, shown, 14, 128, 292, 0xFFFFFF);
        if (st == St.SAY && !typing() && (ticks / 10) % 2 == 0) g.drawString(font, "▼", 302, 160, 0xFFC8A060, false);

        if (verdict >= 0 && st != St.DEFENSE) {
            big(g, verdict == 0 ? "НЕВИНОВЕН!" : "ВИНОВЕН!", 160, 98, 2f, verdict == 0 ? 0x55FF55 : 0xFF4444);
        }
        if (st == St.DONE) g.drawCenteredString(font, "(нажми, чтобы вернуться в Minecraft)", 160, 162, 0xAAAAAA);
        for (float[] c : confetti) g.fill((int) c[0], (int) c[1], (int) c[0] + 2, (int) c[1] + 3, 0xFF000000 | (int) c[4]);
    }
}
