package com.babushki;

import com.babushki.client.ClientSetup;
import com.babushki.entity.BabushkaEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BabushkiMod.MODID)
public class BabushkiMod {
    public static final String MODID = "babushki";

    public BabushkiMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener(this::onAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BabushkiConfig.SPEC, "babushki-common.toml");
        MinecraftForge.EVENT_BUS.register(new ForgeEvents());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistry.BABUSHKA.get(), BabushkaEntity.createAttributes().build());
    }
}
