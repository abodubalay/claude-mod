package com.babushki.item;

import com.babushki.logic.Gifts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class KnittedArmorItem extends ArmorItem {
    public KnittedArmorItem(ArmorMaterial material, Type type, Properties props) {
        super(material, type, props);
    }

    /** Носки, как кожаные ботинки, позволяют ходить по рыхлому снегу. */
    @Override
    public boolean canWalkOnPowderedSnow(ItemStack stack, LivingEntity wearer) {
        return getType() == Type.BOOTS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(getType() == Type.BOOTS ? "Шерстяные, колючие, зато тёплые" : "Чтобы горло не простудить")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Gifts.appendGiftTooltip(stack, tip);
    }
}
