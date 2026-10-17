package com.stella.end;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Raises the off-hand shield of a castle melee mob when a target closes in, so the shield works with the mobs mod
 * absent. It holds the shield {@link #HOLD_TICKS}, rests {@link #REST_TICKS}, and never raises it in striking reach.
 */
public final class CastleShieldGoal extends Goal {
	public static final int HOLD_TICKS = 20;
	public static final int REST_TICKS = 40;
	public static final double MELEE_REACH = 2.5;
	public static final double GUARD_RANGE = 6.0;

	private final Mob mob;
	private int nextRaiseAt;
	private int heldFor;

	public CastleShieldGoal(final Mob mob) {
		this.mob = mob;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		if (mob.tickCount < nextRaiseAt || mob.isUsingItem() || target == null || !target.isAlive()
			|| !mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return false;
		}
		double distance = mob.distanceTo(target);
		return distance > MELEE_REACH && distance <= GUARD_RANGE;
	}

	@Override
	public boolean canContinueToUse() {
		return heldFor < HOLD_TICKS && mob.isUsingItem();
	}

	@Override
	public void start() {
		heldFor = 0;
		mob.startUsingItem(InteractionHand.OFF_HAND);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		heldFor++;
	}

	@Override
	public void stop() {
		mob.stopUsingItem();
		nextRaiseAt = mob.tickCount + REST_TICKS;
	}
}
