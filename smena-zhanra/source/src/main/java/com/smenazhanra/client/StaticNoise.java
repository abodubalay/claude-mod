package com.smenazhanra.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.smenazhanra.SmenaZhanra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/** Помехи как у старого телевизора: случайный «снег» + полосы. */
public class StaticNoise {
    private static final int W = 160, H = 90;
    private static final ResourceLocation ID = new ResourceLocation(SmenaZhanra.MODID, "dynamic/static_noise");
    private static DynamicTexture tex;
    private static long lastUpdate;
    private static final Random R = new Random();

    private static void ensure() {
        if (tex == null) {
            tex = new DynamicTexture(W, H, false);
            Minecraft.getInstance().getTextureManager().register(ID, tex);
        }
        long now = System.currentTimeMillis();
        if (now - lastUpdate > 40) {
            lastUpdate = now;
            NativeImage img = tex.getPixels();
            if (img == null) return;
            int roll = (int) ((now / 12) % H);
            for (int y = 0; y < H; y++) {
                boolean band = Math.abs(y - roll) < 6;
                for (int x = 0; x < W; x++) {
                    int v = R.nextInt(256);
                    if (band) v = Math.min(255, v + 60);
                    img.setPixelRGBA(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
                }
            }
            tex.upload();
        }
    }

    /** Рисует помехи на весь экран с заданной непрозрачностью 0..1. */
    public static void draw(GuiGraphics g, int width, int height, float alpha) {
        if (alpha <= 0.01f) return;
        ensure();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, Math.min(1f, alpha));
        g.blit(ID, 0, 0, width, height, 0, 0, W, H, W, H);
        g.setColor(1f, 1f, 1f, 1f);
        // строки развёртки
        int a = (int) (Math.min(1f, alpha) * 70) << 24;
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, a);
        RenderSystem.disableBlend();
    }
}
