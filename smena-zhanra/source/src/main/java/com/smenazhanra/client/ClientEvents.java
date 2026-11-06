package com.smenazhanra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.smenazhanra.SmenaZhanra;
import com.smenazhanra.client.screen.GenreScreen;
import com.smenazhanra.server.Punishments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Всё, что рисуется в самом мире Minecraft и поверх него. */
public class ClientEvents {
    private static final ResourceLocation TV = new ResourceLocation(SmenaZhanra.MODID, "textures/gui/tv.png");
    private static final ResourceLocation BOW = new ResourceLocation(SmenaZhanra.MODID, "textures/gui/bow.png");

    @Mod.EventBusSubscriber(modid = SmenaZhanra.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("genre_shift", (gui, g, pt, w, h) -> ClientHooks.renderOverlay(g, w, h));
        }
    }

    @Mod.EventBusSubscriber(modid = SmenaZhanra.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        /** Если в момент отсчёта открыт инвентарь или другое окно — рисуем помехи поверх него. */
        @SubscribeEvent
        public static void screenPost(ScreenEvent.Render.Post e) {
            if (!(e.getScreen() instanceof GenreScreen) && ClientHooks.overlayActive()) {
                ClientHooks.renderOverlay(e.getGuiGraphics(), e.getScreen().width, e.getScreen().height);
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientHooks.reset();
        }

        /** Над игроком в другом жанре — мерцающий телевизор и надпись. Над преступником — «ПРЕСТУПНИК». */
        @SubscribeEvent
        public static void renderPlayer(RenderPlayerEvent.Post e) {
            Player p = e.getEntity();
            boolean inGenre = ClientHooks.IN_GENRE.getOrDefault(p.getUUID(), false);
            boolean criminal = ClientHooks.CRIMINAL.getOrDefault(p.getUUID(), false);
            if (!inGenre && !criminal) return;
            Minecraft mc = Minecraft.getInstance();
            if (p == mc.player && mc.screen != null) return; // фигурка в окне инвентаря
            PoseStack ps = e.getPoseStack();
            MultiBufferSource buf = e.getMultiBufferSource();
            int light = 0xF000F0;
            Font font = mc.font;
            ps.pushPose();
            ps.translate(0, p.getBbHeight() + 0.75, 0);
            ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            ps.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f m = ps.last().pose();
            float y = 0;
            if (criminal) {
                Component c = Component.literal("ПРЕСТУПНИК");
                font.drawInBatch(c, -font.width(c) / 2f, y, 0xFFFF3333, false, m, buf, Font.DisplayMode.NORMAL, 0x80000000, light);
                y -= 12;
            }
            if (inGenre) {
                String s = p.getGameProfile().getName() + " сейчас в другом жанре";
                font.drawInBatch(s, -font.width(s) / 2f, y, 0xFFFFFF55, false, m, buf, Font.DisplayMode.NORMAL, 0x80000000, light);
                // телевизор над надписью, кадр меняется — мерцание
                int frame = (int) ((System.currentTimeMillis() / 90) % 3);
                float flicker = 0.75f + 0.25f * Mth.sin(System.currentTimeMillis() / 60f);
                float u0 = frame * 32 / 96f, u1 = (frame * 32 + 32) / 96f;
                VertexConsumer vc = buf.getBuffer(RenderType.text(TV));
                quad(vc, m, -24, y - 42, 24, y - 3, u0, 0, u1, 1, flicker, light);
            }
            ps.popPose();
        }

        /** Свинка-тян носит бантик. */
        @SubscribeEvent
        public static void renderPig(RenderLivingEvent.Post<?, ?> e) {
            if (!(e.getEntity() instanceof Pig pig) || !pig.hasCustomName()) return;
            if (!pig.getCustomName().getString().equals(Punishments.PET_NAME)) return;
            Minecraft mc = Minecraft.getInstance();
            PoseStack ps = e.getPoseStack();
            float yaw = Mth.rotLerp(e.getPartialTick(), pig.yBodyRotO, pig.yBodyRot) * Mth.DEG_TO_RAD;
            float s = pig.isBaby() ? 0.5f : 1f;
            ps.pushPose();
            ps.translate(-Mth.sin(yaw) * 0.45 * s, 1.08 * s, Mth.cos(yaw) * 0.45 * s);
            ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            ps.scale(-0.025f * s, -0.025f * s, 0.025f * s);
            VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.text(BOW));
            quad(vc, ps.last().pose(), -10, -6, 10, 6, 0, 0, 1, 1, 1f, e.getPackedLight());
            ps.popPose();
        }
    }

    /** Двусторонний текстурированный квадрат. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1,
                             float u0, float v0, float u1, float v1, float bright, int light) {
        int c = (int) (bright * 255);
        vc.vertex(m, x0, y1, 0).color(c, c, c, 255).uv(u0, v1).uv2(light).endVertex();
        vc.vertex(m, x1, y1, 0).color(c, c, c, 255).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x1, y0, 0).color(c, c, c, 255).uv(u1, v0).uv2(light).endVertex();
        vc.vertex(m, x0, y0, 0).color(c, c, c, 255).uv(u0, v0).uv2(light).endVertex();
        vc.vertex(m, x0, y0, 0).color(c, c, c, 255).uv(u0, v0).uv2(light).endVertex();
        vc.vertex(m, x1, y0, 0).color(c, c, c, 255).uv(u1, v0).uv2(light).endVertex();
        vc.vertex(m, x1, y1, 0).color(c, c, c, 255).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x0, y1, 0).color(c, c, c, 255).uv(u0, v1).uv2(light).endVertex();
    }
}
