package com.smenazhanra.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import com.smenazhanra.ModSounds;
import com.smenazhanra.client.Songs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** 🥊 Файтинг «Куриный Комбат». A/D — ходить, W — прыжок, J — удар, K — блок, J+K — суперприём. */
public class FightScreen extends GenreScreen {
    private static final int GROUND = 140, FW = 40, FH = 60;
    private static final float GRAVITY = 700, SPEED = 95, JUMP = -260;
    private static final String[] ENEMY_NAMES = {"Крипер", "Зомби", "Курица"};
    private static final String[] ENEMY_TEX = {"fighter_creeper", "fighter_zombie", "fighter_chicken"};

    private static class Fighter {
        float x, y = GROUND, vy;
        float hp = 100, meter;
        float attack;      // >0 — идёт удар (сек)
        boolean hitDone;
        float cooldown;
        float hurt;
        boolean blocking;
        boolean onGround() { return y >= GROUND; }
    }

    private static class Projectile {
        float x, y, vx; float rot; boolean arm; boolean alive = true;
    }

    private final int enemyType;
    private final Fighter me = new Fighter(), foe = new Fighter();
    private final List<Projectile> shots = new ArrayList<>();
    private long lastFrame;
    private float time;           // время с начала
    private float aiThink, aiBlock;
    private float special;        // >0 — враг готовит суперприём
    private float sooty, oneArm, explosion;
    private float mySuper;        // анимация нашего суперприёма
    private long jPressed, kPressed;
    private String banner = "";
    private float bannerTime;
    private float shake;
    private int result = -1;      // 0 победа, 1 поражение
    private float resultTime;
    private float roundTime;

    public FightScreen(GenreContext ctx) {
        super(ctx);
        music = Songs.fighting();
        enemyType = rnd.nextInt(3);
        me.x = 70;
        foe.x = 250;
        roundTime = ctx.seconds();
    }

    @Override
    protected boolean showTimer() { return false; }

    @Override
    protected void onStart() {
        lastFrame = System.nanoTime();
        sfx(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1f);
    }

    @Override
    protected void onTimeUp() {
        if (result < 0) end(me.hp >= foe.hp ? 0 : 1);
    }

    private boolean down(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }

    private boolean controllable() {
        return time > 2.0f && result < 0;
    }

