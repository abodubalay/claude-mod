package com.smenazhanra.client.screen;

import com.smenazhanra.client.NoteMusic;
import com.smenazhanra.client.Songs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 💕 Визуальная новелла «Свинья моей мечты». */
public class NovellaScreen extends GenreScreen {

    record Choice(String text, int love, String reaction, String face) {}
    record Line(String text, String face, Choice... choices) {}
    record Scenario(String title, Line[] lines, String good, String normal, String bad) {}

    // ------------------------------------------------------------------ сценарии
    private static final Scenario[] SCENARIOS = {
        new Scenario("Глава 1. Под сакурой", new Line[]{
            new Line("Ты... ты пришёл? Я думала, ты забыл про меня...", "normal",
                new Choice("Я никогда не забуду тебя", 2, "П-правда?! Хрю... то есть, ой! Я не хрюкала!", "blush"),
                new Choice("Ты кто?", -2, "К-как это «кто»?! Мы же вместе копали картошку!", "cry"),
                new Choice("Я голодный", -1, "...Надеюсь, ты сейчас смотришь НЕ на меня.", "normal")),
            new Line("Ты ведь не ешь свинину, правда?..", "normal",
                new Choice("Конечно нет!", 2, "Я так и знала! Ты не такой, как остальные игроки...", "happy"),
                new Choice("...", -1, "Почему ты молчишь?! Почему у тебя в инвентаре 64 отбивных?!", "cry"),
                new Choice("Только по праздникам", -2, "Сегодня ведь не праздник? Скажи, что не праздник!", "cry")),
            new Line("Смотри, лепестки падают... Как блоки, когда рядом взорвался крипер.", "happy",
                new Choice("Красиво. Как ты.", 2, "Ты такой романтик... для того, кто живёт в земляной хижине.", "blush"),
                new Choice("Крипер?! Где?!", 0, "Успокойся, это была метафора. Наверное.", "normal"),
                new Choice("Из них можно сделать краситель", -1, "Ты всё измеряешь ресурсами, да?..", "normal")),
            new Line("Учитель говорит, что я должна выйти замуж за свина из соседней деревни...", "cry",
                new Choice("Я тебя не отдам!", 2, "Ты будешь бороться за меня? Даже с его морковкой на удочке?!", "happy"),
                new Choice("А он богатый?", -2, "Как ты можешь такое спрашивать?!", "cry"),
                new Choice("Давай сбежим в Незер", 1, "В Незер?! Там же пиглины... Но с тобой — хоть куда!", "happy")),
            new Line("Скажи честно... я тебе нравлюсь?", "blush",
                new Choice("Больше, чем алмазы", 2, "Больше, чем АЛМАЗЫ?! Хрю-хрю-хрю!!", "blush"),
                new Choice("Ты милая, но я люблю свою кровать", 0, "Кровать... Понимаю. Она тоже мягкая.", "normal"),
                new Choice("Мне пора фармить железо", -2, "Железо... Опять это железо...", "cry")),
        }, "Я буду ждать тебя... в деревне.", "Давай останемся друзьями. Хрю.", "Уходи! Я расскажу всем свиньям, какой ты!"),

        new Scenario("Глава 2. Школьный фестиваль", new Line[]{
            new Line("Сегодня школьный фестиваль! Ты... пойдёшь со мной?", "blush",
                new Choice("С тобой — хоть в Энд!", 2, "В Энд?! Там же дракон! ...Ты такой смелый.", "blush"),
                new Choice("А там будут бесплатные пирожки?", -1, "Тыквенные. Ты пойдёшь ради пирожков?..", "normal"),
                new Choice("Я записался в клуб скелетов", -2, "Они же стреляют в людей на переменах!", "cry")),
            new Line("Я испекла для тебя торт! Сама! Копытцами!", "happy",
                new Choice("Это лучший торт в моей жизни", 2, "Я старалась всю ночь! Яйца, правда, украла у куриц...", "blush"),
                new Choice("Он квадратный...", 0, "Все торты в этом мире квадратные! Не придирайся!", "normal"),
                new Choice("Можно ещё шесть кусочков?", 1, "Ты съел весь торт за семь кликов?! Мне это нравится.", "happy")),
            new Line("Ой... Там эндермен. Он смотрит на нас...", "cry",
                new Choice("Не смотри ему в глаза. Я защищу тебя", 2, "Ты закрыл меня собой... Моё сердце стучит как поршень!", "blush"),
                new Choice("Привет, эндермен!", -2, "Зачем ты посмотрел ему в глаза?! Он утащил мою парту!", "cry"),
                new Choice("Может, он тоже хочет торт", 0, "...Он забрал торт и телепортировался. Ладно.", "normal")),
            new Line("Вечером будут фейерверки... Ты любишь фейерверки?", "normal",
                new Choice("Люблю, но ты ярче", 2, "Ярче фейерверка?! Я сейчас взорвусь от счастья... в хорошем смысле!", "blush"),
                new Choice("Я на них летаю на элитрах", 1, "Покатаешь меня? Только не над лавой, пожалуйста.", "happy"),
                new Choice("Я больше люблю динамит", -2, "Это совсем не то же самое!!", "cry")),
            new Line("Фестиваль заканчивается... Ты хочешь мне что-то сказать?", "blush",
                new Choice("Ты — мой самый редкий дроп", 2, "Реже, чем голова визер-скелета?.. Хрю!", "blush"),
                new Choice("Спасибо за торт", 0, "Пожалуйста... Только за торт, да?", "normal"),
                new Choice("Где тут выход?", -2, "Выход... там. Как и из моего сердца.", "cry")),
        }, "Я буду ждать тебя... в деревне. Каждый фестиваль.", "Давай останемся друзьями. Будем есть торты вместе.", "Ты хуже, чем эндермен, укравший парту!"),

        new Scenario("Глава 3. Дождь после уроков", new Line[]{
            new Line("Пошёл дождь... а я забыла зонтик. Хрю...", "cry",
                new Choice("Возьми мой", 2, "Но в Майнкрафте нет зонтиков... Это самое доброе, что мне говорили!", "blush"),
                new Choice("Встань под блок", 0, "Как практично... И холодно.", "normal"),
                new Choice("Свиньи же не тают", -1, "Зато обижаются!", "cry")),
            new Line("Ай, молния! Если в меня ударит молния, я стану зомби-пиглином...", "cry",
                new Choice("Я поставлю громоотвод прямо сейчас!", 2, "Ты бы потратил ради меня медь?! Целых три слитка?!", "happy"),
                new Choice("Зато будешь с золотым мечом", -2, "Мне не нужен меч! Мне нужен ты!.. Ой.", "cry"),
                new Choice("Встань за меня", 1, "За тобой так спокойно...", "blush")),
            new Line("Знаешь, почему я всегда ношу эту ленточку?", "normal",
                new Choice("Потому что она тебе идёт", 2, "Ты заметил?! Никто никогда не замечал!", "blush"),
                new Choice("Это поводок?", -2, "ЭТО. НЕ. ПОВОДОК.", "cry"),
                new Choice("Нет, расскажи", 1, "Её подарил игрок, который не сделал из меня отбивную...", "happy")),
            new Line("Подруги говорят, что игроки любят только коров... за кожу.", "normal",
                new Choice("Коровы — ничто по сравнению с тобой", 2, "Даже грибные коровы?! Хрю-хрю!", "blush"),
                new Choice("Ну, кожа нужна для книжек...", -1, "Книжки... Конечно. Книжки важнее.", "cry"),
                new Choice("Я ем только картошку", 1, "Картошку?! Ты мой герой!", "happy")),
            new Line("Дождь кончился... Смотри, радуга! Загадай желание.", "happy",
                new Choice("Я загадал быть с тобой", 2, "Это... моё желание тоже!", "blush"),
                new Choice("Хочу найти алмазы", -1, "Алмазы... Ну да. Конечно.", "normal"),
                new Choice("В Майнкрафте нет радуги", -2, "Ты разрушил всю атмосферу! Как крипер разрушает дом!", "cry")),
        }, "Я буду ждать тебя... в деревне. Под нашим блоком.", "Давай останемся друзьями. Хотя бы от дождя прятаться вместе.", "Не подходи ко мне! И ко всем моим сёстрам тоже!"),
    };

