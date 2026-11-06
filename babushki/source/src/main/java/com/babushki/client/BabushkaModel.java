package com.babushki.client;

import com.babushki.BabushkiMod;
import com.babushki.entity.Anim;
import com.babushki.entity.BabushkaEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Модель бабушки: голова в платке (с узелком), кофта, широкая юбка, тонкие ножки в чулках,
 * в левой руке клюка, на правой руке сумка.
 * Раскладка текстуры (64x64) повторяется в tools/gen_textures.py.
 */
public class BabushkaModel extends HierarchicalModel<BabushkaEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(BabushkiMod.MODID, "babushka"), "main");

    private final ModelPart root, head, body, skirt, rightArm, leftArm, rightThigh, leftThigh, rightShin, leftShin;

    public BabushkaModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.skirt = root.getChild("skirt");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightThigh = root.getChild("right_thigh");
        this.leftThigh = root.getChild("left_thigh");
        this.rightShin = rightThigh.getChild("shin");
        this.leftShin = leftThigh.getChild("shin");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();

        PartDefinition head = r.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, 2, 0));
        head.addOrReplaceChild("scarf",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.5f)),
                PartPose.ZERO);
        head.addOrReplaceChild("knot",
                CubeListBuilder.create().texOffs(48, 40).addBox(-1, 0, -4.6f, 2, 2, 1),
                PartPose.ZERO);

        r.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 16).addBox(-4.5f, 0, -3, 9, 11, 6),
                PartPose.offset(0, 2, 0));
        r.addOrReplaceChild("skirt",
                CubeListBuilder.create().texOffs(0, 34).addBox(-5, 0, -3.5f, 10, 10, 7),
                PartPose.offset(0, 11, 0));

        PartDefinition rArm = r.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(32, 16).addBox(-1.5f, -1, -1.5f, 3, 10, 3),
                PartPose.offset(-6, 4, 0));
        rArm.addOrReplaceChild("bag",
                CubeListBuilder.create().texOffs(36, 36).addBox(-3.5f, 2, -2, 2, 4, 4),
                PartPose.ZERO);

        PartDefinition lArm = r.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(44, 16).addBox(-1.5f, -1, -1.5f, 3, 10, 3),
                PartPose.offset(6, 4, 0));
        PartDefinition cane = lArm.addOrReplaceChild("cane",
                CubeListBuilder.create().texOffs(56, 16).addBox(-0.5f, -2, -0.5f, 1, 13, 1),
                PartPose.offset(0, 8, -0.5f));
        cane.addOrReplaceChild("handle",
                CubeListBuilder.create().texOffs(48, 44).addBox(-0.5f, -3, -3, 1, 1, 3),
                PartPose.ZERO);

        for (String side : new String[]{"right", "left"}) {
            float x = side.equals("right") ? -2 : 2;
            int u = side.equals("right") ? 0 : 24;
            PartDefinition thigh = r.addOrReplaceChild(side + "_thigh",
                    CubeListBuilder.create().texOffs(u, 52).addBox(-1.5f, 0, -1.5f, 3, 5, 3),
                    PartPose.offset(x, 13, 0));
            thigh.addOrReplaceChild("shin",
                    CubeListBuilder.create().texOffs(u + 12, 52).addBox(-1.5f, 0, -1.5f, 3, 6, 3),
                    PartPose.offset(0, 5, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void setupAnim(BabushkaEntity e, float limbSwing, float limbAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD + 0.12f; // чуть сутулится

        float breathe = Mth.sin(age * 0.08f) * 0.04f;

        if (e.isSitting()) {
            rightThigh.xRot = leftThigh.xRot = -Mth.HALF_PI;
            rightShin.xRot = leftShin.xRot = Mth.HALF_PI;
            rightThigh.yRot = 0.08f;
            leftThigh.yRot = -0.08f;
            skirt.xRot = -1.15f;
            skirt.y = 12.5f;
            rightArm.xRot = -0.6f + breathe;
            leftArm.xRot = -0.6f - breathe;
            rightArm.zRot = 0.15f;
            leftArm.zRot = -0.15f;
        } else {
            float swing = Mth.cos(limbSwing * 0.6662f) * 0.9f * limbAmount;
            rightThigh.xRot = swing;
            leftThigh.xRot = -swing;
            rightArm.xRot = -swing * 0.6f + breathe;
            leftArm.xRot = swing * 0.6f - breathe;
            rightArm.zRot = 0.08f;
            leftArm.zRot = -0.08f;
            skirt.zRot = Mth.sin(limbSwing * 0.6662f) * 0.05f * limbAmount;
            body.xRot = 0.05f;
        }

        Anim anim = e.getAnim();
        switch (anim) {
            case HEAD_SHAKE -> head.yRot += Mth.sin(age * 0.9f) * 0.45f;
            case FINGER_WAG -> {
                // Грозит клюкой.
                leftArm.xRot = -2.5f;
                leftArm.zRot = -0.2f + Mth.sin(age * 0.9f) * 0.35f;
                head.yRot += Mth.sin(age * 0.45f) * 0.15f;
            }
            case WHISPER -> {
                // Прикрывает рот ладонью и наклоняется к подруге.
                rightArm.xRot = -2.0f;
                rightArm.yRot = -0.5f;
                rightArm.zRot = 0.5f;
                head.zRot = Mth.sin(age * 0.3f) * 0.08f;
            }
            case HAND_OUT -> {
                rightArm.xRot = -1.45f;
                rightArm.zRot = 0;
            }
            case WAVE -> {
                rightArm.xRot = -2.9f;
                rightArm.zRot = Mth.sin(age * 0.8f) * 0.5f;
            }
            default -> {}
        }
    }
}
