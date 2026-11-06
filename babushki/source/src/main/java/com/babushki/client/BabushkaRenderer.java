package com.babushki.client;

import com.babushki.BabushkiMod;
import com.babushki.entity.BabushkaEntity;
import com.babushki.entity.Role;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BabushkaRenderer extends MobRenderer<BabushkaEntity, BabushkaModel> {
    private static final ResourceLocation VALYA = new ResourceLocation(BabushkiMod.MODID, "textures/entity/baba_valya.png");
    private static final ResourceLocation ZINA = new ResourceLocation(BabushkiMod.MODID, "textures/entity/baba_zina.png");

    public BabushkaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BabushkaModel(ctx.bakeLayer(BabushkaModel.LAYER)), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(BabushkaEntity e) {
        return e.getRole() == Role.VALYA ? VALYA : ZINA;
    }

    @Override
    public void render(BabushkaEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        if (e.isSitting()) pose.translate(0, -0.03, 0);
        super.render(e, yaw, partial, pose, buf, light);
        pose.popPose();
    }

    @Override
    protected void scale(BabushkaEntity e, PoseStack pose, float partial) {
        // Бабушки чуть ниже и «пошире» обычного жителя.
        pose.scale(0.95f, 0.95f, 0.95f);
    }
}
