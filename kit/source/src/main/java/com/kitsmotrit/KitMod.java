package com.kitsmotrit;

import com.kitsmotrit.entity.MaidWhale;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/** «Кит смотрит»: огромный летающий кит-цундере в костюме горничной. */
@Mod(KitMod.MODID)
public class KitMod {
    public static final String MODID = "kitsmotrit";
    public static final Logger LOG = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<EntityType<MaidWhale>> MAID_WHALE = ENTITIES.register("maid_whale",
            () -> EntityType.Builder.<MaidWhale>of(MaidWhale::new, MobCategory.MISC)
                    .sized(5.0f, 4.0f).clientTrackingRange(16).updateInterval(2).fireImmune().build("maid_whale"));

    public static final RegistryObject<SoundEvent> WHALE_SONG = SOUNDS.register("whale_song",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "whale_song")));

    public KitMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        bus.addListener(this::onAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KitConfig.SPEC);
        Network.register();
        MinecraftForge.EVENT_BUS.register(new GameEvents());
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(MAID_WHALE.get(), MaidWhale.createAttributes().build());
    }
}
