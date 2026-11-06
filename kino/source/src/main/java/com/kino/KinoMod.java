package com.kino;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/** «Кино»: редкие эпичные кат-сцены, ситком и титры в конце серии. */
@Mod(KinoMod.MODID)
public class KinoMod {
    public static final String MODID = "kino";
    public static final Logger LOG = LogUtils.getLogger();

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final RegistryObject<SoundEvent> DRAMA = sound("drama");
    public static final RegistryObject<SoundEvent> BOOM = sound("boom");
    public static final RegistryObject<SoundEvent> APPLAUSE = sound("applause");
    public static final RegistryObject<SoundEvent> CREDITS_MUSIC = sound("credits_music");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, name)));
    }

    public KinoMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUNDS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KinoConfig.SPEC);
        Network.register();
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
    }
}
