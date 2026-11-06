package com.kitsmotrit.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/** Курицы и коровы поднимают головы и смотрят на кита, если он рядом. */
public class LookAtWhaleGoal extends Goal {
    private final Mob mob;
    private MaidWhale whale;

    public LookAtWhaleGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    private MaidWhale findWhale() {
        List<MaidWhale> list = mob.level().getEntitiesOfClass(MaidWhale.class, mob.getBoundingBox().inflate(40, 48, 40),
                w -> w.isAlive() && w.getState() != MaidWhale.LEAVE && w.getState() != MaidWhale.FLEE);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public boolean canUse() {
        if (mob.tickCount % 10 != 0) return false;
        whale = findWhale();
        return whale != null;
    }

    @Override
    public boolean canContinueToUse() {
        return whale != null && whale.isAlive() && whale.getState() != MaidWhale.LEAVE && whale.getState() != MaidWhale.FLEE
                && whale.distanceToSqr(mob) < 64 * 64;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(whale.getX(), whale.getY() + 2, whale.getZ(), 20f, 90f);
    }

    @Override
    public void stop() { whale = null; }
}
