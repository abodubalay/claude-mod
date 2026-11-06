package com.babushki.client;

import com.babushki.ModRegistry;
import com.babushki.item.AvoskaItem;
import com.babushki.item.JamItem;
import com.babushki.item.KnittedArmorItem;
import com.babushki.logic.Gifts;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onRenderers);
        modBus.addListener(ClientSetup::onLayers);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onTooltip);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.BABUSHKA.get(), BabushkaRenderer::new);
        e.registerEntityRenderer(ModRegistry.SEAT.get(), NoopRenderer::new);
    }

    private static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(BabushkaModel.LAYER, BabushkaModel::createLayer);
    }

    /** Подпись «Подарок от...» для пирожков, яблок и прочего из авоськи. */
    private static void onTooltip(ItemTooltipEvent e) {
        var item = e.getItemStack().getItem();
        if (item instanceof KnittedArmorItem || item instanceof JamItem || item instanceof AvoskaItem) return;
        Gifts.appendGiftTooltip(e.getItemStack(), e.getToolTip());
    }
}
