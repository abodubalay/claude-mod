package com.smenazhanra.server;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;

/** Последствия мини-игр в мире: питомец, бегущие свиньи, розовая броня, кудахтанье, «ПРЕСТУПНИК». */
public class Punishments {
    public static final String PET_NAME = "♥ Свинка-тян ♥";
    public static final String PET_OWNER = "smenazhanra_owner";
    public static final String PINK_TAG = "smenazhanra_pink";
    public static final long DAY = 24000L;

    private static long now(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    // ------------------------------------------------------------------ запуск наказаний

    public static void startPigFlee(ServerPlayer p) {
        GenreManager.data(p).putLong("pigFlee", now(p) + 7 * DAY);
    }

    public static void startChickenShame(ServerPlayer p) {
        GenreManager.data(p).putLong("chickenShame", now(p) + DAY);
    }

    public static void startCriminal(ServerPlayer p) {
        GenreManager.data(p).putLong("criminal", now(p) + DAY);
        GenreManager.broadcastStatus(p);
    }

    public static boolean isCriminal(ServerPlayer p) {
        CompoundTag d = GenreManager.data(p);
        return d.contains("criminal") && d.getLong("criminal") > now(p);
    }

    public static void startPinkArmor(ServerPlayer p) {
        CompoundTag d = GenreManager.data(p);
        if (!d.contains("savedArmor")) {
            // сохраняем настоящую броню, чтобы потом вернуть — ничего не теряется
            ListTag list = new ListTag();
            for (EquipmentSlot slot : ARMOR) {
                ItemStack cur = p.getItemBySlot(slot);
                CompoundTag t = new CompoundTag();
                t.putString("slot", slot.getName());
                if (!cur.isEmpty() && !isPink(cur)) cur.save(t);
                list.add(t);
                p.setItemSlot(slot, ItemStack.EMPTY);
            }
            d.put("savedArmor", list);
        }
        d.putLong("pinkUntil", now(p) + DAY);
        ensurePink(p);
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static boolean isPink(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean(PINK_TAG);
    }

    private static ItemStack pinkPiece(EquipmentSlot slot) {
        ItemStack s = new ItemStack(switch (slot) {
            case HEAD -> Items.LEATHER_HELMET;
            case CHEST -> Items.LEATHER_CHESTPLATE;
            case LEGS -> Items.LEATHER_LEGGINGS;
            default -> Items.LEATHER_BOOTS;
        });
        ((DyeableLeatherItem) s.getItem()).setColor(s, 0xFF69B4);
        s.enchant(Enchantments.BINDING_CURSE, 1);
        s.enchant(Enchantments.VANISHING_CURSE, 1);
        s.setHoverName(Component.literal("Розовая броня позора").withStyle(ChatFormatting.LIGHT_PURPLE));
        s.getOrCreateTag().putBoolean(PINK_TAG, true);
        return s;
    }

    /** Надевает розовую броню, если её нет (например, после смерти). Чужая броня уходит в инвентарь. */
    private static void ensurePink(ServerPlayer p) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack cur = p.getItemBySlot(slot);
            if (isPink(cur)) continue;
            if (!cur.isEmpty()) ItemHandlerHelper.giveItemToPlayer(p, cur.copy());
            p.setItemSlot(slot, pinkPiece(slot));
        }
    }

