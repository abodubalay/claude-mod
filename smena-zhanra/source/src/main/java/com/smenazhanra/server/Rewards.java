package com.smenazhanra.server;

import com.smenazhanra.Genre;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemHandlerHelper;

/** Что происходит в мире после мини-игры. Сервер ограничивает награды, чтобы нельзя было «накрутить». */
public class Rewards {

    public static void apply(ServerPlayer p, Genre g, int outcome, int[] v) {
        switch (g) {
            case NOVELLA -> novella(p, outcome);
            case SUD -> sud(p, outcome, v);
            case RITM -> ritm(p, outcome, v);
            case VIKTORINA -> viktorina(p, v);
            case FIGHTING -> fighting(p, outcome, v);
        }
    }

    private static int val(int[] v, int i) {
        return v != null && i < v.length ? v[i] : 0;
    }

    static void give(ServerPlayer p, Item item, int count) {
        while (count > 0) {
            int n = Math.min(count, item.getMaxStackSize());
            ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(item, n));
            count -= n;
        }
    }

    static void say(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal("[Смена жанра] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(text).withStyle(color)));
    }

    private static void novella(ServerPlayer p, int outcome) {
        switch (outcome) {
            case 0 -> {
                Punishments.spawnPetPig(p);
                say(p, "«Я буду ждать тебя... в деревне» ♥ Рядом появилась Свинка-тян с бантиком. Она бессмертна и всегда рядом!", ChatFormatting.LIGHT_PURPLE);
            }
            case 1 -> say(p, "«Давай останемся друзьями». Ничего не произошло... совсем ничего.", ChatFormatting.GRAY);
            default -> {
                Punishments.startPigFlee(p);
                say(p, "Свинка-тян плачет. Все свиньи мира узнали об этом и неделю будут от тебя убегать!", ChatFormatting.RED);
            }
        }
    }

    private static void sud(ServerPlayer p, int outcome, int[] v) {
        if (outcome == 0) {
            int em = Mth.clamp(val(v, 0), 2, 8);
            give(p, Items.EMERALD, em);
            say(p, "Невиновен! Компенсация за моральный ущерб: " + em + " изумрудов.", ChatFormatting.GREEN);
        } else {
            int kind = Mth.clamp(val(v, 0), 0, 2);
            switch (kind) {
                case 0 -> {
                    Punishments.startPinkArmor(p);
                    say(p, "Виновен! Наказание: один игровой день носить РОЗОВУЮ БРОНЮ ПОЗОРА. Твоя броня вернётся потом.", ChatFormatting.RED);
                }
                case 1 -> {
                    Punishments.startChickenShame(p);
                    say(p, "Виновен! Наказание: один игровой день все курицы ходят за тобой и осуждающе кудахчут.", ChatFormatting.RED);
                }
                default -> {
                    Punishments.startCriminal(p);
                    say(p, "Виновен! Наказание: один игровой день над твоей головой надпись «ПРЕСТУПНИК».", ChatFormatting.RED);
                }
            }
        }
    }

    /** v = [алмазы, золото, железо, уголь] пойманные блоки. outcome 0 = высокий счёт. */
    private static void ritm(ServerPlayer p, int outcome, int[] v) {
        if (outcome == 0) {
            int dia = Mth.clamp(val(v, 0), 0, 10), gold = Mth.clamp(val(v, 1), 0, 16),
                    iron = Mth.clamp(val(v, 2), 0, 24), coal = Mth.clamp(val(v, 3), 0, 32);
            give(p, Items.DIAMOND, dia);
            give(p, Items.RAW_GOLD, gold);
            give(p, Items.RAW_IRON, iron);
            give(p, Items.COAL, coal);
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
            say(p, "Ты копаешь в такт! Добыча: " + dia + " алмазов, " + gold + " золота, " + iron + " железа, " + coal + " угля.", ChatFormatting.AQUA);
        } else {
            give(p, Items.COBBLESTONE, 16);
            say(p, "Ритма нет — копай руками. (Держи 16 булыжника.)", ChatFormatting.GRAY);
        }
    }

    private static void viktorina(ServerPlayer p, int[] v) {
        int em = val(v, 0);
        if (em != 1 && em != 2 && em != 5 && em != 10 && em != 25) em = 0;
        if (em > 0) {
            give(p, Items.EMERALD, em);
            say(p, "Ты выиграл " + em + " изумрудов! Зрители-мобы аплодируют.", ChatFormatting.GREEN);
        } else {
            say(p, "Выигрыш сгорел. Зато ты был на телевидении!", ChatFormatting.GRAY);
        }
    }

    /** v[0]: 0 крипер, 1 зомби, 2 курица. */
    private static void fighting(ServerPlayer p, int outcome, int[] v) {
        if (outcome != 0) {
            say(p, "Поражение... Противник станцевал победный танец.", ChatFormatting.RED);
            return;
        }
        switch (Mth.clamp(val(v, 0), 0, 2)) {
            case 0 -> { give(p, Items.GUNPOWDER, 4); give(p, Items.EMERALD, 3); }
            case 1 -> { give(p, Items.IRON_INGOT, 3); give(p, Items.EMERALD, 3); }
            default -> { give(p, Items.COOKED_CHICKEN, 8); give(p, Items.FEATHER, 6); give(p, Items.EMERALD, 3); }
        }
        say(p, "БЕЗУПРЕЧНАЯ ПОБЕДА! Награда уже в инвентаре.", ChatFormatting.GOLD);
    }
}
