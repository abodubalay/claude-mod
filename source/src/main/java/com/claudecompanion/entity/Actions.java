package com.claudecompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/** Все игровые действия напарника. Вызываются только на серверном потоке. */
public final class Actions {
    private Actions() {}

    // ================= поиск предметов / блоков (работает и с модами) =================

    public static String id(Item item) {
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(item);
        return k == null ? "?" : k.toString();
    }

    public static String id(Block block) {
        ResourceLocation k = ForgeRegistries.BLOCKS.getKey(block);
        return k == null ? "?" : k.toString();
    }

    public static String entityName(Entity e) {
        if (e instanceof Player p) return "игрок " + p.getGameProfile().getName();
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return k == null ? e.getName().getString() : k.toString();
    }

    private static ResourceLocation rl(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse(t.contains(":") ? t : "minecraft:" + t);
    }

    /** Предмет по id; бросает исключение с подсказками, если такого нет. */
    public static Item item(String name) {
        ResourceLocation r = rl(name);
        if (r != null && ForgeRegistries.ITEMS.containsKey(r)) return ForgeRegistries.ITEMS.getValue(r);
        throw new IllegalArgumentException("Нет предмета '" + name + "'." + suggest(name, ForgeRegistries.ITEMS.getKeys()));
    }

    private static String suggest(String name, Collection<ResourceLocation> keys) {
        String q = name.toLowerCase(Locale.ROOT).replaceAll("^.*:", "");
        List<String> hits = new ArrayList<>();
        for (ResourceLocation k : keys) {
            if (k.getPath().contains(q)) hits.add(k.toString());
            if (hits.size() >= 8) break;
        }
        return hits.isEmpty() ? "" : " Похожие: " + String.join(", ", hits);
    }

