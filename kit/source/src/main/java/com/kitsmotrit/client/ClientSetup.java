package com.kitsmotrit.client;

import com.kitsmotrit.KitMod;
import com.kitsmotrit.entity.MaidWhale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = KitMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    static final ResourceLocation TEX = new ResourceLocation(KitMod.MODID, "textures/entity/maid_whale.png");
    static final ResourceLocation BLUSH_TEX = new ResourceLocation(KitMod.MODID, "textures/entity/maid_whale_blush.png");

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WhaleModel.LAYER, WhaleModel::createLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(KitMod.MAID_WHALE.get(), WhaleRenderer::new);
    }

    public static class WhaleRenderer extends MobRenderer<MaidWhale, WhaleModel> {
        public WhaleRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new WhaleModel(ctx.bakeLayer(WhaleModel.LAYER)), 0f);
            addLayer(new BlushLayer(this));
        }

        @Override
        protected void scale(MaidWhale e, PoseStack pose, float partial) {
            float s = 3f * e.getScale();
            pose.scale(s, s, s);
        }

        @Override
        protected void setupRotations(MaidWhale e, PoseStack pose, float age, float yaw, float partial) {
            // медленно покачивается, будто плывёт в воде
            pose.translate(0, Mth.sin(age * 0.05f) * 0.35f, 0);
            super.setupRotations(e, pose, age, yaw, partial);
            pose.mulPose(Axis.ZP.rotation(Mth.sin(age * 0.03f) * 0.04f));
            pose.mulPose(Axis.XP.rotation(Mth.sin(age * 0.05f + 1f) * 0.03f));
            if (e.getState() == MaidWhale.SHY && e.stateAge() < 12) { // вздрогнул
                float k = (12 - e.stateAge()) / 12f;
                pose.translate(Mth.sin(age * 3.1f) * 0.15f * k, Mth.sin(age * 2.3f) * 0.1f * k, 0);
            }
            if (e.getState() == MaidWhale.FLEE) pose.mulPose(Axis.XP.rotation(-0.5f)); // нос вверх — уплывает
        }

        @Override
        protected boolean isShaking(MaidWhale e) { return false; }

        @Nullable
        @Override
        protected RenderType getRenderType(MaidWhale e, boolean visible, boolean translucent, boolean glowing) {
            return RenderType.entityTranslucent(TEX); // для плавного проявления из прозрачности
        }

        @Override
        public ResourceLocation getTextureLocation(MaidWhale e) {
            return TEX;
        }
    }

    /** Румянец: розовые пятна на щеках, плавно ярче; от цветка — до фиолетового. */
    public static class BlushLayer extends RenderLayer<MaidWhale, WhaleModel> {
        public BlushLayer(RenderLayerParent<MaidWhale, WhaleModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, MaidWhale e, float limbSwing, float limbAmount,
                           float partial, float age, float headYaw, float headPitch) {
            float b = e.getBlush();
            if (b <= 0.01f) return;
            float r = 1f, g = 1f, bl = 1f;
            if (e.isPurple()) { r = 0.8f; g = 0.35f; bl = 1f; }
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(BLUSH_TEX)),
                    light, OverlayTexture.NO_OVERLAY, r, g, bl, Math.min(1f, b));
        }
    }
}
