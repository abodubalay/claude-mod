package com.babushki.entity;

import com.babushki.ModRegistry;
import com.babushki.block.BenchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Невидимое «сиденье», на котором сидит игрок на лавочке. */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SeatEntity create(Level level, BlockPos pos, Direction facing) {
        SeatEntity seat = new SeatEntity(ModRegistry.SEAT.get(), level);
        seat.setPos(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5);
        seat.setYRot(facing.toYRot());
        return seat;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            boolean benchHere = level().getBlockState(blockPosition()).getBlock() instanceof BenchBlock;
            if (getPassengers().isEmpty() || !benchHere) discard();
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof LivingEntity le) {
            le.setYBodyRot(getYRot());
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction facing = Direction.fromYRot(getYRot());
        BlockPos front = blockPosition().relative(facing);
        if (level().getBlockState(front).getCollisionShape(level(), front).isEmpty()) {
            return new Vec3(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
        }
        return new Vec3(getX(), blockPosition().getY() + 1.0, getZ());
    }

    @Override public double getPassengersRidingOffset() { return 0.0; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