    /** Блок по id ("minecraft:oak_log") или по тегу ("#minecraft:logs", "#forge:ores/iron"). */
    public static Predicate<BlockState> blockMatcher(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.startsWith("#")) {
            ResourceLocation r = rl(n.substring(1));
            if (r == null) throw new IllegalArgumentException("Плохой тег " + name);
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, r);
            return s -> s.is(tag);
        }
        ResourceLocation r = rl(n);
        if (r == null || !ForgeRegistries.BLOCKS.containsKey(r))
            throw new IllegalArgumentException("Нет блока '" + name + "'." + suggest(name, ForgeRegistries.BLOCKS.getKeys())
                    + " Можно искать по тегу, например #minecraft:logs или #forge:ores/iron");
        Block b = ForgeRegistries.BLOCKS.getValue(r);
        return s -> s.is(b);
    }

    /** Ближайшие блоки, подходящие под условие (сначала те, что граничат с воздухом). */
    public static List<BlockPos> findBlocks(CompanionEntity e, Predicate<BlockState> match, int radius, int max, Set<BlockPos> skip) {
        BlockPos c = e.blockPosition();
        ServerLevel lvl = (ServerLevel) e.level();
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int ry = Math.min(radius, 16);
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -ry; dy <= ry; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!lvl.isLoaded(m)) continue;
                    BlockState s = lvl.getBlockState(m);
                    if (s.isAir() || !match.test(s)) continue;
                    BlockPos p = m.immutable();
                    if (!skip.contains(p)) found.add(p);
                }
        found.sort(Comparator.comparingDouble(p -> {
            double d = p.distSqr(c);
            return exposed(lvl, p) ? d : d + 400; // скрытые под землёй — в конец
        }));
        return found.size() > max ? found.subList(0, max) : found;
    }

    private static boolean exposed(ServerLevel lvl, BlockPos p) {
        for (Direction d : Direction.values()) if (!lvl.getBlockState(p.relative(d)).isSolidRender(lvl, p.relative(d))) return true;
        return false;
    }

    // ================= инвентарь =================

    public static String inventory(CompanionEntity e) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < e.inventory.getContainerSize(); i++) {
            ItemStack s = e.inventory.getItem(i);
            if (!s.isEmpty()) counts.merge(id(s.getItem()), s.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) return "пусто";
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(k).append(" x").append(v).append(", "));
        return sb.substring(0, sb.length() - 2);
    }

    public static int count(Container inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    /** Забрать из инвентаря N предметов (стопками), вернуть забранное. */
    public static List<ItemStack> take(Container inv, Item item, int n) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int k = Math.min(n, s.getCount());
            out.add(s.split(k));
            n -= k;
        }
        inv.setChanged();
        return out;
    }

    public static void giveOrDrop(CompanionEntity e, ItemStack stack) {
        ItemStack rest = e.inventory.addItem(stack);
        if (!rest.isEmpty()) e.spawnAtLocation(rest);
    }

    /** Взять лучший инструмент для блока в руку. */
    public static void equipBestTool(CompanionEntity e, BlockState state) {
        float best = e.getMainHandItem().getDestroySpeed(state);
        int bestSlot = -1;
        for (int i = 0; i < e.inventory.getContainerSize(); i++) {
            ItemStack s = e.inventory.getItem(i);
            if (s.isEmpty()) continue;
            float sp = s.getDestroySpeed(state);
            boolean correct = !state.requiresCorrectToolForDrops() || s.isCorrectToolForDrops(state);
            if (correct && sp > best) { best = sp; bestSlot = i; }
        }
        if (bestSlot >= 0) swapToHand(e, bestSlot, EquipmentSlot.MAINHAND);
    }

    public static void swapToHand(CompanionEntity e, int slot, EquipmentSlot target) {
        ItemStack inSlot = e.inventory.getItem(slot);
        ItemStack held = e.getItemBySlot(target);
        e.setItemSlot(target, inSlot);
        e.inventory.setItem(slot, held);
    }

    public static String equip(CompanionEntity e, String itemName, String destination) {
        Item it = item(itemName);
        for (int i = 0; i < e.inventory.getContainerSize(); i++) {
            ItemStack s = e.inventory.getItem(i);
            if (!s.is(it)) continue;
            EquipmentSlot slot = switch (destination) {
                case "hand" -> EquipmentSlot.MAINHAND;
                case "off-hand" -> EquipmentSlot.OFFHAND;
                default -> Mob.getEquipmentSlotForItem(s);
            };
            swapToHand(e, i, slot);
            return "Экипировал " + id(it) + " (" + slot.getName() + ").";
        }
        return "Нет " + id(it) + " в инвентаре.";
    }

    public static String eatBest(CompanionEntity e) {
        int best = -1;
        int bestFood = 0;
        for (int i = 0; i < e.inventory.getContainerSize(); i++) {
            ItemStack s = e.inventory.getItem(i);
            FoodProperties f = s.getFoodProperties(e);
            if (f == null || s.is(net.minecraft.world.item.Items.ROTTEN_FLESH) || s.is(net.minecraft.world.item.Items.SPIDER_EYE)
                    || s.is(net.minecraft.world.item.Items.POISONOUS_POTATO) || s.is(net.minecraft.world.item.Items.PUFFERFISH)) continue;
            if (f.getNutrition() > bestFood) { bestFood = f.getNutrition(); best = i; }
        }
        if (best < 0) return "Нет нормальной еды.";
        ItemStack s = e.inventory.getItem(best);
        String name = id(s.getItem());
        s.shrink(1);
        e.inventory.setChanged();
        e.heal(bestFood);
        e.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 1.0f, 1.0f);
        return "Съел " + name + ". Здоровье " + Math.round(e.getHealth()) + "/" + Math.round(e.getMaxHealth()) + ".";
    }

    // ================= статус =================

    public static String status(CompanionEntity e) {
        ServerLevel lvl = (ServerLevel) e.level();
        long time = lvl.getDayTime() % 24000;
        boolean day = time < 12500 || time > 23500;
        StringBuilder sb = new StringBuilder();
        BlockPos p = e.blockPosition();
        sb.append("Позиция: ").append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ())
                .append(" (").append(lvl.dimension().location()).append(", биом ")
                .append(lvl.getBiome(p).unwrapKey().map(k -> k.location().toString()).orElse("?")).append(")\n");
        sb.append("Здоровье: ").append(Math.round(e.getHealth())).append("/" + Math.round(e.getMaxHealth()) + "\n");
        sb.append("Время: ").append(day ? "день" : "ночь").append(" (").append(time).append("), ")
                .append(lvl.isThundering() ? "гроза" : lvl.isRaining() ? "дождь" : "ясно").append('\n');
        sb.append("В руке: ").append(e.getMainHandItem().isEmpty() ? "ничего" : id(e.getMainHandItem().getItem()));
        List<String> armor = new ArrayList<>();
        for (ItemStack a : e.getArmorSlots()) if (!a.isEmpty()) armor.add(id(a.getItem()));
        sb.append(". Броня: ").append(armor.isEmpty() ? "нет" : String.join(", ", armor)).append('\n');
        sb.append("Инвентарь: ").append(inventory(e)).append('\n');
        sb.append("Режим охраны: ").append(e.guard ? "вкл" : "выкл")
                .append(e.getFollowTarget() != null ? ", иду за игроком" : "").append('\n');
        ServerPlayer o = e.getOwnerPlayer();
        if (o != null) {
            BlockPos op = o.blockPosition();
            sb.append("Хозяин ").append(o.getGameProfile().getName()).append(": ")
                    .append(op.getX()).append(' ').append(op.getY()).append(' ').append(op.getZ())
                    .append(", ").append(Math.round(e.distanceTo(o))).append(" м от тебя, здоровье ")
                    .append(Math.round(o.getHealth())).append("/" + Math.round(o.getMaxHealth()) + "\n");
        }
        List<Entity> near = lvl.getEntities(e, e.getBoundingBox().inflate(20), x -> x instanceof net.minecraft.world.entity.LivingEntity);
        near.sort(Comparator.comparingDouble(e::distanceTo));
        List<String> names = new ArrayList<>();
        for (Entity x : near.subList(0, Math.min(12, near.size())))
            names.add(entityName(x) + " (" + Math.round(e.distanceTo(x)) + "м)");
        sb.append("Рядом: ").append(names.isEmpty() ? "никого" : String.join("; ", names));
        return sb.toString();
    }

    public static String lookAround(CompanionEntity e, List<String> blocks, int radius) {
        int r = Math.max(4, Math.min(32, radius));
        StringBuilder sb = new StringBuilder();
        for (String name : blocks) {
            Predicate<BlockState> m;
            try { m = blockMatcher(name); } catch (IllegalArgumentException ex) { sb.append(name).append(": ").append(ex.getMessage()).append('\n'); continue; }
            List<BlockPos> found = findBlocks(e, m, r, 5, Set.of());
            sb.append(name).append(": ");
            if (found.isEmpty()) sb.append("не найдено в радиусе ").append(r);
            else for (BlockPos p : found)
                sb.append(id(e.level().getBlockState(p).getBlock())).append(" @ ").append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ())
                        .append(" (").append(Math.round(Math.sqrt(p.distSqr(e.blockPosition())))).append("м); ");
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /** Что за блоки вокруг (полезно, чтобы узнать id блоков из модов). */
    public static String survey(CompanionEntity e, int radius) {
        int r = Math.max(2, Math.min(10, radius));
        Map<String, Integer> counts = new HashMap<>();
        BlockPos c = e.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            BlockState s = e.level().getBlockState(p);
            if (!s.isAir()) counts.merge(id(s.getBlock()), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder("Блоки в радиусе " + r + ": ");
        for (var en : list.subList(0, Math.min(30, list.size()))) sb.append(en.getKey()).append(" x").append(en.getValue()).append(", ");
        return sb.toString();
    }

    // ================= крафт (любые рецепты, включая модовые) =================

    public static String craft(CompanionEntity e, String itemName, int count) {
        Item target = item(itemName);
        ServerLevel lvl = (ServerLevel) e.level();
        List<CraftingRecipe> recipes = new ArrayList<>();
        for (CraftingRecipe r : lvl.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))
            if (r.getResultItem(lvl.registryAccess()).is(target) && !r.getIngredients().isEmpty()) recipes.add(r);
        if (recipes.isEmpty()) return "Нет обычного рецепта крафта для " + id(target) + " (может, его делают в машине из мода).";

        boolean table = hasCraftingTable(e);
        int made = 0;
        String lastMissing = null;
        boolean needTable = false;
        int guard = 0;
        while (made < count && guard++ < 64) {
            boolean ok = false;
            for (CraftingRecipe r : recipes) {
                if (!r.canCraftInDimensions(2, 2) && !table) { needTable = true; continue; }
                List<Integer> slots = matchIngredients(e.inventory, r.getIngredients());
                if (slots == null) { lastMissing = missing(e.inventory, r.getIngredients()); continue; }
                for (int slot : slots) {
                    ItemStack s = e.inventory.getItem(slot);
                    ItemStack remainder = s.getCraftingRemainingItem();
                    s.shrink(1);
                    if (!remainder.isEmpty()) giveOrDrop(e, remainder.copy());
                }
                ItemStack out = r.getResultItem(lvl.registryAccess()).copy();
                made += out.getCount();
                giveOrDrop(e, out);
                ok = true;
                break;
            }
            if (!ok) break;
        }
        e.inventory.setChanged();
        if (made > 0) return "Скрафтил " + made + " " + id(target) + ". Всего: " + count(e.inventory, target) + ".";
        if (needTable && lastMissing == null) return "Для " + id(target) + " нужен верстак рядом (в 6 блоках) или в инвентаре.";
        return "Не хватает ресурсов для " + id(target) + ": " + (lastMissing == null ? "?" : lastMissing)
                + (needTable ? ". И ещё нужен верстак рядом." : "") + " Инвентарь: " + inventory(e);
    }

    private static boolean hasCraftingTable(CompanionEntity e) {
        for (int i = 0; i < e.inventory.getContainerSize(); i++)
            if (id(e.inventory.getItem(i).getItem()).contains("crafting_table")) return true;
        BlockPos c = e.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6)))
            if (id(e.level().getBlockState(p).getBlock()).contains("crafting_table")) return true;
        return false;
    }

    /** Подбираем слот на каждый ингредиент; null — если чего-то не хватает. */
    @Nullable
    private static List<Integer> matchIngredients(SimpleContainer inv, List<Ingredient> ingredients) {
        int[] left = new int[inv.getContainerSize()];
        for (int i = 0; i < left.length; i++) left[i] = inv.getItem(i).getCount();
        List<Integer> slots = new ArrayList<>();
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            int found = -1;
            for (int i = 0; i < left.length; i++)
                if (left[i] > 0 && ing.test(inv.getItem(i))) { found = i; break; }
            if (found < 0) return null;
            left[found]--;
            slots.add(found);
        }
        return slots;
    }

    private static String missing(SimpleContainer inv, List<Ingredient> ingredients) {
        Map<String, Integer> need = new LinkedHashMap<>();
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            ItemStack[] opts = ing.getItems();
            String n = opts.length == 0 ? "?" : id(opts[0].getItem()) + (opts.length > 1 ? " (или аналог)" : "");
            need.merge(n, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder("рецепт: ");
        need.forEach((k, v) -> sb.append(k).append(" x").append(v).append(", "));
        return sb.substring(0, sb.length() - 2);
    }

    // ================= плавка =================

    @Nullable
    public static SmeltingRecipe smeltRecipe(CompanionEntity e, Item input) {
        return e.level().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(input)), e.level()).orElse(null);
    }

    public static boolean hasFurnace(CompanionEntity e) {
        for (int i = 0; i < e.inventory.getContainerSize(); i++)
            if (id(e.inventory.getItem(i).getItem()).contains("furnace")) return true;
        BlockPos c = e.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -3, -8), c.offset(8, 3, 8)))
            if (id(e.level().getBlockState(p).getBlock()).contains("furnace")) return true;
        return false;
    }

    public static int burnTime(ItemStack s) {
        return ForgeHooks.getBurnTime(s, RecipeType.SMELTING);
    }

    // ================= установка блока =================

    public static String place(CompanionEntity e, String itemName, BlockPos pos) {
        Item it = item(itemName);
        if (!(it instanceof BlockItem bi)) return id(it) + " — это не блок.";
        int slot = -1;
        for (int i = 0; i < e.inventory.getContainerSize(); i++) if (e.inventory.getItem(i).is(it)) { slot = i; break; }
        if (slot < 0) return "Нет " + id(it) + " в инвентаре.";
        if (e.distanceToSqr(Vec3.atCenterOf(pos)) > 36) return "Слишком далеко, подойди ближе (go_to).";
        if (!e.level().getBlockState(pos).canBeReplaced()) return "Место " + pos.toShortString() + " занято блоком " + id(e.level().getBlockState(pos).getBlock()) + ".";
        ItemStack one = e.inventory.getItem(slot).copyWithCount(1);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult r = bi.place(new BlockPlaceContext(e.level(), null, InteractionHand.MAIN_HAND, one, hit));
        if (!r.consumesAction()) return "Не получилось поставить " + id(it) + " — нужна опора или место занято.";
        e.inventory.getItem(slot).shrink(1);
        e.inventory.setChanged();
        e.swing(InteractionHand.MAIN_HAND);
        return "Поставил " + id(it) + " на " + pos.toShortString() + ".";
    }

    // ================= контейнеры (сундуки и хранилища из модов) =================

    @Nullable
    public static BlockPos findStorage(CompanionEntity e, int radius) {
        BlockPos c = e.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -4, -radius), c.offset(radius, 4, radius))) {
            BlockEntity be = e.level().getBlockEntity(p);
            if (be == null || be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().isEmpty()) continue;
            String bid = id(e.level().getBlockState(p).getBlock());
            // печи и машины не считаем хранилищами
            if (!(bid.contains("chest") || bid.contains("barrel") || bid.contains("crate") || bid.contains("drawer")
                    || bid.contains("storage") || bid.contains("vault") || bid.contains("shulker"))) continue;
            double d = p.distSqr(c);
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        return best;
    }

    public static String deposit(CompanionEntity e, BlockPos pos, String itemName, int count) {
        IItemHandler h = handler(e, pos);
        if (h == null) return "Это не хранилище.";
        boolean all = itemName.equalsIgnoreCase("all");
        Item it = all ? null : item(itemName);
        int left = all ? Integer.MAX_VALUE : count;
        int moved = 0;
        for (int i = 0; i < e.inventory.getContainerSize() && left > 0; i++) {
            ItemStack s = e.inventory.getItem(i);
            if (s.isEmpty()) continue;
            if (all ? isGear(s) : !s.is(it)) continue;
            int k = Math.min(left, s.getCount());
            ItemStack rest = ItemHandlerHelper.insertItemStacked(h, s.copyWithCount(k), false);
            int put = k - rest.getCount();
            s.shrink(put);
            moved += put;
            left -= put;
            if (!rest.isEmpty()) break;
        }
        e.inventory.setChanged();
        return "Положил " + moved + " предметов. Инвентарь: " + inventory(e);
    }

    public static String withdraw(CompanionEntity e, BlockPos pos, String itemName, int count) {
        IItemHandler h = handler(e, pos);
        if (h == null) return "Это не хранилище.";
        if (itemName.equalsIgnoreCase("list")) {
            Map<String, Integer> c = new LinkedHashMap<>();
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) c.merge(id(s.getItem()), s.getCount(), Integer::sum);
            }
            return "В хранилище: " + (c.isEmpty() ? "пусто" : c.toString());
        }
        Item it = item(itemName);
        int left = count;
        for (int i = 0; i < h.getSlots() && left > 0; i++) {
            if (!h.getStackInSlot(i).is(it)) continue;
            ItemStack got = h.extractItem(i, left, false);
            left -= got.getCount();
            giveOrDrop(e, got);
        }
        return "Взял " + (count - left) + " " + id(it) + ". Инвентарь: " + inventory(e);
    }

    private static boolean isGear(ItemStack s) {
        // при "положить всё" оставляем себе инструменты, оружие, броню и еду
        return s.isDamageableItem() || s.getFoodProperties(null) != null;
    }

    @Nullable
    private static IItemHandler handler(CompanionEntity e, BlockPos pos) {
        BlockEntity be = e.level().getBlockEntity(pos);
        if (be == null) return null;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
    }

    // ================= задачи =================

    /** Дойти до точки. */
    public static class GoTo extends CompanionTask {
        private final Vec3 target;
        private final double range;
        private double bestDist = Double.MAX_VALUE;
        private int stuck = 0;
        @Nullable private final CompanionTask then;

        public GoTo(Vec3 target, double range, int maxSeconds, @Nullable CompanionTask then) {
            super(maxSeconds);
            this.target = target;
            this.range = range;
            this.then = then;
        }

        @Override
        protected boolean step(CompanionEntity e) {
            double d = e.position().distanceTo(target);
            if (d <= range) {
                e.getNavigation().stop();
                if (then != null) {
                    e.startTaskChain(then, this);
                    return true;
                }
                complete("Пришёл. " + pos(e));
                return true;
            }
            if (ticks % 20 == 1 || e.getNavigation().isDone()) e.getNavigation().moveTo(target.x, target.y, target.z, 1.15);
            if (d < bestDist - 0.5) { bestDist = d; stuck = 0; }
            else if (++stuck > 160) {
                fail("не могу дойти (застрял), осталось " + Math.round(d) + " м. " + pos(e));
                return true;
            }
            return false;
        }
    }

    public static String pos(CompanionEntity e) {
        BlockPos p = e.blockPosition();
        return "Я на " + p.getX() + " " + p.getY() + " " + p.getZ() + ".";
    }

    /** Добыть N блоков. */
    public static class Mine extends CompanionTask {
        private final Predicate<BlockState> match;
        private final String name;
        private final int want;
        private int mined = 0;
        private final Set<BlockPos> skip = new HashSet<>();
        @Nullable private BlockPos current;
        private int breakTicks = 0, breakNeeded = 0, approachTicks = 0;
        private int collectTicks = 0;

        public Mine(Predicate<BlockState> match, String name, int want) {
            super(40 + want * 12);
            this.match = match;
            this.name = name;
            this.want = want;
        }

        @Override
        protected boolean step(CompanionEntity e) {
            if (collectTicks > 0) { // подбираем дроп
                if (--collectTicks == 0 && mined >= want) { finish(e); return true; }
                return false;
            }
            if (current == null || !match.test(e.level().getBlockState(current))) {
                if (mined >= want) { finish(e); return true; }
                List<BlockPos> list = findBlocks(e, match, 24, 1, skip);
                if (list.isEmpty()) {
                    if (mined == 0) fail("не вижу " + name + " в радиусе 24 блоков");
                    else finish(e);
                    return true;
                }
                current = list.get(0);
                breakTicks = 0;
                approachTicks = 0;
            }
            BlockState state = e.level().getBlockState(current);
            Vec3 center = Vec3.atCenterOf(current);
            if (e.getEyePosition().distanceTo(center) > 4.3) {
                if (approachTicks % 20 == 0 || e.getNavigation().isDone()) {
                    boolean ok = e.getNavigation().moveTo(current.getX() + 0.5, current.getY(), current.getZ() + 0.5, 1.15);
                    if (!ok && approachTicks > 40) { skip.add(current); current = null; return false; }
                }
                if (++approachTicks > 200) { skip.add(current); current = null; }
                return false;
            }
            e.getNavigation().stop();
            e.getLookControl().setLookAt(center);
            if (breakTicks == 0) {
                float hardness = state.getDestroySpeed(e.level(), current);
                if (hardness < 0) { skip.add(current); current = null; return false; }
                equipBestTool(e, state);
                ItemStack tool = e.getMainHandItem();
                boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
                if (!correct) {
                    fail("для " + id(state.getBlock()) + " нужен инструмент получше (например кирка нужного уровня). "
                            + "Добыто " + mined + ". Инвентарь: " + inventory(e));
                    return true;
                }
                float speed = Math.max(1f, tool.getDestroySpeed(state));
                breakNeeded = Math.max(2, (int) Math.ceil(hardness * 30f / speed));
            }
            breakTicks++;
            if (breakTicks % 5 == 0) e.swing(InteractionHand.MAIN_HAND);
            e.level().destroyBlockProgress(e.getId(), current, Math.min(9, breakTicks * 10 / breakNeeded));
            if (breakTicks >= breakNeeded) {
                ServerLevel lvl = (ServerLevel) e.level();
                BlockEntity be = lvl.getBlockEntity(current);
                Block.dropResources(state, lvl, current, be, e, e.getMainHandItem());
                lvl.destroyBlock(current, false, e);
                lvl.destroyBlockProgress(e.getId(), current, -1);
                if (e.getMainHandItem().isDamageableItem())
                    e.getMainHandItem().hurtAndBreak(1, e, x -> x.broadcastBreakEvent(EquipmentSlot.MAINHAND));
                e.getNavigation().moveTo(current.getX() + 0.5, current.getY(), current.getZ() + 0.5, 1.0);
                mined++;
                current = null;
                breakTicks = 0;
                collectTicks = 12;
            }
            return false;
        }

        private void finish(CompanionEntity e) {
            complete("Добыл " + mined + " из " + want + " (" + name + "). Инвентарь: " + inventory(e));
        }

        @Override
        protected void onTimeout(CompanionEntity e) {
            complete("Время вышло. Добыл " + mined + " из " + want + ". Инвентарь: " + inventory(e));
        }
    }

    /** Атаковать сущность. */
    public static class Attack extends CompanionTask {
        private final net.minecraft.world.entity.LivingEntity target;

        public Attack(net.minecraft.world.entity.LivingEntity target) {
            super(30);
            this.target = target;
        }

        @Override
        protected boolean step(CompanionEntity e) {
            if (!target.isAlive()) {
                complete(entityName(target) + " побеждён. Здоровье " + Math.round(e.getHealth()) + "/" + Math.round(e.getMaxHealth()) + ".");
                return true;
            }
            if (e.distanceTo(target) > 40) {
                complete(entityName(target) + " убежал.");
                e.setTarget(null);
                return true;
            }
            if (e.getTarget() != target) e.setTarget(target);
            return false;
        }

        @Override
        protected void onTimeout(CompanionEntity e) {
            e.setTarget(null);
            complete("Не успел победить " + entityName(target) + " за 30 с.");
        }
    }

    /** Отдать предметы игроку. */
    public static class Give extends CompanionTask {
        private final ServerPlayer player;
        private final Item item;
        private final int count;

        public Give(ServerPlayer player, Item item, int count) {
            super(60);
            this.player = player;
            this.item = item;
            this.count = count;
        }

        @Override
        protected boolean step(CompanionEntity e) {
            if (e.distanceTo(player) > 2.8) {
                if (ticks % 15 == 1) e.getNavigation().moveTo(player, 1.2);
                return false;
            }
            e.getNavigation().stop();
            e.getLookControl().setLookAt(player);
            int given = 0;
            for (ItemStack s : take(e.inventory, item, count)) {
                given += s.getCount();
                if (!player.getInventory().add(s)) player.drop(s, false);
            }
            e.swing(InteractionHand.MAIN_HAND);
            complete("Отдал " + given + " " + id(item) + " игроку " + player.getGameProfile().getName() + ".");
            return true;
        }
    }

    /** Переплавка: по 1 с на предмет. */
    public static class Smelt extends CompanionTask {
        private final Item input;
        private final int total;
        private int done = 0;
        private int fuelLeft = 0;

        public Smelt(Item input, int total) {
            super(total + 15);
            this.input = input;
            this.total = total;
        }

        @Override
        protected boolean step(CompanionEntity e) {
            if (ticks % 20 != 0) return false;
            if (done >= total) { finish(e); return true; }
            SmeltingRecipe r = smeltRecipe(e, input);
            if (r == null || count(e.inventory, input) == 0) { finish(e); return true; }
            if (fuelLeft < 200) {
                // лучшее топливо — с самым долгим горением (уголь лучше брёвен и досок)
                int bestSlot = -1, bestBurn = 0;
                for (int i = 0; i < e.inventory.getContainerSize(); i++) {
                    ItemStack s = e.inventory.getItem(i);
                    int bt = burnTime(s);
                    if (bt > bestBurn && !s.is(input) && !s.is(net.minecraft.world.item.Items.LAVA_BUCKET)) { bestBurn = bt; bestSlot = i; }
                }
                boolean found = bestSlot >= 0;
                if (found) {
                    ItemStack s = e.inventory.getItem(bestSlot);
                    fuelLeft += bestBurn;
                    ItemStack rem = s.getCraftingRemainingItem();
                    s.shrink(1);
                    if (!rem.isEmpty()) giveOrDrop(e, rem.copy());
                }
                if (!found) { fail("кончилось топливо. Переплавлено " + done + ". Инвентарь: " + inventory(e)); return true; }
            }
            fuelLeft -= 200;
            take(e.inventory, input, 1);
            giveOrDrop(e, r.getResultItem(e.level().registryAccess()).copy());
            done++;
            return false;
        }

        private void finish(CompanionEntity e) {
            complete("Переплавил " + done + " " + id(input) + ". Инвентарь: " + inventory(e));
        }
    }

    /** Подождать (прерывается новым сообщением игрока). */
    public static class Wait extends CompanionTask {
        private final java.util.function.BooleanSupplier interrupt;

        public Wait(int seconds, java.util.function.BooleanSupplier interrupt) {
            super(seconds);
            this.interrupt = interrupt;
        }

        @Override
        protected boolean step(CompanionEntity e) {
            if (interrupt.getAsBoolean()) { complete("Пришло новое сообщение."); return true; }
            return false;
        }

        @Override
        protected void onTimeout(CompanionEntity e) {
            complete("Подождал.");
        }
    }

    /** Действие у хранилища после подхода. */
    public static class AtStorage extends CompanionTask {
        private final BlockPos pos;
        private final boolean deposit;
        private final String item;
        private final int count;

        public AtStorage(BlockPos pos, boolean deposit, String item, int count) {
            super(5);
            this.pos = pos;
            this.deposit = deposit;
            this.item = item;
            this.count = count;
        }

        @Override
        protected boolean step(CompanionEntity e) {
            e.getLookControl().setLookAt(Vec3.atCenterOf(pos));
            complete(deposit ? deposit(e, pos, item, count) : withdraw(e, pos, item, count));
            return true;
        }
    }
}