    // ------------------------------------------------------------------ ввод
    @Override
    protected boolean keyGame(int key) {
        if (result >= 0) {
            if (resultTime > 1.5f && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER)) send();
            return true;
        }
        if (!controllable()) return true;
        long now = System.currentTimeMillis();
        if (key == GLFW.GLFW_KEY_J) {
            jPressed = now;
            if (now - kPressed < 180 || down(GLFW.GLFW_KEY_K)) trySuper();
            else punch();
            return true;
        }
        if (key == GLFW.GLFW_KEY_K) {
            kPressed = now;
            if (now - jPressed < 180) trySuper();
            return true;
        }
        if (key == GLFW.GLFW_KEY_W && me.onGround()) {
            me.vy = JUMP;
            sfx(SoundEvents.NOTE_BLOCK_BIT.value(), 1.5f, 0.3f);
            return true;
        }
        return false;
    }

    @Override
    protected void clickGame(int mx, int my) {
        if (result >= 0 && resultTime > 1.5f) send();
    }

    private void punch() {
        if (me.cooldown > 0 || me.attack > 0) return;
        me.attack = 0.25f;
        me.hitDone = false;
        me.cooldown = 0.38f;
        sfx(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.4f);
        // враг иногда успевает закрыться
        if (Math.abs(foe.x - me.x) < 60 && special <= 0 && aiBlock <= 0 && foe.attack <= 0 && rnd.nextInt(100) < 20) aiBlock = 0.35f;
    }

    private void trySuper() {
        if (me.meter < 100) {
            setBanner("Суперприём не заряжен!", 0.8f);
            return;
        }
        me.meter = 0;
        mySuper = 0.6f;
        setBanner("АЛМАЗНЫЙ АППЕРКОТ!", 1.2f);
        sfx(SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1f);
        sfx(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1f);
    }

    private void setBanner(String s, float t) {
        banner = s;
        bannerTime = t;
    }

    // ------------------------------------------------------------------ урон
    private void damageFoe(float dmg) {
        boolean blocked = aiBlock > 0 && special <= 0;
        float d = blocked ? dmg * 0.2f : dmg;
        foe.hp = Math.max(0, foe.hp - d);
        foe.hurt = 0.2f;
        foe.meter = Math.min(100, foe.meter + d * 0.8f);
        me.meter = Math.min(100, me.meter + d * 1.6f);
        foe.x += Math.signum(foe.x - me.x) * (blocked ? 4 : 10);
        shake = 0.15f;
        sfx(blocked ? SoundEvents.SHIELD_BLOCK : SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.8f);
        sfx(foeHurtSound(), 1.1f, 0.7f);
        if (foe.hp <= 0) end(0);
    }

    private void damageMe(float dmg) {
        if (result >= 0) return;
        boolean blocked = me.blocking;
        float d = blocked ? dmg * 0.25f : dmg;
        me.hp = Math.max(0, me.hp - d);
        me.hurt = 0.25f;
        me.meter = Math.min(100, me.meter + d * 1.2f);
        me.x += Math.signum(me.x - foe.x) * (blocked ? 4 : 14);
        shake = 0.2f;
        sfx(blocked ? SoundEvents.SHIELD_BLOCK : SoundEvents.PLAYER_HURT, 1f, 0.8f);
        if (me.hp <= 0) end(1);
    }

    private SoundEvent foeHurtSound() {
        return switch (enemyType) {
            case 0 -> SoundEvents.CREEPER_HURT;
            case 1 -> SoundEvents.ZOMBIE_HURT;
            default -> SoundEvents.CHICKEN_HURT;
        };
    }

    private void end(int r) {
        if (result >= 0) return;
        result = r;
        resultTime = 0;
        if (r == 0) {
            sfx(ModSounds.APPLAUSE.get(), 1f, 1f);
            sfx(SoundEvents.PLAYER_LEVELUP, 1f, 0.8f);
        } else {
            sfx(SoundEvents.VILLAGER_NO, 0.8f, 1f);
        }
    }

    private void send() {
        finish(result == 0 ? 0 : 1, enemyType);
    }

    // ------------------------------------------------------------------ симуляция
    private void update(float dt) {
        time += dt;
        if (bannerTime > 0) bannerTime -= dt;
        if (shake > 0) shake -= dt;
        if (result >= 0) {
            resultTime += dt;
            if (resultTime > 8) send();
            return;
        }
        if (time > 2.0f) roundTime -= dt;
        if (roundTime <= 0) { end(me.hp >= foe.hp ? 0 : 1); return; }
        if (time < 2.0f) return;

        // ---- игрок
        me.blocking = down(GLFW.GLFW_KEY_K) && me.attack <= 0 && mySuper <= 0;
        float mv = 0;
        if (!me.blocking && mySuper <= 0) {
            if (down(GLFW.GLFW_KEY_A)) mv -= SPEED;
            if (down(GLFW.GLFW_KEY_D)) mv += SPEED;
        }
        me.x += mv * dt;
        physics(me, dt);
        if (me.attack > 0) {
            me.attack -= dt;
            if (!me.hitDone && me.attack < 0.15f) {
                me.hitDone = true;
                if (Math.abs(foe.x - me.x) < 46 && Math.abs(foe.y - me.y) < 36) damageFoe(7);
            }
        }
        if (mySuper > 0) {
            float before = mySuper;
            mySuper -= dt;
            me.x += Math.signum(foe.x - me.x) * 220 * dt;
            if (before > 0.3f && mySuper <= 0.3f && Math.abs(foe.x - me.x) < 70) damageFoe(25);
        }
        if (me.cooldown > 0) me.cooldown -= dt;
        if (me.hurt > 0) me.hurt -= dt;

        // ---- противник
        updateFoe(dt);
        physics(foe, dt);
        if (foe.hurt > 0) foe.hurt -= dt;

        // не проходят сквозь друг друга
        if (Math.abs(me.x - foe.x) < 22 && Math.abs(me.y - foe.y) < 30) {
            float push = (22 - Math.abs(me.x - foe.x)) / 2;
            float s = Math.signum(me.x - foe.x);
            if (s == 0) s = -1;
            me.x += s * push;
            foe.x -= s * push;
        }
        me.x = Math.max(14, Math.min(306, me.x));
        foe.x = Math.max(14, Math.min(306, foe.x));

        // ---- снаряды
        for (Projectile p : shots) {
            if (!p.alive) continue;
            p.x += p.vx * dt;
            p.rot += 720 * dt;
            // от бегущих куриц спасает прыжок; от летящей руки — прыжок повыше
            boolean dodged = p.arm ? me.y < GROUND - 34 : me.y < GROUND - 12;
            if (Math.abs(p.x - me.x) < 12 && !dodged) {
                p.alive = false;
                damageMe(p.arm ? 15 : 4);
                if (!p.arm) sfx(SoundEvents.CHICKEN_HURT, 1.3f, 0.6f);
            }
            if (p.x < -20 || p.x > VW + 20) p.alive = false;
        }
        shots.removeIf(p -> !p.alive);
    }

    private void physics(Fighter f, float dt) {
        f.vy += GRAVITY * dt;
        f.y += f.vy * dt;
        if (f.y >= GROUND) { f.y = GROUND; f.vy = 0; }
    }

    private void updateFoe(float dt) {
        if (aiBlock > 0) aiBlock -= dt;
        if (sooty > 0) sooty -= dt;
        if (oneArm > 0) {
            oneArm -= dt;
            if (oneArm <= 0) setBanner("Рука зомби приросла обратно!", 1f);
        }
        if (explosion > 0) explosion -= dt;
        foe.meter = Math.min(100, foe.meter + dt * 8);
        float dist = me.x - foe.x;

        // суперприём противника
        if (special > 0) {
            float before = special;
            special -= dt;
            if (enemyType == 0) {
                if ((int) (special * 10) % 3 == 0) sfx(SoundEvents.CREEPER_PRIMED, 1f, 0.15f);
                if (special <= 0) {
                    explosion = 0.6f;
                    sooty = 3f;
                    shake = 0.5f;
                    sfx(SoundEvents.GENERIC_EXPLODE, 1f, 1f);
                    if (Math.abs(dist) < 85) damageMe(22);
                    setBanner("Крипер взорвался... но остался жив!", 1.6f);
                }
            } else if (enemyType == 1) {
                if (before > 0.3f && special <= 0.3f) {
                    Projectile p = new Projectile();
                    p.x = foe.x; p.y = GROUND - 30; p.vx = Math.signum(dist) * 210; p.arm = true;
                    shots.add(p);
                    oneArm = 4f;
                    sfx(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.3f, 0.6f);
                }
            } else if (special <= 0) {
                for (int i = 0; i < 6; i++) {
                    Projectile p = new Projectile();
                    float side = dist > 0 ? -1 : 1; // бегут со стороны курицы
                    p.x = side > 0 ? VW + 10 + i * 26 : -10 - i * 26;
                    p.y = GROUND - 8;
                    p.vx = -side * (150 + rnd.nextInt(40));
                    shots.add(p);
                }
                sfx(SoundEvents.CHICKEN_AMBIENT, 1f, 1f);
                sfx(SoundEvents.CHICKEN_AMBIENT, 1.4f, 1f);
                setBanner("Курица призвала стаю! Прыгай (W)!", 1.6f);
            }
            return;
        }
        if (foe.meter >= 100 && Math.abs(dist) < 140) {
            foe.meter = 0;
            special = switch (enemyType) { case 0 -> 1.2f; case 1 -> 0.7f; default -> 0.6f; };
            setBanner(switch (enemyType) {
                case 0 -> "Крипер шипит... ОН СЕЙЧАС ВЗОРВЁТСЯ!";
                case 1 -> "Зомби отрывает себе руку!..";
                default -> "Курица кричит: КО-КО-КООООО!";
            }, 1.2f);
            if (enemyType == 2) sfx(SoundEvents.CHICKEN_AMBIENT, 0.6f, 1f);
            return;
        }

        aiThink -= dt;
        if (foe.attack > 0) {
            foe.attack -= dt;
            if (!foe.hitDone && foe.attack < 0.12f) {
                foe.hitDone = true;
                if (Math.abs(dist) < 46 && Math.abs(me.y - foe.y) < 36) damageMe(oneArm > 0 ? 4 : 6);
            }
            return;
        }
        if (foe.cooldown > 0) foe.cooldown -= dt;
        if (Math.abs(dist) > 38) {
            foe.x += Math.signum(dist) * (enemyType == 2 ? 80 : 60) * dt;
        } else if (foe.cooldown <= 0) {
            foe.attack = 0.4f;
            foe.hitDone = false;
            foe.cooldown = 0.8f + rnd.nextFloat() * 0.6f;
        }
        if (aiThink <= 0) {
            aiThink = 0.8f + rnd.nextFloat();
            if (rnd.nextInt(5) == 0) foe.x -= Math.signum(dist) * 20; // отступает
            if (enemyType == 2 && rnd.nextInt(4) == 0 && foe.onGround()) foe.vy = JUMP * 0.8f;
        }
    }

    // ------------------------------------------------------------------ рисование
    @Override
    protected void renderGame(GuiGraphics g, int mx, int my, float pt) {
        long nowN = System.nanoTime();
        float dt = Math.min(0.05f, (nowN - lastFrame) / 1e9f);
        lastFrame = nowN;
        update(dt);

        g.pose().pushPose();
        if (shake > 0) g.pose().translate((rnd.nextFloat() - 0.5f) * 6, (rnd.nextFloat() - 0.5f) * 6, 0);
        sprite(g, tex("fight_bg"), 0, 0, VW, VH, 320, 180);

        boolean meRight = foe.x > me.x;
        // игрок
        if (result == 1) {
            // лежит, а противник танцует над ним
            g.pose().pushPose();
            g.pose().translate(lieX(), GROUND - FW / 2f + 4, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(90));
            frame(g, tex("fighter_player"), -FW / 2, -FH, FW, FH, 3, 32, 48, 128, 48, false);
            g.pose().popPose();
        } else {
            int f = me.hurt > 0 ? 3 : me.blocking ? 2 : (me.attack > 0 || mySuper > 0) ? 1 : 0;
            frame(g, tex("fighter_player"), (int) me.x - FW / 2, (int) me.y - FH, FW, FH, f, 32, 48, 128, 48, !meRight);
            if (mySuper > 0) g.fill((int) me.x - 3, (int) me.y - FH - 6, (int) me.x + 3, (int) me.y - 4, 0x8055FFFF);
        }

        // противник
        drawFoe(g, meRight);

        // снаряды
        for (Projectile p : shots) {
            if (p.arm) {
                g.pose().pushPose();
                g.pose().translate(p.x, p.y, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(p.rot));
                sprite(g, tex("zombie_arm"), -12, -4, 24, 9, 16, 6);
                g.pose().popPose();
            } else {
                frame(g, tex("chicken"), (int) p.x - 8, GROUND - 17 - ((int) (time * 20) % 2), 16, 17, 0, 14, 15, 14, 15, p.vx > 0);
            }
        }
        if (explosion > 0) {
            int s = (int) (60 + (0.6f - explosion) * 120);
            sprite(g, tex("explosion"), (int) foe.x - s / 2, GROUND - 30 - s / 2, s, s, 48, 48);
        }
        g.pose().popPose();

        // интерфейс: полоски здоровья
        hpBar(g, 8, 6, me.hp, false, ctx.playerName());
        hpBar(g, 192, 6, foe.hp, true, ENEMY_NAMES[enemyType]);
        meterBar(g, 8, 26, me.meter, "СУПЕР (J+K)");
        meterBar(g, 252, 26, foe.meter, "");
        int left = (int) Math.ceil(Math.max(0, roundTime));
        box(g, 146, 4, 28, 18, 0xCC000000, 0xFFFFDD55);
        g.drawCenteredString(font, String.valueOf(left), 160, 9, left <= 10 ? 0xFF5555 : 0xFFFFFF);

        if (time < 1.0f) big(g, "РАУНД 1", VW / 2, 60, 3f, 0xFFFFFF);
        else if (time < 2.0f) big(g, "FIGHT!", VW / 2, 56, 4f, 0xFF4444);
        if (time < 2.4f) g.drawCenteredString(font, "A/D — ходить  W — прыжок  J — удар  K — блок  J+K — суперприём", VW / 2, 100, 0xFFFFFF);
        if (bannerTime > 0 && result < 0) big(g, banner, VW / 2, 46, 1.3f, 0xFFDD55);

        if (result == 0) {
            big(g, "БЕЗУПРЕЧНАЯ ПОБЕДА", VW / 2, 52, 2.4f, 0xFFDD33);
            if (me.hp >= 100) g.drawCenteredString(font, "(и правда безупречная — ни одной царапины!)", VW / 2, 78, 0xFFFFFF);
        } else if (result == 1) {
            big(g, "ПОРАЖЕНИЕ", VW / 2, 52, 2.4f, 0xFF4444);
            g.drawCenteredString(font, ENEMY_NAMES[enemyType] + " исполняет победный танец...", VW / 2, 78, 0xFFFFFF);
        }
        if (result >= 0 && resultTime > 1.5f) g.drawCenteredString(font, "(нажми, чтобы вернуться в Minecraft)", VW / 2, 90, 0xAAAAAA);
    }

    /** Левый край лежащего игрока — так, чтобы он целиком был в кадре. */
    private float lieX() {
        return Math.max(4, Math.min(VW - FH - 4, me.x - FH / 2f));
    }

    private void drawFoe(GuiGraphics g, boolean meRight) {
        ResourceLocation t = tex(ENEMY_TEX[enemyType]);
        int f;
        boolean flip; // спрайты зомби и курицы нарисованы лицом вправо
        if (result == 1) {
            // танец над поверженным игроком
            int beat = (int) (resultTime * 4);
            f = enemyType == 2 ? (beat % 2 == 0 ? 3 : 2) : enemyType == 1 ? (beat % 2) : (beat % 2 == 0 ? 0 : 2);
            flip = beat % 2 == 0;
            int hop = (int) (Math.abs(Math.sin(resultTime * 6)) * 14);
            frame(g, t, (int) lieX() + FH / 2 - FW / 2, GROUND - FH - 22 - hop, FW, FH, f, 32, 48, 128, 48, flip);
            return;
        }
        if (enemyType == 0) {
            f = special > 0 ? ((int) (special * 8) % 2 == 0 ? 2 : 0) : sooty > 0 ? 3 : foe.attack > 0 ? 1 : 0;
            flip = !meRight;
            if (foe.attack > 0 && !meRight) flip = true;
        } else if (enemyType == 1) {
            boolean noArm = oneArm > 0 || (special > 0 && special < 0.3f);
            f = noArm ? (foe.attack > 0 ? 3 : 2) : (foe.attack > 0 ? 1 : 0);
            flip = meRight;
        } else {
            f = special > 0 ? 2 : foe.attack > 0 ? 1 : (!foe.onGround() ? 2 : 0);
            flip = meRight;
        }
        if (foe.hurt > 0 && (int) (foe.hurt * 30) % 2 == 0) g.setColor(1f, 0.5f, 0.5f, 1f);
        frame(g, t, (int) foe.x - FW / 2, (int) foe.y - FH, FW, FH, f, 32, 48, 128, 48, flip);
        g.setColor(1f, 1f, 1f, 1f);
        if (aiBlock > 0 && special <= 0) g.drawCenteredString(font, "блок", (int) foe.x, (int) foe.y - FH - 10, 0x88CCFF);
    }

    private void hpBar(GuiGraphics g, int x, int y, float hp, boolean rightAlign, String name) {
        box(g, x, y, 120, 12, 0xCC000000, 0xFFFFFFFF);
        int w = (int) (116 * hp / 100f);
        int col = hp > 50 ? 0xFF33DD33 : hp > 25 ? 0xFFDDDD33 : 0xFFDD3333;
        if (rightAlign) g.fill(x + 2 + 116 - w, y + 2, x + 118, y + 10, col);
        else g.fill(x + 2, y + 2, x + 2 + w, y + 10, col);
        g.drawString(font, name, rightAlign ? x + 118 - font.width(name) : x + 2, y + 14, 0xFFFFFF, true);
    }

    private void meterBar(GuiGraphics g, int x, int y, float m, String label) {
        box(g, x, y + 8, 60, 5, 0xCC000000, 0xFF8888FF);
        g.fill(x + 1, y + 9, x + 1 + (int) (58 * m / 100f), y + 12, m >= 100 ? ((int) (time * 6) % 2 == 0 ? 0xFF55FFFF : 0xFFFFFFFF) : 0xFF5555FF);
        if (!label.isEmpty()) g.drawString(font, label, x + 64, y + 6, m >= 100 ? 0x55FFFF : 0xAAAAAA, true);
    }
}
