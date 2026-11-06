package com.kino.client;

import com.kino.KinoMod;
import com.kino.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.*;

/** Титры «как в конце серии»: плывут снизу вверх 12 секунд, любая клавиша — пропустить. */
public class CreditsScreen extends Screen {
    private static final int DURATION = 260; // ~13 секунд
    private final List<Component> lines = new ArrayList<>();
    @Nullable private final Screen previous;
    private int ticks;
    private SoundInstance music;

    public CreditsScreen(Network.Credits c, @Nullable Screen previous) {
        super(Component.literal("Титры"));
        this.previous = previous;
        add("§7— КОНЕЦ СЕРИИ —");
        add("");
        add("§7В главной роли");
        add("§e§l" + c.player());
        add("");
        if (!c.cast().isEmpty()) {
            add("§7В ролях:");
            for (int i = 0; i < c.cast().size(); i++) add(role(c.cast().get(i), c.counts().get(i)));
            add("");
        }
        add(c.chickens() > 0 ? "Ни одна курица не пострадала при съёмках §7(это ложь)" : "Ни одна курица не пострадала при съёмках §7(правда!)");
        add(c.deaths() > 0 ? "Каскадёр игрока погиб " + c.deaths() + " " + times(c.deaths())
                : "Каскадёр игрока ни разу не погиб. Подозрительно.");
        add("Ни один блок не был сломан зря. §7Ну, почти.");
        add("Режиссёр — случайность");
        add("Закадровый смех исполнили жители деревни");
        add("");
        add("§6§lПродолжение следует...");
    }

    private static String times(int n) {
        int m = n % 100;
        if (m >= 11 && m <= 14) return "раз";
        return switch (n % 10) { case 2, 3, 4 -> "раза"; default -> "раз"; };
    }

    private void add(String s) { lines.add(Component.literal(s)); }

    /** Смешная роль для моба. */
    private static String role(String id, int count) {
        String name = EntityType.byString(id).map(t -> t.getDescription().getString()).orElse(id);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String n = count > 1 ? " №" + count : "";
        return switch (path) {
            case "cow" -> name + n + " — в роли самой себя";
            case "creeper" -> name + " — в роли крипера, который взорвался";
            case "chicken" -> name + " — в роли курицы, которая всё видела";
            case "pig" -> name + n + " — в роли будущего бекона";
            case "sheep" -> name + n + " — в роли пушистого облака";
            case "zombie" -> name + " — в роли зомби, который хотел обнимашек";
            case "skeleton" -> name + " — в роли лучника, который так и не попал";
            case "spider" -> name + " — в роли паука на потолке";
            case "enderman" -> name + " — в роли того, на кого нельзя смотреть";
            case "villager" -> name + n + " — в роли жителя, который хмыкал";
            case "wolf" -> name + " — в роли лучшего друга";
            case "cat" -> name + " — в роли главного начальника";
            case "horse" -> name + " — в роли транспорта";
            case "squid", "glow_squid" -> name + " — в роли загадки океана";
            case "bat" -> name + " — в роли того, кто всех напугал";
            case "chicken_queen" -> name + " — в роли самой себя (и она этим гордится)";
            case "maid_whale" -> name + " — в роли того, кто не смотрел. Совсем.";
            case "companion" -> name + " — в роли напарника, который всё сделал";
            default -> name + n + " — в роли самого себя";
        };
    }

    @Override
    protected void init() {
        Minecraft.getInstance().getToasts().clear();
        if (music == null) {
            music = SimpleSoundInstance.forUI(KinoMod.CREDITS_MUSIC.get(), 1f, 0.8f);
            Minecraft.getInstance().getSoundManager().play(music);
        }
    }

    @Override
    public void tick() {
        if (++ticks >= DURATION) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xF0000000);
        float t = (ticks + partial) / DURATION;
        int lineH = 14;
        int total = lines.size() * lineH;
        float y = height - t * (height + total) + (t > 0.85f ? 0 : 0);
        // последняя строка («Продолжение следует...») останавливается по центру
        float stopY = height / 2f - (lines.size() - 1) * lineH;
        if (y < stopY) y = stopY;
        for (int i = 0; i < lines.size(); i++) {
            Component c = lines.get(i);
            int ly = (int) (y + i * lineH);
            if (ly < -lineH || ly > height) continue;
            g.drawString(font, c, (width - font.width(c)) / 2, ly, 0xFFFFFF, true);
        }
        Component hint = Component.translatable("kino.skip");
        g.drawString(font, hint, width - font.width(hint) - 6, height - 12, 0x666666, false);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) { onClose(); return true; }
    @Override
    public boolean mouseClicked(double x, double y, int b) { onClose(); return true; }
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (music != null) mc.getSoundManager().stop(music);
        if (mc.player != null && mc.player.isSleeping()) mc.setScreen(new InBedChatScreen());
        else mc.setScreen(previous instanceof InBedChatScreen ? null : previous);
    }
}
