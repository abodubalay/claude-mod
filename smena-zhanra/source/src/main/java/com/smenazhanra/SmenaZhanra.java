package com.smenazhanra;

import com.smenazhanra.network.Net;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Мод «Смена жанра». Раз в N минут Minecraft превращается в другую игру.
 */
@Mod(SmenaZhanra.MODID)
public class SmenaZhanra {
    public static final String MODID = "smenazhanra";

    public SmenaZhanra() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        Net.init();
    }
}
