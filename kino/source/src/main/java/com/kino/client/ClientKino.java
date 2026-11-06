package com.kino.client;

import com.kino.KinoConfig;
import com.kino.KinoMod;
import com.kino.Network;
import com.kino.Sitcom;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Клиент: кат-сцены (полосы, титр, облёт камеры), звуки зала, титры. */
@Mod.EventBusSubscriber(modid = KinoMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientKino {
    private ClientKino() {}

    private static final Random RANDOM = new Random();

    // ---------- кат-сцена ----------

    private static Network.Scene scene;
    private static int sceneTick;          // тиков с начала
    private static CameraType savedCamera;
    private static float orbitStart;
    private static SoundInstance music;
    private static boolean boomPlayed;
    private static final List<Integer> queuedSitcom = new ArrayList<>(); // реакции зала ждут конца кат-сцены

    public static boolean inScene() { return scene != null; }

    public static void startScene(Network.Scene s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (scene != null) endScene(false);
        scene = s;
        sceneTick = 0;
        boomPlayed = false;
        mc.getToasts().clear(); // всплывающие подсказки не загораживают титр
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); // камера облетает игрока
        orbitStart = mc.player.getYRot() + 140f;
        music = SimpleSoundInstance.forUI(KinoMod.DRAMA.get(), 1f, 0.9f);
        mc.getSoundManager().play(music);
        // «замершие» в воздухе частицы вокруг
        for (int i = 0; i < 40; i++) {
            Vec3 p = mc.player.position().add((RANDOM.nextDouble() - .5) * 8, RANDOM.nextDouble() * 3, (RANDOM.nextDouble() - .5) * 8);
            mc.level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    private static void endScene(boolean tellServer) {
        Minecraft mc = Minecraft.getInstance();
        if (scene == null) return;
        scene = null;
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        if (music != null) mc.getSoundManager().stop(music);
        if (tellServer) Network.CHANNEL.sendToServer(new Network.Skip());
        for (int t : queuedSitcom) playSitcom(t);
        queuedSitcom.clear();
    }

    private static float progress(float partial) {
        return scene == null ? 0f : Mth.clamp((sceneTick + partial) / scene.ticks(), 0f, 1f);
    }

    /** Любая клавиша — пропустить. */
    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (scene != null && e.getAction() == GLFW.GLFW_PRESS && sceneTick > 6) endScene(true);
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre e) {
        if (scene != null && e.getAction() == GLFW.GLFW_PRESS && sceneTick > 6) { endScene(true); e.setCanceled(true); }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        tickSounds(mc);
        if (scene == null) return;
        if (mc.player == null || mc.level == null) { scene = null; return; }
        sceneTick++;
        if (!boomPlayed && sceneTick >= scene.ticks() - 22) { // громкий «БУМ» в конце
            boomPlayed = true;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(KinoMod.BOOM.get(), 1f, 1f));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EXPLODE, 0.55f, 0.5f));
        }
        if (sceneTick >= scene.ticks()) endScene(false);
    }

    /** Камера плавно облетает игрока. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (scene == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float partial = (float) e.getPartialTick();
        float p = progress(partial);
        float ease = p * p * (3 - 2 * p);
        float angle = orbitStart + ease * 110f;            // медленно — как в замедленной съёмке
        double radius = 4.2 - ease * 0.8;
        Vec3 eye = mc.player.getEyePosition(partial).subtract(0, 0.3, 0);
        double rad = Math.toRadians(angle);
        Vec3 cam = eye.add(-Math.sin(rad) * radius, 0.6 + ease * 0.4, Math.cos(rad) * radius);
        e.getCamera().setPosition(cam.x, cam.y, cam.z);
        Vec3 d = eye.subtract(cam);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        e.setYaw(yaw);
        e.setPitch(pitch);
        e.setRoll(Mth.sin(ease * Mth.PI) * 2f);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (scene != null) e.setFOV(e.getFOV() * 0.82);
    }

    /** Во время кат-сцены прячем интерфейс игры. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (scene != null) e.setCanceled(true);
    }

    /** Чёрные полосы и титр. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        if (scene == null) return;
        GuiGraphics g = e.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = e.getPartialTick();
        float t = sceneTick + partial;
        float in = Mth.clamp(t / 10f, 0f, 1f), out = Mth.clamp((scene.ticks() - t) / 8f, 0f, 1f);
        int bar = (int) (h * 0.13f * Math.min(in, out));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        Font font = mc.font;
        // большой титр по центру — появляется через полсекунды, «бьёт» с БУМом
        float ta = Mth.clamp((t - 10) / 12f, 0f, 1f) * out;
        if (ta > 0.02f) {
            float boomPop = boomPlayed ? Math.max(0f, 1f - (t - (scene.ticks() - 22)) / 6f) * 0.25f : 0f;
            float scale = Math.min(3.2f, (w * 0.9f) / Math.max(1, font.width(scene.title()))) * (1f + boomPop);
            int alpha = (int) (ta * 255) << 24;
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f - 10, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, scene.title(), -font.width(scene.title()) / 2, -4, 0xF5E6B8 | alpha, true);
            g.pose().popPose();
            if (!scene.subtitle().isEmpty() && t > scene.ticks() * 0.6f) {
                float sa = Mth.clamp((t - scene.ticks() * 0.6f) / 10f, 0f, 1f) * out;
                Component sub = Component.literal(scene.subtitle()).withStyle(s -> s.withItalic(true));
                g.drawString(font, sub, (w - font.width(sub)) / 2, (int) (h / 2f + 16 * scale / 3.2f + 10), 0xBBBBBB | ((int) (sa * 255) << 24), true);
            }
        }
        // вспышка на «БУМ»
        if (boomPlayed) {
            float f = Math.max(0f, 1f - (t - (scene.ticks() - 22)) / 5f);
            if (f > 0) g.fill(0, 0, w, h, ((int) (f * 120) << 24) | 0xFFFFFF);
        }
        Component hint = Component.translatable("kino.skip");
        if (bar > 6) g.drawString(font, hint, w - font.width(hint) - 8, h - bar / 2 - 4, 0x777777, false);
    }

    // ---------- ситком: зал собран из звуков жителей ----------

    private record Planned(int at, SoundEvent sound, float pitch, float volume) {}
    private static final List<Planned> planned = new ArrayList<>();
    private static int soundClock;

    public static void sitcom(int type) {
        if (scene != null) { if (queuedSitcom.isEmpty()) queuedSitcom.add(type); return; } // после кат-сцены
        playSitcom(type);
    }

    private static void plan(int delay, SoundEvent s, float pitch, float vol) {
        float v = (float) (vol * KinoConfig.SITCOM_VOLUME.get());
        if (v > 0.001f) planned.add(new Planned(soundClock + delay, s, Mth.clamp(pitch, 0.5f, 2f), v));
    }

    /** Смех: десяток ускоренных «хе-хе» и «хм» жителей, наложенных друг на друга. */
    private static void laugh(int start, int n, float vol) {
        SoundEvent[] voices = {SoundEvents.VILLAGER_CELEBRATE, SoundEvents.VILLAGER_YES, SoundEvents.VILLAGER_TRADE, SoundEvents.VILLAGER_AMBIENT};
        for (int i = 0; i < n; i++)
            plan(start + RANDOM.nextInt(36), voices[RANDOM.nextInt(voices.length)], 1.45f + RANDOM.nextFloat() * 0.55f, vol * (0.45f + RANDOM.nextFloat() * 0.5f));
    }

    /** «Ооо»: протяжные низкие «хм». */
    private static void ooh(int start, float vol) {
        for (int i = 0; i < 9; i++) plan(start + RANDOM.nextInt(8), SoundEvents.VILLAGER_AMBIENT, 0.62f + RANDOM.nextFloat() * 0.15f, vol * 0.7f);
        for (int i = 0; i < 6; i++) plan(start + 8 + RANDOM.nextInt(10), SoundEvents.VILLAGER_AMBIENT, 0.55f + RANDOM.nextFloat() * 0.1f, vol * 0.5f);
    }

    public static void playSitcom(int type) {
        switch (type) {
            case Sitcom.LAUGH -> laugh(0, 14, 1f);                                   // громкий смех зала
            case Sitcom.LAUGH_OOH -> { laugh(0, 12, 1f); ooh(30, 0.9f); }           // смех + «оооо»
            case Sitcom.OOH_CHUCKLE -> { ooh(0, 0.8f); laugh(38, 4, 0.6f); }        // сочувственно, потом смешок
            case Sitcom.CHEER -> {                                                  // аплодисменты и одобрительные крики
                plan(0, KinoMod.APPLAUSE.get(), 1f, 1f);
                for (int i = 0; i < 8; i++) plan(2 + RANDOM.nextInt(40), SoundEvents.VILLAGER_CELEBRATE, 1.2f + RANDOM.nextFloat() * 0.5f, 0.7f);
                plan(5, SoundEvents.PLAYER_LEVELUP, 1.4f, 0.35f);
            }
            case Sitcom.AWW -> {                                                    // «ааааа» умилённо
                for (int i = 0; i < 10; i++) plan(RANDOM.nextInt(10), SoundEvents.VILLAGER_AMBIENT, 1.05f + RANDOM.nextFloat() * 0.2f, 0.55f);
                plan(4, SoundEvents.CAT_PURREOW, 1.3f, 0.4f);
            }
            case Sitcom.APPLAUSE -> plan(0, KinoMod.APPLAUSE.get(), 1f, 1f);       // аплодисменты
            case Sitcom.BOO -> {                                                    // осуждающее «уууу»
                for (int i = 0; i < 12; i++) plan(RANDOM.nextInt(16), SoundEvents.VILLAGER_NO, 0.55f + RANDOM.nextFloat() * 0.2f, 0.7f);
            }
            default -> {}
        }
    }

    private static void tickSounds(Minecraft mc) {
        soundClock++;
        if (planned.isEmpty()) return;
        Iterator<Planned> it = planned.iterator();
        while (it.hasNext()) {
            Planned p = it.next();
            if (p.at <= soundClock) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(p.sound, p.pitch, p.volume));
                it.remove();
            }
        }
    }

    // ---------- титры ----------

    public static void credits(Network.Credits c) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new CreditsScreen(c, mc.screen));
    }
}
