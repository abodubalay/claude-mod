package com.claudecompanion.client;

import com.claudecompanion.ClaudeCompanion;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;

/** Скин, присланный сервером. Если его нет — используется встроенный. */
public final class ClientSkin {
    private ClientSkin() {}

    public static final ResourceLocation DEFAULT = new ResourceLocation(ClaudeCompanion.MODID, "textures/entity/claude.png");
    private static final ResourceLocation CUSTOM = new ResourceLocation(ClaudeCompanion.MODID, "dynamic/custom_skin");
    private static volatile ResourceLocation current = DEFAULT;

    public static ResourceLocation current() { return current; }

    public static void apply(byte[] png) {
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            if (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
                ClaudeCompanion.LOG.warn("Скин должен быть 64x64, а он {}x{}", img.getWidth(), img.getHeight());
                img.close();
                return;
            }
            Minecraft.getInstance().getTextureManager().register(CUSTOM, new DynamicTexture(img));
            current = CUSTOM;
        } catch (Exception e) {
            ClaudeCompanion.LOG.warn("Не удалось загрузить скин напарника", e);
        }
    }
}
