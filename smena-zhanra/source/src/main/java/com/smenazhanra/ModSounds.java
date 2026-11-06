package com.smenazhanra;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Собственные звуки мода (синтезированы скриптом tools/make_sounds.py). */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SmenaZhanra.MODID);

    public static final RegistryObject<SoundEvent> CHANNEL_SWITCH = reg("channel_switch");
    public static final RegistryObject<SoundEvent> APPLAUSE = reg("applause");
    public static final RegistryObject<SoundEvent> GAVEL = reg("gavel");
    public static final RegistryObject<SoundEvent> BUZZER = reg("buzzer");
    public static final RegistryObject<SoundEvent> CORRECT = reg("correct");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SmenaZhanra.MODID, name)));
    }
}
