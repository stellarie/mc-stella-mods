package com.stella.mobs.behavior;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ShieldUseGoal<T extends PathfinderMob> extends Goal {
	private static final int HOLD_TICKS = 20;
	private static final int COOLDOWN_TICKS = 40;
	private static final int ARROW_SCAN_INTERVAL_TICKS = 5;
	private static final double TRIGGER_RADIUS = 8.0;
	private static final double ARROW_BOX_RADIUS = 8.0;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final T mob;
	private @Nullable Entity threat;
	private int holdTicks;

	public ShieldUseGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.mob);
		if (!state.playerInRange(this.mob)) {
			return false;
		}

		if (this.mob.level().getGameTime() < state.getShieldReadyTick()) {
			return false;
		}

		if (this.mob.isUsingItem() || !this.mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return false;
		}

		if (this.mob.getHealth() <= this.mob.getMaxHealth() * RetreatGoal.HEALTH_FRACTION) {
			return false;
		}

		Entity found = this.findThreat(state);
		if (found == null) {
			return false;
		}

		this.threat = found;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.holdTicks > 0
			&& this.threat != null
			&& this.threat.isAlive()
			&& this.mob.getHealth() > this.mob.getMaxHealth() * RetreatGoal.HEALTH_FRACTION;
	}

	@Override
	public void start() {
		this.holdTicks = HOLD_TICKS;
		this.mob.getNavigation().stop();
		this.mob.startUsingItem(InteractionHand.OFF_HAND);
	}

	@Override
	public void stop() {
		this.mob.stopUsingItem();
		this.threat = null;
		this.holdTicks = 0;
		ReactiveState state = ReactiveState.get(this.mob);
		state.setShieldReadyTick(this.mob.level().getGameTime() + COOLDOWN_TICKS);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return this.holdTicks > 0;
	}

	@Override
	public void tick() {
		Entity currentThreat = this.threat;
		if (currentThreat != null) {
			this.mob.lookAt(currentThreat, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
			this.mob.getLookControl().setLookAt(currentThreat, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		}

		this.holdTicks--;
	}

	private @Nullable Entity findThreat(final ReactiveState state) {
		LivingEntity target = this.mob.getTarget();
		if (target != null
			&& target.isAlive()
			&& this.mob.distanceToSqr(target) <= TRIGGER_RADIUS * TRIGGER_RADIUS
			&& this.mob.getSensing().hasLineOfSight(target)) {
			return target;
		}

		long now = this.mob.level().getGameTime();
		if (now < state.getNextArrowScanTick()) {
			return null;
		}

		state.setNextArrowScanTick(now + ARROW_SCAN_INTERVAL_TICKS);
		AABB box = this.mob.getBoundingBox().inflate(ARROW_BOX_RADIUS);
		List<AbstractArrow> arrows = this.mob.level().getEntitiesOfClass(AbstractArrow.class, box, this::isIncomingArrow);

		for (AbstractArrow arrow : arrows) {
			return arrow;
		}

		return null;
	}

	private boolean isIncomingArrow(final AbstractArrow arrow) {
		if (!arrow.isAlive() || arrow.getOwner() == this.mob) {
			return false;
		}

		Vec3 toMob = this.mob.position().subtract(arrow.position());
		return toMob.lengthSqr() > 0.0 && arrow.getDeltaMovement().dot(toMob) > 0.0;
	}
}
