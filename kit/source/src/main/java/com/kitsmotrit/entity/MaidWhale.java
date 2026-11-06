package com.kitsmotrit.entity;

import com.kitsmotrit.Chat;
import com.kitsmotrit.KitConfig;
import com.kitsmotrit.KitMod;
import com.kitsmotrit.Phrases;
import com.kitsmotrit.Simpatia;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Огромный летающий кит-цундере в костюме горничной. Никому не вредит, ничего не ломает:
 * проходит сквозь блоки, неуязвим, стрелы пролетают насквозь.
 */
public class MaidWhale extends Mob {
    public static final int APPEAR = 0, IDLE = 1, SHY = 2, FLEE = 3, LEAVE = 4;
    public static final int APPEAR_TICKS = 40, LEAVE_TICKS = 70;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MaidWhale.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE_START = SynchedEntityData.defineId(MaidWhale.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BLUSH = SynchedEntityData.defineId(MaidWhale.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> PURPLE = SynchedEntityData.defineId(MaidWhale.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EYES_WIDE = SynchedEntityData.defineId(MaidWhale.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> COVER = SynchedEntityData.defineId(MaidWhale.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> WAVE_UNTIL = SynchedEntityData.defineId(MaidWhale.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(MaidWhale.class, EntityDataSerializers.FLOAT);


    @Nullable private UUID owner;
    private double hoverHeight = 14;
    /** Кружит вокруг игрока: угол и расстояние по горизонтали. */
    private double orbitAngle, orbitDistance = 18;
    private int stayTicks = 600;
    private int gazeTicks, punchCooldown, blushHoldTicks, fidgetTicks;
    @Nullable private String shyPhrase;
    private float targetYaw;

    public MaidWhale(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, APPEAR);
        entityData.define(STATE_START, 0);
        entityData.define(BLUSH, 0f);
        entityData.define(PURPLE, false);
        entityData.define(EYES_WIDE, false);
        entityData.define(COVER, false);
        entityData.define(WAVE_UNTIL, 0);
        entityData.define(SCALE, 1f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SCALE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getScale());
    }

    // ---------- состояние (видно и на клиенте) ----------

    public int getState() { return entityData.get(STATE); }
    public int stateAge() { return (int) (level().getGameTime() - entityData.get(STATE_START)); }
    public float getBlush() { return entityData.get(BLUSH); }
    public boolean isPurple() { return entityData.get(PURPLE); }
    public boolean eyesWide() { return entityData.get(EYES_WIDE); }
    public boolean coversFace() { return entityData.get(COVER); }
    public boolean isWaving() { return level().getGameTime() < entityData.get(WAVE_UNTIL); }
    public float getScale() { return entityData.get(SCALE); }

    /** Прозрачность: проявляется за 2 с, растворяется при уходе. */
    public float fade(float partial) {
        float age = stateAge() + partial;
        return switch (getState()) {
            case APPEAR -> Mth.clamp(age / APPEAR_TICKS, 0f, 1f);
            case LEAVE -> Mth.clamp(1f - age / LEAVE_TICKS, 0f, 1f);
            case FLEE -> Mth.clamp(1f - (age - 40f) / 20f, 0f, 1f);
            default -> 1f;
        };
    }

    private void setState(int s) {
        entityData.set(STATE, s);
        entityData.set(STATE_START, (int) level().getGameTime());
    }

    @Nullable
    public ServerPlayer ownerPlayer() {
        if (owner == null || getServer() == null) return null;
        return getServer().getPlayerList().getPlayer(owner);
    }

    @Nullable public UUID getOwnerId() { return owner; }

    /** Кит этого игрока (рядом с ним), если есть. */
    @Nullable
    public static MaidWhale ofPlayer(ServerPlayer p) {
        for (MaidWhale w : p.serverLevel().getEntitiesOfClass(MaidWhale.class, p.getBoundingBox().inflate(128)))
            if (w.isAlive() && p.getUUID().equals(w.owner)) return w;
        return null;
    }

    // ---------- появление ----------

    /** Создать кита над игроком. */
    public static MaidWhale spawnFor(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        MaidWhale w = KitMod.MAID_WHALE.get().create(lvl);
        if (w == null) return null;
        int simp = Simpatia.get(p);
        w.owner = p.getUUID();
        w.entityData.set(SCALE, KitConfig.WHALE_SCALE.get().floatValue());
        w.refreshDimensions();
        w.hoverHeight = 9 + lvl.random.nextDouble() * 3;       // ниже — чтобы хорошо было видно
        w.orbitDistance = 18 + lvl.random.nextDouble() * 6;    // и чуть в стороне, а не прямо над головой
        w.orbitAngle = lvl.random.nextDouble() * Math.PI * 2;
        int stay = KitConfig.STAY_MIN.get() + lvl.random.nextInt(Math.max(1, KitConfig.STAY_MAX.get() - KitConfig.STAY_MIN.get() + 1));
        w.stayTicks = Math.min(KitConfig.STAY_MAX_TOTAL.get(), stay + 5 * simp) * 20;
        float yaw = lvl.random.nextFloat() * 360f;
        w.moveTo(p.getX() + Math.cos(w.orbitAngle) * w.orbitDistance, p.getY() + w.hoverHeight,
                p.getZ() + Math.sin(w.orbitAngle) * w.orbitDistance, yaw, 0);
        w.targetYaw = yaw;
        w.setYBodyRot(yaw);
        w.setYHeadRot(yaw);
        lvl.addFreshEntity(w);
        w.setState(APPEAR);
        if (KitConfig.SOUND.get())
            lvl.playSound(null, w.getX(), w.getY(), w.getZ(), KitMod.WHALE_SONG.get(), SoundSource.NEUTRAL, 4f, 0.8f);
        w.greet(p, simp);
        return w;
    }

    private void greet(ServerPlayer p, int simp) {
        int lvl = Simpatia.level(simp);
        Chat.whale(p, Phrases.next(p.getUUID(), "greet", Phrases.greetPool(lvl)));
        if (lvl >= 2 && random.nextFloat() < 0.5f) { // «Друг»: иногда сам роняет еду
            ItemStack[] food = {new ItemStack(Items.COOKED_COD, 3), new ItemStack(Items.COOKED_SALMON, 2), new ItemStack(Items.BREAD, 3)};
            dropOnPlayer(p, food[random.nextInt(food.length)]);
        }
        if (lvl >= 3) entityData.set(WAVE_UNTIL, (int) level().getGameTime() + 80); // «Лучший друг»: машет плавником
    }

    private void dropOnPlayer(ServerPlayer p, ItemStack stack) {
        ItemEntity item = new ItemEntity(level(), p.getX(), p.getY() + 6, p.getZ(), stack);
        item.setDeltaMovement(0, -0.2, 0);
        item.setPickUpDelay(10);
        level().addFreshEntity(item);
    }

    // ---------- каждый тик ----------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerPlayer p = ownerPlayer();
        int state = getState(), age = stateAge();

        if ((p == null || p.level() != level() || p.isSpectator()) && state != LEAVE && state != FLEE) {
            startLeave(null);
            return;
        }
        if (punchCooldown > 0) punchCooldown--;
        tickBlush();

        switch (state) {
            case APPEAR -> { follow(p, 0.03); if (age >= APPEAR_TICKS) setState(IDLE); }
            case IDLE -> {
                follow(p, 0.03);
                checkGaze(p);
                if (--stayTicks <= 0) startLeave(p);
            }
            case SHY -> shyTick(p, age);
            case FLEE -> {
                // резко отвернулся и быстро уплывает вверх, оставляя след из сердечек
                Vec3 away = Vec3.directionFromRotation(0, getYRot()).scale(0.35);
                setPos(getX() + away.x, getY() + 0.6, getZ() + away.z);
                if (age % 2 == 0) ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 1, getZ(), 2, 1.5, 1, 1.5, 0);
                if (age >= 60) discard();
            }
            case LEAVE -> {
                setPos(getX(), getY() + 0.12, getZ()); // медленно уплывает вверх и растворяется в облаках
                if (age >= LEAVE_TICKS) discard();
            }
            default -> {}
        }
        turnBody();
        if (p != null && state != FLEE) lookAt(p);
    }

    @Override
    public void travel(Vec3 input) {
        // движение задаём сами — без трения и падения
    }

    /** Медленно кружит вокруг игрока чуть в стороне и плывёт за ним — боком к игроку, чтобы было хорошо видно. */
    private void follow(Player p, double speed) {
        orbitAngle += 0.0035; // один круг примерно за полторы минуты
        Vec3 target = new Vec3(p.getX() + Math.cos(orbitAngle) * orbitDistance, p.getY() + hoverHeight,
                p.getZ() + Math.sin(orbitAngle) * orbitDistance);
        Vec3 d = target.subtract(position());
        Vec3 step = d.scale(speed);
        if (step.length() > 0.45) step = step.normalize().scale(0.45);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        if (d.length() > 8) {
            targetYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f; // догоняет головой вперёд
        } else {
            // плывёт по кругу: нос по касательной, бок к игроку
            double tx = -Math.sin(orbitAngle), tz = Math.cos(orbitAngle);
            targetYaw = (float) (Mth.atan2(tz, tx) * Mth.RAD_TO_DEG) - 90f;
        }
    }

    private void turnBody() {
        float yaw = Mth.approachDegrees(getYRot(), targetYaw, getState() == FLEE ? 18f : 1.2f);
        setYRot(yaw);
        setYBodyRot(yaw);
    }

    /** Голова и глаз поворачиваются за игроком. */
    private void lookAt(Player p) {
        Vec3 head = headPos();
        Vec3 d = p.getEyePosition().subtract(head);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float diff = Mth.clamp(Mth.wrapDegrees(yaw - getYRot()), -40f, 40f);
        setYHeadRot(getYRot() + diff);
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        setXRot(Mth.clamp(pitch, -10f, 45f));
    }

    /** Где голова (для частиц и проверки взгляда). */
    public Vec3 headPos() {
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        float s = getScale();
        return position().add(0, 2.2 * s, 0).add(fwd.scale(6.0 * s));
    }

    // ---------- взгляд игрока ----------

    /** Смотрит ли игрок прямо на кита (луч взгляда проходит сквозь тело, не загорожено блоками). */
    public boolean isLookedAtBy(Player p) {
        Vec3 eye = p.getEyePosition(), dir = p.getViewVector(1f);
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        float s = getScale();
        Vec3 center = position().add(0, 2.0 * s, 0);
        double radius = 2.6 * s;
        for (double k = -11; k <= 7.5; k += 1.5) {
            Vec3 pt = center.add(fwd.scale(k * s));
            Vec3 v = pt.subtract(eye);
            double t = v.dot(dir);
            if (t <= 0 || t > 160) continue;
            if (v.subtract(dir.scale(t)).length() < radius) {
                HitResult block = level().clip(new ClipContext(eye, pt, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                return block.getType() == HitResult.Type.MISS;
            }
        }
        return false;
    }

    private void checkGaze(ServerPlayer p) {
        if (isLookedAtBy(p)) {
            if (++gazeTicks >= KitConfig.GAZE_SECONDS.get() * 20) startShy(p, null);
        } else gazeTicks = Math.max(0, gazeTicks - 2);
    }

    // ---------- цундере-режим ----------

    /** Смутился: вздрогнул, глаза расширились, краснеет, фраза, потом убегает. phrase=null — случайная. */
    public void startShy(ServerPlayer p, @Nullable String phrase) {
        int simp = Simpatia.get(p);
        if (phrase == null) {
            phrase = Phrases.next(p.getUUID(), "shy", Phrases.shyPool(Simpatia.level(simp)));
        }
        shyPhrase = phrase;
        fidgetTicks = simp >= 3 ? 40 : 0; // на 3+ ещё 2 секунды «мнётся», пряча глаза
        entityData.set(EYES_WIDE, true);
        setState(SHY);
        playSound(net.minecraft.sounds.SoundEvents.DOLPHIN_AMBIENT, 2f, 0.5f);
    }

    private void shyTick(ServerPlayer p, int age) {
        ServerLevel lvl = (ServerLevel) level();
        float target = Math.min(1f, age / 30f);
        if (getBlush() < target) entityData.set(BLUSH, target); // румянец плавно ярче
        Vec3 h = headPos();
        if (age % 4 == 0) {
            lvl.sendParticles(ParticleTypes.HEART, h.x, h.y + 1.5, h.z, 2, 2, 1, 2, 0);
            lvl.sendParticles(ParticleTypes.CLOUD, h.x, h.y + 1, h.z, 3, 1.5, 0.8, 1.5, 0.02); // «пар»
        }
        if (age == 8 && shyPhrase != null && p != null) Chat.whale(p, shyPhrase);
        if (age == 30) entityData.set(COVER, true);            // закрывает «лицо» плавником
        if (age >= 30 && age < 30 + fidgetTicks) targetYaw += (age % 20 < 10 ? 1.5f : -1.5f); // мнётся на месте
        if (age >= 30 + fidgetTicks) {
            targetYaw = getYRot() + 180f;                      // резко отворачивается
            entityData.set(EYES_WIDE, false);
            setState(FLEE);
        }
    }

    /** Через 30–60 с без взгляда: медленно и спокойно уплывает. */
    private void startLeave(@Nullable ServerPlayer p) {
        if (p != null) Chat.whale(p, Simpatia.level(Simpatia.get(p)) >= 3
                ? Phrases.BEST_FRIEND_LEAVE
                : Phrases.next(p.getUUID(), "leave", Phrases.LEAVE));
        setState(LEAVE);
    }

    private void tickBlush() {
        int state = getState();
        if (state == SHY || state == FLEE) return;
        if (blushHoldTicks > 0) { blushHoldTicks--; return; }
        float b = getBlush();
        if (b > 0) entityData.set(BLUSH, Math.max(0f, b - 0.02f)); // плавно пропадает
    }

    // ---------- удар и подарок ----------

    /** Игрок ударил (урона нет): краснеет сильнее, симпатия −1. */
    public void onPunched(ServerPlayer p) {
        if (punchCooldown > 0 || getState() == SHY || getState() == FLEE || getState() == LEAVE) return;
        punchCooldown = 30;
        entityData.set(PURPLE, false);
        entityData.set(BLUSH, Math.min(1f, getBlush() + 0.6f));
        blushHoldTicks = 40;
        entityData.set(EYES_WIDE, true);
        Chat.whale(p, Phrases.next(p.getUUID(), "punch", Phrases.PUNCH));
        Simpatia.set(p, Simpatia.get(p) - 1);
        playSound(net.minecraft.sounds.SoundEvents.DOLPHIN_HURT, 2f, 0.6f);
        ((ServerLevel) level()).sendParticles(ParticleTypes.ANGRY_VILLAGER, headPos().x, headPos().y + 1.5, headPos().z, 3, 1, 0.5, 1, 0);
        if (getServer() != null) getServer().tell(new net.minecraft.server.TickTask(getServer().getTickCount() + 40,
                () -> entityData.set(EYES_WIDE, false)));
    }

    /** Подарили цветок: краснеет до фиолетового, роняет тортик, уплывает. Симпатия +1. */
    public void onFlower(ServerPlayer p) {
        if (getState() == SHY || getState() == FLEE || getState() == LEAVE) return;
        int before = Simpatia.get(p);
        Simpatia.set(p, before + 1);
        if (Simpatia.level(before + 1) > Simpatia.level(before)) // новый уровень симпатии
            Chat.whale(p, Phrases.LEVEL_UP);
        entityData.set(PURPLE, true);
        entityData.set(BLUSH, 1f);
        dropOnPlayer(p, new ItemStack(Items.CAKE));
        ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, headPos().x, headPos().y + 1, headPos().z, 15, 2, 1, 2, 0);
        startShy(p, Phrases.FLOWER);
    }

    // ---------- неуязвим, ничего не трогает ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!level().isClientSide && source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p
                && p.getUUID().equals(owner)) onPunched(p);
        return false;
    }

    @Override public boolean canBeHitByProjectile() { return false; } // стрелы пролетают насквозь
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean ignoreExplosion() { return true; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldBeSaved() { return false; } // после перезахода не остаётся
    @Override protected void doPush(Entity e) {}
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean isPickable() { return getState() != LEAVE && getState() != FLEE; }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(14 * getScale());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 320 * 320;
    }
}
