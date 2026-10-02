package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs a hurt mob away from its target. Two modes share the goal.
 *
 * <p>A mob with a player target retreats only in the RETREAT stance. Its stance retreat runs toward the pack, ends when
 * the gap to the player is {@link #STANCE_GAP} or more, after {@link #STANCE_DURATION_TICKS}, or when no flee spot is
 * left, and then leaves the mob in KITE. Its timers count the mob's own ticks.
 *
 * <p>A mob with any other target keeps the old rule: it retreats at 35 percent health or less, runs straight away, and
 * stops after a gain of one block, with a rearm of 100 ticks.
 */
public class RetreatGoal<T extends PathfinderMob> extends Goal {
	public static final double HEALTH_FRACTION = 0.35;
	/** A stance retreat ends at this gap from the threat (decision D2). */
	public static final double STANCE_GAP = 8.0;
	public static final int STANCE_DURATION_TICKS = 100;
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
	private boolean stanceRun;

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

		// The shared rearm budget, counted in ticks. This goal sits at rank 0, so it is evaluated first
		// on every tick a MOVE goal is considered, and it is the single place the budget decreases.
		boolean coolingDown = state.isRetreatCoolingDown();
		if (coolingDown) {
			state.tickRetreatCooldown();
		}

		LivingEntity threat = this.mob.getTarget();
		if (threat instanceof Player) {
			// A stance retreat ignores the rearm.
			// Only the old retreat arms it.
			return this.stanceRetreatWanted(threat);
		}

		if (coolingDown) {
			return false;
		}

		if (this.mob.getHealth() > this.mob.getMaxHealth() * HEALTH_FRACTION) {
			return false;
		}

		if (!this.grounded()) {
			return false;
		}

		if (threat == null || !threat.isAlive()) {
			return false;
		}

		return FleePositions.away(this.mob, threat.position()) != null;
	}

	@Override
	public void start() {
		this.attacker = this.mob.getTarget();
		this.stanceRun = this.attacker instanceof Player;
		this.startDistance = this.attacker == null ? 0.0 : this.mob.distanceTo(this.attacker);
		this.bestDistance = this.startDistance;
		this.checkedDistance = this.startDistance;
		this.attempts = 0;
		this.gainedDistance = false;
		this.endTick = this.now() + (this.stanceRun ? STANCE_DURATION_TICKS : DURATION_TICKS);
		this.repath();
	}

	@Override
	public boolean canContinueToUse() {
		if (this.attacker == null || !this.attacker.isAlive()) {
			return false;
		}

		double distance = this.mob.distanceTo(this.attacker);
		this.bestDistance = Math.max(this.bestDistance, distance);

		if (this.stanceRun) {
			// A new stance or a new target ends the run.
			if (Stance.of(this.mob) != Stance.RETREAT || this.mob.getTarget() != this.attacker) {
				return false;
			}

			if (distance >= STANCE_GAP) {
				return false;
			}
		} else if (this.bestDistance >= this.startDistance + MINIMUM_GAIN) {
			this.gainedDistance = true;
			return false;
		}

		long now = this.now();
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

		if (this.stanceRun) {
			this.stanceRun = false;
			Stance.retreatEnded(this.mob);
		} else if (this.gainedDistance) {
			ReactiveState state = ReactiveState.get(this.mob);
			state.armRetreatCooldown(REARM_TICKS);
		}

		this.gainedDistance = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	/** True when a stance retreat can start now. */
	private boolean stanceRetreatWanted(final LivingEntity threat) {
		return Stance.of(this.mob) == Stance.RETREAT
			&& threat.isAlive()
			&& this.grounded()
			&& this.mob.distanceTo(threat) < STANCE_GAP
			&& FleePositions.towardPack(this.mob, threat.position()) != null;
	}

	private boolean grounded() {
		return this.mob.onGround() || this.mob.isInLiquid() || this.mob.isPassenger();
	}

	/** A stance run counts the mob's own ticks. */
	private long now() {
		return this.stanceRun ? this.mob.tickCount : this.mob.level().getGameTime();
	}

	private boolean repath() {
		LivingEntity threat = this.attacker;
		if (threat == null || this.attempts >= MAX_ATTEMPTS) {
			return false;
		}

		this.attempts++;
		Vec3 away = this.stanceRun
			? FleePositions.towardPack(this.mob, threat.position())
			: FleePositions.away(this.mob, threat.position());
		if (away == null) {
			return false;
		}

		boolean moved = this.mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED_MODIFIER);
		this.nextProgressCheckTick = this.now() + PROGRESS_CHECK_TICKS;
		return moved;
	}
}