    // ------------------------------------------------------------------ состояние
    private enum State { LINE, CHOICES, REACTION, ENDING }

    private final Scenario sc;
    private int lineIdx;
    private State state = State.LINE;
    private String shown = "";
    private String full;
    private float typed;
    private String face = "normal";
    private int love = 4;
    private List<Choice> current = new ArrayList<>();
    private int ending = -1;
    private final List<float[]> floatHearts = new ArrayList<>();

    public NovellaScreen(GenreContext ctx) {
        super(ctx);
        sc = SCENARIOS[rnd.nextInt(SCENARIOS.length)];
        music = Songs.novella();
    }

    @Override
    protected void onStart() {
        say(sc.lines[0].text, sc.lines[0].face);
    }

    private void say(String text, String newFace) {
        full = text;
        shown = "";
        typed = 0;
        face = newFace;
    }

    private boolean typing() {
        return shown.length() < full.length();
    }

    @Override
    protected void tickGame() {
        if (typing()) {
            int before = (int) typed;
            typed += 1.6f;
            shown = full.substring(0, Math.min(full.length(), (int) typed));
            if ((int) typed / 2 != before / 2 && shown.length() > 0 && shown.charAt(shown.length() - 1) != ' ')
                sfx(SoundEvents.NOTE_BLOCK_BIT.value(), 1.6f + rnd.nextFloat() * 0.3f, 0.25f);
        } else if (state == State.LINE) {
            state = State.CHOICES;
            current = new ArrayList<>(Arrays.asList(sc.lines[lineIdx].choices));
            Collections.shuffle(current, rnd);
        }
        floatHearts.removeIf(h -> (h[2] -= 1) <= 0);
        for (float[] h : floatHearts) h[1] -= 0.8f;
    }

