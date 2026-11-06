package com.babushki.item;

import com.babushki.ModRegistry;
import com.babushki.logic.Gifts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Авоська с едой: ПКМ — достать 3 пирожка, яблоко, морковь и хлеб. */
public class AvoskaItem extends Item {
    public AvoskaItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            List<ItemStack> food = List.of(
                    new ItemStack(ModRegistry.PIROZHOK.get(), 3),
                    new ItemStack(Items.APPLE),
                    new ItemStack(Items.CARROT),
                    new ItemStack(Items.BREAD));
            for (ItemStack f : food) {
                ItemStack copy = f.copy();
                Gifts.copyGiftTag(stack, copy);
                if (!player.getInventory().add(copy)) player.drop(copy, false);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1f, 1f);
            player.displayClientMessage(Component.literal("В авоське: 3 пирожка, яблоко, морковка и хлеб!"), true);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("ПКМ — разобрать авоську").withStyle(ChatFormatting.GRAY));
        Gifts.appendGiftTooltip(stack, tip);
    }
}
