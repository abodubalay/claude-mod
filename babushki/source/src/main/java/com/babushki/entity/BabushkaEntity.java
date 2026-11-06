package com.babushki.entity;

import com.babushki.block.BenchBlock;
import com.babushki.logic.BabushkiManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Бабушка. Никого не бьёт, ничего не ломает, урон не получает.
 * Сущность не сохраняется в мир: её каждый день заново «выпускает» {@link BabushkiManager}.
 */
public class BabushkaEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(BabushkaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SITTING = SynchedEntityData.defineId(BabushkaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(BabushkaEntity.class, EntityDataSerializers.INT);

    private enum State { SIT, STAND, GO_TO_PLAYER, WAVE, GIVING, WATCH, RETURN }

    // --- серверное состояние ---
    @Nullable private UUID ownerId;
    @Nullable private BlockPos benchLeft;
    private Direction benchFacing = Direction.NORTH;
    private State state = State.STAND;
    private int stateTicks;
    private int animTicks;
    @Nullable private UUID giftTarget;
    private final List<ItemStack> giftItems = new ArrayList<>();
    @Nullable private Runnable giftCallback;
    private boolean watchEat;
    private boolean eaten;
    private int leaveTimer = -1;

    public BabushkaEntity(EntityType<? extends BabushkaEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ROLE, 0);
        entityData.define(DATA_SITTING, false);
        entityData.define(DATA_ANIM, 0);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                if (!isSitting()) super.clientTick();
            }
        };
    }

    // ---------- доступ ----------
    public Role getRole() { return Role.values()[Math.floorMod(entityData.get(DATA_ROLE), 2)]; }
    public void setRole(Role role) {
        entityData.set(DATA_ROLE, role.ordinal());
        setCustomName(Component.literal(role.displayName).withStyle(s -> s.withColor(role.textColor())));
    }
    public boolean isSitting() { return entityData.get(DATA_SITTING); }
    private void setSitting(boolean s) {
        entityData.set(DATA_SITTING, s);
        setNoGravity(s);
        this.noPhysics = s;
    }
    public Anim getAnim() { return Anim.byId(entityData.get(DATA_ANIM)); }
    public void playAnim(Anim anim, int ticks) {
        entityData.set(DATA_ANIM, anim.ordinal());
        animTicks = ticks;
    }
    @Nullable public UUID getOwnerId() { return ownerId; }
    public boolean isBusy() { return state != State.SIT && state != State.STAND; }

    public void setHome(UUID owner, @Nullable BlockPos benchLeft, Direction facing) {
        this.ownerId = owner;
        this.benchLeft = benchLeft;
        this.benchFacing = facing;
    }

    public BlockPos getSeatPos() {
        if (benchLeft == null) return blockPosition();
        return getRole() == Role.VALYA ? benchLeft : benchLeft.relative(benchFacing.getClockWise());
    }

    public boolean benchValid() {
        if (benchLeft == null) return false;
        return level().getBlockState(getSeatPos()).getBlock() instanceof BenchBlock;
    }

    /** Мгновенно сесть на своё место на лавочке. */
    public void sitDown() {
        if (!benchValid()) { standUp(); state = State.STAND; return; }
        BlockPos seat = getSeatPos();
        getNavigation().stop();
        setSitting(true);
        float yaw = benchFacing.toYRot();
        moveTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, yaw, 0f);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setDeltaMovement(Vec3.ZERO);
        state = State.SIT;
        stateTicks = 0;
    }

    /** Встать с лавочки на клетку перед ней. */
    private void standUp() {
        if (!isSitting()) return;
        setSitting(false);
        BlockPos front = getSeatPos().relative(benchFacing);
        if (isFree(front)) {
            moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, getYRot(), 0f);
        } else if (isFree(front.below()) ) {
            moveTo(front.getX() + 0.5, front.getY() - 1, front.getZ() + 0.5, getYRot(), 0f);
        } else {
            // Перед лавочкой тесно — стоим на сиденье.
            moveTo(getX(), getSeatPos().getY() + BenchBlock.SEAT_HEIGHT, getZ(), getYRot(), 0f);
        }
    }

    private boolean isFree(BlockPos p) {
        Level l = level();
        return l.getBlockState(p).getCollisionShape(l, p).isEmpty()
                && l.getBlockState(p.above()).getCollisionShape(l, p.above()).isEmpty()
                && !l.getBlockState(p.below()).getCollisionShape(l, p.below()).isEmpty();
    }

    /** Лавочку переставили — пойти к новой. */
    public void goToBench() {
        if (state == State.SIT) {
            setSitting(false);
            moveTo(getX(), Math.floor(getY()) + BenchBlock.SEAT_HEIGHT, getZ(), getYRot(), 0f);
            state = State.RETURN;
            stateTicks = 0;
        } else if (state == State.STAND) {
            state = State.RETURN;
            stateTicks = 0;
        }
    }

    /** Попрощаться и уйти домой через пару секунд. */
    public void leaveSoon(int ticks) {
        if (leaveTimer < 0) leaveTimer = ticks;
    }

    // ---------- подарки ----------
    /**
     * Встать, подойти к игроку (или помахать, если он рядом), протянуть руку и бросить подарок под ноги.
     * @return false, если бабушка сейчас занята
     */
    public boolean startGift(ServerPlayer target, List<ItemStack> items, boolean watchEat, @Nullable Runnable onGive) {
        if (isBusy()) return false;
        giftTarget = target.getUUID();
        giftItems.clear();
        for (ItemStack s : items) giftItems.add(s.copy());
        giftCallback = onGive;
        this.watchEat = watchEat;
        this.eaten = false;
        standUp();
        if (distanceTo(target) <= 3.5) {
            state = State.WAVE;
            playAnim(Anim.WAVE, 20);
        } else {
            state = State.GO_TO_PLAYER;
        }
        stateTicks = 0;
        return true;
    }

    public boolean isWatching(UUID player) {
        return state == State.WATCH && player.equals(giftTarget);
    }

    public void markEaten() { eaten = true; }

    @Nullable
    private ServerPlayer target() {
        if (giftTarget == null || !(level() instanceof ServerLevel sl)) return null;
        Player p = sl.getPlayerByUUID(giftTarget);
        return p instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    private void dropGift(ServerPlayer p) {
        for (ItemStack s : giftItems) {
            ItemEntity e = new ItemEntity(level(), p.getX(), p.getY() + 0.1, p.getZ(), s.copy());
            e.setDeltaMovement(Vec3.ZERO);
            e.setNoPickUpDelay();
            level().addFreshEntity(e);
        }
        giftItems.clear();
        if (giftCallback != null) giftCallback.run();
        giftCallback = null;
    }

    // ---------- тик ----------
    @Override
    public void tick() {
        super.tick();
        if (isSitting()) {
            setYBodyRot(getYRot());
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        stateTicks++;
        if (animTicks > 0 && --animTicks == 0) entityData.set(DATA_ANIM, Anim.NONE.ordinal());

        if (leaveTimer >= 0 && --leaveTimer <= 0) {
            discard();
            return;
        }
        if (ownerId == null) { discard(); return; }

        ServerPlayer target = target();
        switch (state) {
            case SIT -> {
                if (!benchValid()) { standUp(); state = State.STAND; break; }
                BlockPos seat = getSeatPos();
                setPos(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5);
                setYRot(benchFacing.toYRot());
                lookAround();
            }
            case STAND -> {
                lookAround();
                if (benchValid() && stateTicks % 20 == 0) { state = State.RETURN; stateTicks = 0; }
            }
            case WAVE -> {
                if (target == null) { state = State.RETURN; stateTicks = 0; break; }
                getLookControl().setLookAt(target, 30f, 30f);
                if (stateTicks >= 20) { state = State.GIVING; stateTicks = 0; playAnim(Anim.HAND_OUT, 25); }
            }
            case GO_TO_PLAYER -> {
                if (target == null) { state = State.RETURN; stateTicks = 0; break; }
                getLookControl().setLookAt(target, 30f, 30f);
                if (stateTicks % 10 == 1) getNavigation().moveTo(target, 0.9);
                if (distanceTo(target) < 2.5 || stateTicks > 300) {
                    getNavigation().stop();
                    state = State.GIVING; stateTicks = 0;
                    playAnim(Anim.HAND_OUT, 25);
                }
            }
            case GIVING -> {
                if (target == null) { giftItems.clear(); state = State.RETURN; stateTicks = 0; break; }
                getLookControl().setLookAt(target, 30f, 30f);
                if (stateTicks == 15) dropGift(target);
                if (stateTicks >= 30) {
                    state = watchEat ? State.WATCH : State.RETURN;
                    stateTicks = 0;
                }
            }
            case WATCH -> {
                // «Ешь давай, при мне ешь!» — стоит рядом, пока не съест (или 10 секунд).
                if (target == null || eaten || stateTicks > 200) { state = State.RETURN; stateTicks = 0; break; }
                getLookControl().setLookAt(target, 30f, 30f);
                if (distanceTo(target) > 3 && stateTicks % 10 == 1) getNavigation().moveTo(target, 0.8);
                if (stateTicks % 60 == 30) playAnim(Anim.FINGER_WAG, 20);
            }
            case RETURN -> {
                if (!benchValid()) { getNavigation().stop(); state = State.STAND; stateTicks = 0; break; }
                BlockPos front = getSeatPos().relative(benchFacing);
                double d = distanceToSqr(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
                if (d < 2.0 || stateTicks > 300 || distanceToSqr(Vec3.atCenterOf(getSeatPos())) > 48 * 48) {
                    sitDown();
                } else if (stateTicks % 20 == 1) {
                    getNavigation().moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 0.8);
                }
            }
        }
    }

    private void lookAround() {
        if (getAnim() == Anim.WHISPER) {
            BabushkaEntity friend = BabushkiManager.get().findPartner(this);
            if (friend != null) { getLookControl().setLookAt(friend, 40f, 30f); return; }
        }
        Player p = level().getNearestPlayer(this, 10);
        if (p != null) {
            getLookControl().setLookAt(p, 20f, 20f);
        } else if (isSitting()) {
            Vec3 ahead = position().add(Vec3.atLowerCornerOf(benchFacing.getNormal()).scale(3)).add(0, 1.4, 0);
            getLookControl().setLookAt(ahead.x, ahead.y, ahead.z, 10f, 10f);
        }
    }

    // ---------- никакого вреда ----------
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof ServerPlayer p) {
            BabushkiManager.get().onBabushkaHit(this, p);
        }
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        return BabushkiManager.get().onBabushkaInteract(this, (ServerPlayer) player, player.getItemInHand(hand));
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public void push(Entity e) {}
    @Override public boolean canBeLeashed(Player p) { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
}
