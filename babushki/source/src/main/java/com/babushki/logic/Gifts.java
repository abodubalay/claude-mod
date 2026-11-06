package com.babushki.logic;

import com.babushki.entity.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/** Метки на подарках бабушек (хранятся в NBT предмета). */
public final class Gifts {
    private Gifts() {}
    public static final String TAG = "BabushkiGift";
    public static final String WORN = "BabushkiWorn";

    public static void mark(ItemStack stack, Role giver) {
        stack.getOrCreateTag().putString(TAG, giver.name());
    }

    public static boolean isGift(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG);
    }

    @Nullable
    public static Role giver(ItemStack stack) {
        if (!isGift(stack)) return null;
        try { return Role.valueOf(stack.getTag().getString(TAG)); } catch (Exception e) { return null; }
    }

    public static void unmark(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(TAG);
        tag.remove(WORN);
        if (tag.isEmpty()) stack.setTag(null);
    }

    public static void copyGiftTag(ItemStack from, ItemStack to) {
        Role r = giver(from);
        if (r != null) mark(to, r);
    }

    public static boolean wornRewarded(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(WORN);
    }

    public static void setWornRewarded(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(WORN, true);
    }

    public static void appendGiftTooltip(ItemStack stack, List<Component> tip) {
        Role r = giver(stack);
        if (r != null) {
            tip.add(Component.literal("Подарок от: " + r.displayName).withStyle(s -> s.withColor(r.textColor())));
            tip.add(Component.literal("Не выбрасывай — обидится!").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
