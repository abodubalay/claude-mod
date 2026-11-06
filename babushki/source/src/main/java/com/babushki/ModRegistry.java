package com.babushki;

import com.babushki.block.BenchBlock;
import com.babushki.entity.BabushkaEntity;
import com.babushki.entity.SeatEntity;
import com.babushki.item.AvoskaItem;
import com.babushki.item.JamItem;
import com.babushki.item.KnittedArmorItem;
import com.babushki.item.KnittedMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BabushkiMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BabushkiMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BabushkiMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BabushkiMod.MODID);

    public static final RegistryObject<Block> BENCH = BLOCKS.register("bench", () -> new BenchBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));

    public static final RegistryObject<Item> BENCH_ITEM = ITEMS.register("bench", () -> new BlockItem(BENCH.get(), new Item.Properties()));

    public static final RegistryObject<Item> PIROZHOK = ITEMS.register("pirozhok", () -> new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(6).saturationMod(0.6f).build())));

    public static final RegistryObject<Item> JAM = ITEMS.register("jam", () -> new JamItem(new Item.Properties()
            .stacksTo(16).rarity(Rarity.UNCOMMON)
            .food(new FoodProperties.Builder().nutrition(8).saturationMod(0.8f).alwaysEat()
                    .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 200, 0), 1.0f).build())));

    public static final RegistryObject<Item> SOCKS = ITEMS.register("socks", () -> new KnittedArmorItem(
            KnittedMaterial.KNITTED, ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final RegistryObject<Item> SCARF = ITEMS.register("scarf", () -> new KnittedArmorItem(
            KnittedMaterial.KNITTED, ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<Item> AVOSKA = ITEMS.register("avoska", () -> new AvoskaItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<EntityType<BabushkaEntity>> BABUSHKA = ENTITIES.register("babushka",
            () -> EntityType.Builder.<BabushkaEntity>of(BabushkaEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.7f).clientTrackingRange(10).build("babushka"));

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.01f, 0.01f).clientTrackingRange(10).noSummon().build("seat"));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.babushki"))
            .icon(() -> new ItemStack(PIROZHOK.get()))
            .displayItems((params, out) -> {
                out.accept(BENCH_ITEM.get());
                out.accept(PIROZHOK.get());
                out.accept(JAM.get());
                out.accept(SOCKS.get());
                out.accept(SCARF.get());
                out.accept(AVOSKA.get());
            }).build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
    }
}
