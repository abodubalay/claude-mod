package com.claudecompanion.client;

import com.claudecompanion.ClaudeCompanion;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Скины напарников: присланные сервером, иначе встроенные (у каждой нейросети свой цвет). */
public final class ClientSkin {
    private ClientSkin() {}

    private static final Set<String> BUILT_IN = Set.of("claude", "deepseek", "grok", "chatgpt", "gemini");
    private static final Map<String, ResourceLocation> CUSTOM = new ConcurrentHashMap<>();

    public static ResourceLocation forProfile(String profile) {
        ResourceLocation custom = CUSTOM.get(profile);
        if (custom != null) return custom;
        String id = BUILT_IN.contains(profile) ? profile : "claude";
        return new ResourceLocation(ClaudeCompanion.MODID, "textures/entity/" + id + ".png");
    }

    public static void apply(String profile, byte[] png) {
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            if (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
                ClaudeCompanion.LOG.warn("Скин {} должен быть 64x64, а он {}x{}", profile, img.getWidth(), img.getHeight());
                img.close();
                return;
            }
            String safe = profile.replaceAll("[^a-z0-9_]", "_");
            ResourceLocation rl = new ResourceLocation(ClaudeCompanion.MODID, "dynamic/skin_" + safe);
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            CUSTOM.put(profile, rl);
        } catch (Exception e) {
            ClaudeCompanion.LOG.warn("Не удалось загрузить скин {}", profile, e);
        }
    }
}