    private static void restoreArmor(ServerPlayer p) {
        CompoundTag d = GenreManager.data(p);
        for (EquipmentSlot slot : ARMOR) if (isPink(p.getItemBySlot(slot))) p.setItemSlot(slot, ItemStack.EMPTY);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isPink(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        ListTag list = d.getList("savedArmor", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            EquipmentSlot slot = EquipmentSlot.byName(t.getString("slot"));
            ItemStack s = ItemStack.of(t);
            if (s.isEmpty()) continue;
            if (p.getItemBySlot(slot).isEmpty()) p.setItemSlot(slot, s);
            else ItemHandlerHelper.giveItemToPlayer(p, s);
        }
        d.remove("savedArmor");
        d.remove("pinkUntil");
        Rewards.say(p, "Срок наказания истёк: розовая броня снята, твоя броня вернулась.", ChatFormatting.GREEN);
    }

    // ------------------------------------------------------------------ питомец

    public static void spawnPetPig(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        Pig pig = EntityType.PIG.create(lvl);
        if (pig == null) return;
        pig.moveTo(p.getX() + 1.5, p.getY(), p.getZ() + 1.5, p.getYRot(), 0);
        pig.setCustomName(Component.literal(PET_NAME).withStyle(ChatFormatting.LIGHT_PURPLE));
        pig.setCustomNameVisible(true);
        pig.setInvulnerable(true);
        pig.setPersistenceRequired();
        pig.getPersistentData().putUUID(PET_OWNER, p.getUUID());
        lvl.addFreshEntity(pig);
        lvl.playSound(null, pig.blockPosition(), SoundEvents.PIG_AMBIENT, SoundSource.NEUTRAL, 1f, 1.5f);
    }

    public static boolean isPet(Pig pig) {
        return pig.getPersistentData().hasUUID(PET_OWNER);
    }

    /** Свинка-тян ходит за хозяином. Вызывается каждый тик свиньи. */
    public static void tickPet(Pig pig) {
        if (pig.tickCount % 10 != 0 || pig.level().isClientSide) return;
        if (pig.getTags().contains(GenreManager.FROZEN_TAG)) return;
        ServerLevel lvl = (ServerLevel) pig.level();
        ServerPlayer owner = lvl.getServer().getPlayerList().getPlayer(pig.getPersistentData().getUUID(PET_OWNER));
        if (pig.getY() < lvl.getMinBuildHeight() + 1) {
            if (owner != null && owner.level() == lvl) pig.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            else pig.teleportTo(pig.getX(), lvl.getMinBuildHeight() + 100, pig.getZ());
        }
        if (owner == null || owner.level() != lvl || owner.isSpectator()) return;
        double d2 = pig.distanceToSqr(owner);
        if (d2 > 24 * 24) {
            pig.teleportTo(owner.getX() + 1, owner.getY(), owner.getZ() + 1);
            pig.getNavigation().stop();
        } else if (d2 > 9) {
            pig.getNavigation().moveTo(owner, 1.3);
        }
        if (pig.getRandom().nextInt(40) == 0) lvl.playSound(null, pig.blockPosition(), SoundEvents.PIG_AMBIENT, SoundSource.NEUTRAL, 0.7f, 1.6f);
    }

    // ------------------------------------------------------------------ каждый тик игрока

    public static void tick(ServerPlayer p) {
        if (p.tickCount % 10 != 0) return;
        CompoundTag d = GenreManager.data(p);
        long now = now(p);
        ServerLevel lvl = p.serverLevel();

        if (d.contains("pigFlee")) {
            if (d.getLong("pigFlee") <= now) {
                d.remove("pigFlee");
                Rewards.say(p, "Прошла неделя. Свиньи тебя простили.", ChatFormatting.GREEN);
            } else {
                for (Pig pig : lvl.getEntitiesOfClass(Pig.class, p.getBoundingBox().inflate(16))) {
                    if (isPet(pig) || pig.isNoAi()) continue;
                    Vec3 away = DefaultRandomPos.getPosAway(pig, 16, 7, p.position());
                    if (away != null) pig.getNavigation().moveTo(away.x, away.y, away.z, 1.8);
                }
            }
        }

        if (d.contains("chickenShame")) {
            if (d.getLong("chickenShame") <= now) {
                d.remove("chickenShame");
                Rewards.say(p, "Курицы перестали тебя осуждать. Наверное.", ChatFormatting.GREEN);
            } else if (p.tickCount % 20 == 0) {
                for (Chicken c : lvl.getEntitiesOfClass(Chicken.class, p.getBoundingBox().inflate(24))) {
                    if (c.isNoAi()) continue;
                    if (c.distanceToSqr(p) > 6) c.getNavigation().moveTo(p, 1.2);
                    c.getLookControl().setLookAt(p);
                    if (c.getRandom().nextInt(4) == 0)
                        lvl.playSound(null, c.blockPosition(), SoundEvents.CHICKEN_AMBIENT, SoundSource.NEUTRAL, 1f, 0.7f);
                }
            }
        }

        if (d.contains("criminal") && d.getLong("criminal") <= now) {
            d.remove("criminal");
            GenreManager.broadcastStatus(p);
            Rewards.say(p, "Срок истёк. Ты больше не ПРЕСТУПНИК.", ChatFormatting.GREEN);
        }

        if (d.contains("pinkUntil")) {
            if (d.getLong("pinkUntil") <= now) restoreArmor(p);
            else if (p.tickCount % 40 == 0 && p.isAlive()) ensurePink(p);
        }
    }
}
