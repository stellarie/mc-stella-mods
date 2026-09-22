package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RetreatGoal<T extends PathfinderMob> extends Goal {
	public static final double HEALTH_FRACTION = 0.35;
	private static final int DURATION_TICKS = 100;
	private static final int REARM_TICKS = 100;
	private static final int MAX_ATTEMPTS = 4;
	private static final int PROGRESS_CHECK_TICKS = 10;
	private static final double MINIMUM_GAIN = 1.0;
	private static final double PROGRESS_EPSILON = 0.25;
	private static final double SPEED_MODIFIER = 1.2;

	private final T mob;
	private @Nullable LivingEntity attacker;
	private long endTick;
	private long nextProgressCheckTick;
	private double startDistance;
	private double bestDistance;
	private double checkedDistance;
	private int attempts;
	private boolean gainedDistance;

	public RetreatGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.mob);
		if (!state.playerInRange(this.mob)) {
			return false;
		}

		if (this.mob.level().getGameTime() < state.getRetreatReadyTick()) {
			return false;
		}

		if (this.mob.getHealth() > this.mob.getMaxHealth() * HEALTH_FRACTION) {
			return false;
		}

		if (!this.mob.onGround() && !this.mob.isInLiquid() && !this.mob.isPassenger()) {
			return false;
		}

		LivingEntity threat = this.mob.getTarget();
		if (threat == null || !threat.isAlive()) {
			return false;
		}

		return FleePositions.away(this.mob, threat.position()) != null;
	}

	@Override
	public void start() {
		this.attacker = this.mob.getTarget();
		this.startDistance = this.attacker == null ? 0.0 : this.mob.distanceTo(this.attacker);
		this.bestDistance = this.startDistance;
		this.checkedDistance = this.startDistance;
		this.attempts = 0;
		this.gainedDistance = false;
		this.endTick = this.mob.level().getGameTime() + DURATION_TICKS;
		this.repath();
	}

	@Override
	public boolean canContinueToUse() {
		if (this.attacker == null || !this.attacker.isAlive()) {
			return false;
		}

		double distance = this.mob.distanceTo(this.attacker);
		this.bestDistance = Math.max(this.bestDistance, distance);

		if (this.bestDistance >= this.startDistance + MINIMUM_GAIN) {
			this.gainedDistance = true;
			return false;
		}

		long now = this.mob.level().getGameTime();
		if (now >= this.endTick) {
			return false;
		}

		if (now < this.nextProgressCheckTick) {
			return true;
		}

		boolean progressing = distance > this.checkedDistance + PROGRESS_EPSILON;
		this.checkedDistance = distance;

		if (progressing) {
			this.nextProgressCheckTick = now + PROGRESS_CHECK_TICKS;
			return true;
		}

		return this.repath();
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		this.attacker = null;

		if (this.gainedDistance) {
			ReactiveState state = ReactiveState.get(this.mob);
			state.setRetreatReadyTick(this.mob.level().getGameTime() + REARM_TICKS);
		}

		this.gainedDistance = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	private boolean repath() {
		LivingEntity threat = this.attacker;
		if (threat == null || this.attempts >= MAX_ATTEMPTS) {
			return false;
		}

		this.attempts++;
		Vec3 away = FleePositions.away(this.mob, threat.position());
		if (away == null) {
			return false;
		}

		boolean moved = this.mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED_MODIFIER);
		this.nextProgressCheckTick = this.mob.level().getGameTime() + PROGRESS_CHECK_TICKS;
		return moved;
	}
}
