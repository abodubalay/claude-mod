package com.kitsmotrit.client;

import com.kitsmotrit.KitConfig;
import com.kitsmotrit.KitMod;
import com.kitsmotrit.Network;
import com.kitsmotrit.entity.MaidWhale;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Пока кит рядом: небо чуть темнеет, обычная музыка затихает; клик по киту — «удар». */
@Mod.EventBusSubscriber(modid = KitMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static float darkness, prevDarkness;
    private static boolean whaleNear;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean near = false;
        float fade = 0f;
        if (mc.level != null && mc.player != null) {
            for (MaidWhale w : mc.level.getEntitiesOfClass(MaidWhale.class, mc.player.getBoundingBox().inflate(96))) {
                near = true;
                fade = Math.max(fade, w.fade(0));
            }
        }
        if (near && !whaleNear) mc.getMusicManager().stopPlaying(); // обычная музыка затихает
        whaleNear = near;
        prevDarkness = darkness;
        float target = near && KitConfig.SKY_DARKENING.get() ? fade : 0f;
        darkness += (target - darkness) * 0.05f;
    }

    private static float dark(float partial) {
        return prevDarkness + (darkness - prevDarkness) * partial;
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float d = dark((float) event.getPartialTick());
        if (d <= 0.001f) return;
        float k = 1f - 0.35f * d;
        event.setRed(event.getRed() * k);
        event.setGreen(event.getGreen() * k);
        event.setBlue(event.getBlue() * (1f - 0.25f * d));
    }

    /** Затемняем небо: полупрозрачная тёмная завеса сразу после неба — земля и постройки рисуются поверх. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float d = dark(event.getPartialTick());
        if (d <= 0.001f) return;
        Matrix4f oldProj = RenderSystem.getProjectionMatrix();
        VertexSorting oldSort = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, 1f, 1f, 0f, -1f, 1f), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float a = 0.32f * d;
        b.vertex(0, 0, 0).color(0.02f, 0.02f, 0.08f, a).endVertex();
        b.vertex(0, 1, 0).color(0.02f, 0.02f, 0.08f, a).endVertex();
        b.vertex(1, 1, 0).color(0.02f, 0.02f, 0.08f, a).endVertex();
        b.vertex(1, 0, 0).color(0.02f, 0.02f, 0.08f, a).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProj, oldSort);
    }

    /** Пока кит рядом — обычная музыка игры не включается. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (whaleNear && event.getSound() != null && event.getSound().getSource() == SoundSource.MUSIC) event.setSound(null);
    }

    /** Клик левой кнопкой, когда смотришь на кита, — «удар» (кит слишком высоко, рукой не дотянуться). */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (MaidWhale w : mc.level.getEntitiesOfClass(MaidWhale.class, mc.player.getBoundingBox().inflate(96)))
            if (w.isLookedAtBy(mc.player)) { Network.CHANNEL.sendToServer(new Network.PunchPacket()); return; }
    }
}
