package com.kitsmotrit.client;

import com.kitsmotrit.KitMod;
import com.kitsmotrit.entity.MaidWhale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Кит (в 3 раза больше этой модели ≈ 20 блоков) с ободком, бантиком, манжетами и глазами, которые расширяются и моргают. */
public class WhaleModel extends EntityModel<MaidWhale> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(KitMod.MODID, "maid_whale"), "main");

    private final ModelPart body, head, tail1, tail2, flukes, rightFin, leftFin, rightEye, leftEye, rightLid, leftLid;
    /** Прозрачность (проявление/растворение) — задаётся перед отрисовкой. */
    float alpha = 1f;

    public WhaleModel(ModelPart root) {
        body = root.getChild("body");
        head = body.getChild("head");
        tail1 = body.getChild("tail1");
        tail2 = tail1.getChild("tail2");
        flukes = tail2.getChild("flukes");
        rightFin = body.getChild("right_fin");
        leftFin = body.getChild("left_fin");
        rightEye = head.getChild("right_eye");
        leftEye = head.getChild("left_eye");
        rightLid = head.getChild("right_lid");
        leftLid = head.getChild("left_lid");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-10f, -18f, -22f, 20, 18, 44), PartPose.offset(0f, 24f, 0f));
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 64).addBox(-9f, -8f, -20f, 18, 16, 20), PartPose.offset(0f, -9f, -22f));
        // белый кружевной ободок
        CubeListBuilder band = CubeListBuilder.create().texOffs(140, 100).addBox(-9.5f, -9.5f, -13f, 19, 2, 3);
        for (int i = 0; i < 5; i++) band.texOffs(190, 100).addBox(-9f + i * 4f, -10.5f, -12.5f, 2, 1, 1);
        head.addOrReplaceChild("headband", band, PartPose.ZERO);
        // бантик под подбородком, на «шее»
        head.addOrReplaceChild("bow", CubeListBuilder.create()
                .texOffs(200, 100).addBox(-1.5f, -1.5f, -1f, 3, 3, 2, new CubeDeformation(0.6f))
                .texOffs(200, 110).addBox(-7.5f, -2f, -0.5f, 5, 4, 1, new CubeDeformation(0.8f, 0.8f, 0.3f))
                .texOffs(200, 110).addBox(2.5f, -2f, -0.5f, 5, 4, 1, new CubeDeformation(0.8f, 0.8f, 0.3f)),
                PartPose.offsetAndRotation(0f, 9.5f, -4f, 0.35f, 0f, 0f));
        // глаза и веки (для моргания и «спрятать глаза»)
        head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(140, 120).addBox(-0.5f, -1.5f, -1.5f, 1, 3, 3), PartPose.offset(-9.1f, -1.5f, -12.5f));
        head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(140, 120).addBox(-0.5f, -1.5f, -1.5f, 1, 3, 3), PartPose.offset(9.1f, -1.5f, -12.5f));
        head.addOrReplaceChild("right_lid", CubeListBuilder.create().texOffs(150, 120).addBox(-0.5f, -1.5f, -1.5f, 1, 3, 3, new CubeDeformation(0.3f)), PartPose.offset(-9.1f, -1.5f, -12.5f));
        head.addOrReplaceChild("left_lid", CubeListBuilder.create().texOffs(150, 120).addBox(-0.5f, -1.5f, -1.5f, 1, 3, 3, new CubeDeformation(0.3f)), PartPose.offset(9.1f, -1.5f, -12.5f));
        // хвост
        PartDefinition t1 = body.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(80, 64).addBox(-7f, -6f, 0f, 14, 12, 16), PartPose.offset(0f, -9f, 22f));
        PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(144, 64).addBox(-4f, -4f, 0f, 8, 8, 14), PartPose.offset(0f, 0f, 16f));
        t2.addOrReplaceChild("flukes", CubeListBuilder.create().texOffs(0, 104).addBox(-15f, -1f, 0f, 30, 2, 10), PartPose.offset(0f, 0f, 14f));
        // плавники с белыми манжетами
        body.addOrReplaceChild("right_fin", CubeListBuilder.create()
                .texOffs(84, 100).addBox(-16f, -1f, -4f, 16, 2, 8)
                .texOffs(160, 110).addBox(-17f, -1.5f, -4.5f, 2, 3, 9), PartPose.offset(-10f, -4f, -12f));
        body.addOrReplaceChild("left_fin", CubeListBuilder.create().mirror()
                .texOffs(84, 100).addBox(0f, -1f, -4f, 16, 2, 8)
                .texOffs(160, 110).addBox(15f, -1.5f, -4.5f, 2, 3, 9), PartPose.offset(10f, -4f, -12f));
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void setupAnim(MaidWhale e, float limbSwing, float limbAmount, float age, float netHeadYaw, float headPitch) {
        alpha = e.fade(age - e.tickCount);
        float t = age;
        // хвост бьёт вверх-вниз, как у настоящего кита
        tail1.xRot = Mth.sin(t * 0.08f) * 0.12f;
        tail2.xRot = Mth.sin(t * 0.08f - 0.7f) * 0.18f;
        flukes.xRot = Mth.sin(t * 0.08f - 1.4f) * 0.25f;
        boolean fleeing = e.getState() == MaidWhale.FLEE;
        if (fleeing) { tail1.xRot *= 3; tail2.xRot *= 3; flukes.xRot *= 3; }
        // плавники медленно гребут
        float fin = 0.15f + Mth.sin(t * 0.06f) * 0.12f;
        rightFin.xRot = rightFin.yRot = 0;
        rightFin.zRot = -fin;
        leftFin.zRot = fin;
        if (e.isWaving()) rightFin.zRot = 0.5f + Mth.sin(t * 0.5f) * 0.6f;   // машет плавником
        if (e.coversFace()) {                                                // закрывает «лицо» плавником
            rightFin.yRot = 1.25f;
            rightFin.zRot = 0.35f;
            rightFin.xRot = -0.25f;
        }
        // голова и глаз за игроком
        head.yRot = Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(headPitch, -10f, 45f) * Mth.DEG_TO_RAD * 0.6f;
        // глаза расширяются от смущения
        float eye = e.eyesWide() ? 1.7f : 1f;
        rightEye.yScale = rightEye.zScale = leftEye.yScale = leftEye.zScale = eye;
        // моргает изредка; прячет глаза, когда закрывается плавником
        boolean blink = (e.tickCount + e.getId() * 37) % 110 < 4;
        rightLid.visible = leftLid.visible = (blink && !e.eyesWide()) || e.coversFace();
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
        body.render(pose, buf, light, overlay, r, g, b, a * alpha);
    }
}
