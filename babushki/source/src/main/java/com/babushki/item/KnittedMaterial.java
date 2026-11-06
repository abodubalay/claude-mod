package com.babushki.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Вязаная «броня»: носки и шарф. Небольшая защита. */
public enum KnittedMaterial implements ArmorMaterial {
    KNITTED;

    @Override public int getDurabilityForType(ArmorItem.Type type) { return type == ArmorItem.Type.HELMET ? 110 : 130; }
    @Override public int getDefenseForType(ArmorItem.Type type) { return 1; }
    @Override public int getEnchantmentValue() { return 15; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.WHITE_WOOL); }
    @Override public String getName() { return "babushki:knitted"; }
    @Override public float getToughness() { return 0f; }
    @Override public float getKnockbackResistance() { return 0f; }
}
