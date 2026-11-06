package com.claudecompanion.entity;

import com.claudecompanion.brain.BrainManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Напарник: моб в виде игрока, которым управляет нейросеть (Claude, DeepSeek, Grok...). */
public class CompanionEntity extends PathfinderMob {
    /** Какая нейросеть управляет напарником (id профиля из настроек). Синхронизируется с клиентом — для скина. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> PROFILE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CompanionEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.STRING);

    public final SimpleContainer inventory = new SimpleContainer(36);
    public final List<String> memory = new ArrayList<>();
    public boolean guard = false;
    @Nullable private UUID owner;
    @Nullable private UUID followTarget;
    @Nullable private CompanionTask task;
    private int lastHurtNotify = -1000;

    public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("Claude"));
        setCustomNameVisible(true);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PROFILE, "claude");
    }

    public String getProfileId() { return entityData.get(PROFILE); }

    /** Назначить нейросеть: меняет id, имя и цвет ника. */
    public void setProfile(String id) {
        entityData.set(PROFILE, id);
        var p = com.claudecompanion.Settings.get().profile(id);
        net.minecraft.ChatFormatting color = net.minecraft.ChatFormatting.getByCode(p.color != null && !p.color.isEmpty() ? p.color.charAt(0) : '6');
        setCustomName(Component.literal(p.name).withStyle(color == null ? net.minecraft.ChatFormatting.GOLD : color));
    }

    public String displayName() { return getName().getString(); }