    private void advance() {
        if (typing()) {
            typed = full.length();
            shown = full;
            return;
        }
        switch (state) {
            case REACTION -> {
                lineIdx++;
                if (lineIdx >= sc.lines.length) showEnding();
                else {
                    state = State.LINE;
                    say(sc.lines[lineIdx].text, sc.lines[lineIdx].face);
                }
            }
            case ENDING -> finish(ending);
            default -> {}
        }
    }

    private void choose(Choice c) {
        love = Math.max(0, Math.min(10, love + c.love));
        if (c.love > 0) {
            sfx(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.7f);
            for (int i = 0; i < c.love * 3; i++) floatHearts.add(new float[]{200 + rnd.nextInt(100), 60 + rnd.nextInt(40), 30 + rnd.nextInt(20)});
        } else if (c.love < 0) {
            sfx(SoundEvents.PIG_HURT, 1.4f, 0.6f);
        } else {
            sfx(SoundEvents.PIG_AMBIENT, 1.5f, 0.6f);
        }
        state = State.REACTION;
        say(c.reaction, c.face);
    }

    private void showEnding() {
        state = State.ENDING;
        if (love >= 8) { ending = 0; say(sc.good, "blush"); sfx(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f); }
        else if (love <= 2) { ending = 2; say(sc.bad, "cry"); sfx(SoundEvents.PIG_DEATH, 1.3f, 0.6f); }
        else { ending = 1; say(sc.normal, "normal"); }
    }

    @Override
    protected void onTimeUp() {
        if (state != State.ENDING) {
            ending = 1;
            finish(1);
        } else finish(ending);
    }

    @Override
    protected void clickGame(int mx, int my) {
        if (state == State.CHOICES) {
            for (int i = 0; i < current.size(); i++) {
                if (inside(mx, my, 10, 34 + i * 26, 170, 22)) { choose(current.get(i)); return; }
            }
            return;
        }
        advance();
    }

    @Override
    protected boolean keyGame(int key) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) { advance(); return true; }
        if (state == State.CHOICES && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_3) {
            choose(current.get(key - GLFW.GLFW_KEY_1));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ рисование
    @Override
    protected void renderGame(GuiGraphics g, int mx, int my, float pt) {
        sprite(g, tex("novella_bg"), 0, 0, VW, VH, 320, 180);
        // свинья «дышит»
        int bob = (int) (Math.sin((ticks + pt) / 12.0) * 1.5);
        ResourceLocation pig = tex("novella_pig_" + face);
        sprite(g, pig, 188, 14 + bob, 120, 160, 120, 160);

        // шкала любви
        box(g, 2, 14, 136, 13, 0xAA000000, 0xFFFF88AA);
        g.drawString(font, "Любовь", 6, 17, 0xFFAACC, false);
        for (int i = 0; i < 10; i++) {
            boolean full = i < love;
            g.blit(tex("hearts"), 44 + i * 9, 16, full ? 0 : 9, 0, 9, 9, 18, 9);
        }
        for (float[] h : floatHearts) g.blit(tex("hearts"), (int) h[0], (int) h[1], 0, 0, 9, 9, 18, 9);
        g.drawString(font, sc.title, 4, 4, 0xFFFFFF, true);

        // окно диалога
        g.flush();
        box(g, 6, 120, 308, 52, 0xE0201030, 0xFFFF88AA);
        box(g, 12, 112, 70, 12, 0xFFFF88AA, 0xFFFFFFFF);
        g.drawCenteredString(font, "Свинка-тян", 47, 114, 0xFFFFFF);
        text(g, shown, 14, 128, 292, 0xFFFFFF);
        if (!typing() && state != State.CHOICES && (ticks / 10) % 2 == 0)
            g.drawString(font, "▼", 302, 162, 0xFFFF88AA, false);

        if (state == State.CHOICES) {
            for (int i = 0; i < current.size(); i++) {
                button(g, 10, 34 + i * 26, 170, 22, (i + 1) + ". " + current.get(i).text, mx, my, 0x803060);
            }
        }
        if (state == State.ENDING && !typing()) {
            String title = switch (ending) { case 0 -> "ХОРОШАЯ КОНЦОВКА ♥"; case 2 -> "ПЛОХАЯ КОНЦОВКА"; default -> "НОРМАЛЬНАЯ КОНЦОВКА"; };
            int col = switch (ending) { case 0 -> 0xFF88CC; case 2 -> 0x8888FF; default -> 0xDDDDDD; };
            big(g, title, 96, 60, 1.6f, col);
            g.drawCenteredString(font, "(нажми, чтобы вернуться)", 96, 80, 0xFFFFFF);
        }
    }
}