    /** Вдвое крепче и сильнее игрока: 40 здоровья (20 сердец) и 4 урона рукой. */
    public static final double MAX_HEALTH = 40.0;
    public static final double ATTACK_DAMAGE = 4.0;

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                e -> guard && !(e instanceof Creeper) && distanceTo(e) < 14));
    }

    // ---------- хозяин ----------

    public void setOwner(@Nullable UUID owner) { this.owner = owner; }
    @Nullable public UUID getOwnerId() { return owner; }

    @Nullable
    public ServerPlayer getOwnerPlayer() {
        if (owner == null || getServer() == null) return null;
        return getServer().getPlayerList().getPlayer(owner);
    }

    public boolean isOwner(Entity e) {
        return owner != null && e != null && owner.equals(e.getUUID());
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (isOwner(target)) return false;
        if (target instanceof CompanionEntity) return false;
        return super.canAttack(target);
    }

    // ---------- задачи ----------

    public void setFollowTarget(@Nullable UUID player) { this.followTarget = player; }
    @Nullable public UUID getFollowTarget() { return followTarget; }

    public void startTask(CompanionTask newTask) {
        if (task != null) task.cancel("прервано новым действием");
        task = newTask;
    }

    @Nullable public CompanionTask getTask() { return task; }

    /** После подхода (prev) запускаем следующее действие; его результат станет результатом prev. */
    public void startTaskChain(CompanionTask next, CompanionTask prev) {
        next.result.thenAccept(prev.result::complete);
        task = next;
    }

    public void stopAll() {
        if (task != null) task.cancel("остановлено");
        task = null;
        followTarget = null;
        setTarget(null);
        getNavigation().stop();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;

        if (isDeadOrDying()) return; // мёртвый не подбирает (иначе он «съедал» свой же дроп)
        pickUpNearbyItems();

        if (task != null) {
            CompanionTask current = task;
            boolean done;
            try {
                done = current.tick(this);
            } catch (Exception e) {
                current.fail("ошибка: " + e.getMessage());
                done = true;
            }
            if (done && task == current) task = null;
        } else if (followTarget != null && getTarget() == null) {
            tickFollow();
        }

        // медленная регенерация и автоеда
        if (tickCount % 80 == 0 && getHealth() < getMaxHealth()) heal(1.0f);
        if (tickCount % 40 == 0 && getHealth() < getMaxHealth() / 2) Actions.eatBest(this);
    }

    private void tickFollow() {
        if (getServer() == null) return;
        ServerPlayer p = getServer().getPlayerList().getPlayer(followTarget);
        if (p == null || p.level() != level()) return;
        double d = distanceTo(p);
        if (d > 40) {
            // как питомцы: если сильно отстал — телепортируемся
            teleportTo(p.getX(), p.getY(), p.getZ());
            getNavigation().stop();
        } else if (d > 3.5) {
            if (tickCount % 10 == 0) getNavigation().moveTo(p, d > 10 ? 1.4 : 1.15);
        } else if (d < 2.2) {
            getNavigation().stop();
        }
    }

    private void pickUpNearbyItems() {
        if (tickCount % 5 != 0) return;
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.6, 0.6, 1.6))) {
            if (!item.isAlive() || item.hasPickUpDelay()) continue;
            ItemStack stack = item.getItem();
            int before = stack.getCount();
            ItemStack rest = inventory.addItem(stack.copy());
            if (rest.getCount() == before) continue;
            take(item, before - rest.getCount());
            if (rest.isEmpty()) item.discard();
            else item.setItem(rest);
        }
    }

    // ---------- взаимодействие ----------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // ПКМ хозяина — открыть инвентарь напарника
        if (!level().isClientSide && isOwner(player) && player instanceof ServerPlayer sp) {
            openInventoryFor(sp);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** Открыть инвентарь напарника игроку (хозяину). */
    public void openInventoryFor(ServerPlayer sp) {
        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, inventory, 4),
                Component.literal("Инвентарь " + displayName())));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && tickCount - lastHurtNotify > 300) {
            Entity attacker = source.getEntity();
            if (attacker != null && !isOwner(attacker)) {
                lastHurtNotify = tickCount;
                BrainManager.notify(this, "[событие] Тебя атакует " + Actions.entityName(attacker)
                        + "! Здоровье " + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()) + ".");
            }
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !isRemoved() && !dead) {
            ServerPlayer o = getOwnerPlayer();
            String why = source.getLocalizedDeathMessage(this).getString();
            BlockPos p = blockPosition();
            String where = p.getX() + " " + p.getY() + " " + p.getZ() + " (" + level().dimension().location().getPath() + ")";
            boolean keep = level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            if (keep) {
                // keepInventory: запоминаем всё и сразу возрождаемся рядом с хозяином, ничего не выпадает
                CompoundTag snapshot = saveWithoutId(new CompoundTag());
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values())
                    setItemSlot(slot, ItemStack.EMPTY);
                inventory.clearContent();
                MinecraftServer server = getServer();
                net.minecraft.resources.ResourceKey<Level> dim = level().dimension();
                if (server != null) server.execute(() -> respawn(server, snapshot, dim));
                if (o != null) o.sendSystemMessage(Component.literal("§e" + displayName() + " погиб (" + why + ") на " + where
                        + ". keepInventory включён — он возродился рядом с тобой со всеми вещами."));
            } else if (o != null) {
                o.sendSystemMessage(Component.literal("§c" + displayName() + " погиб: " + why
                        + ". §eЕго вещи лежат тут: " + where + "§c. Призови снова: /claude summon " + getProfileId()));
            }
            BrainManager.remove(this);
        }
        super.die(source);
    }

    /** Возродить напарника из сохранённых данных (для keepInventory): рядом с хозяином или на точке спавна. */
    private static void respawn(MinecraftServer server, CompoundTag snapshot, net.minecraft.resources.ResourceKey<Level> dim) {
        for (String k : new String[]{"UUID", "Health", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "Motion", "FallDistance", "Air", "ActiveEffects"})
            snapshot.remove(k);
        UUID ownerId = snapshot.hasUUID("Owner") ? snapshot.getUUID("Owner") : null;
        ServerPlayer o = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        net.minecraft.server.level.ServerLevel level = o != null ? o.serverLevel() : server.getLevel(dim);
        if (level == null) level = server.overworld();
        CompanionEntity e = com.claudecompanion.ClaudeCompanion.COMPANION.get().create(level);
        if (e == null) return;
        e.load(snapshot);
        e.setUUID(UUID.randomUUID());
        BlockPos at = o != null ? o.blockPosition() : level.getSharedSpawnPos();
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        e.setHealth(e.getMaxHealth());
        e.setTarget(null);
        level.addFreshEntity(e);
        BrainManager.notify(e, "[событие] Ты погиб и возродился рядом с хозяином со всеми вещами (keepInventory). Коротко скажи об этом.");
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        // не вызываем super: там броня и предметы в руках выпадают лишь с маленьким шансом
        dropEverything();
    }

    /** Выбросить на землю всё: броню, предметы в руках и инвентарь. */
    public void dropEverything() {
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack s = getItemBySlot(slot);
            if (!s.isEmpty()) {
                spawnAtLocation(s.copy());
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ---------- сохранение ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Inventory", inventory.createTag());
        if (owner != null) tag.putUUID("Owner", owner);
        if (followTarget != null) tag.putUUID("Follow", followTarget);
        tag.putBoolean("Guard", guard);
        tag.putString("Profile", getProfileId());
        ListTag mem = new ListTag();
        for (String m : memory) mem.add(StringTag.valueOf(m));
        tag.put("Memory", mem);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        inventory.fromTag(tag.getList("Inventory", Tag.TAG_COMPOUND));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        followTarget = tag.hasUUID("Follow") ? tag.getUUID("Follow") : null;
        guard = tag.getBoolean("Guard");
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getBaseValue() < MAX_HEALTH) hp.setBaseValue(MAX_HEALTH);
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && dmg.getBaseValue() < ATTACK_DAMAGE) dmg.setBaseValue(ATTACK_DAMAGE);
        if (tag.contains("Profile")) setProfile(tag.getString("Profile"));
        memory.clear();
        ListTag mem = tag.getList("Memory", Tag.TAG_STRING);
        for (int i = 0; i < mem.size(); i++) memory.add(mem.getString(i));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) BrainManager.remove(this);
        super.remove(reason);
    }
}
