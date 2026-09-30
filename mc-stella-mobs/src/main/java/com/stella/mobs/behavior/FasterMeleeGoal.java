package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * R22: the elite skeleton attacks half again as often, and T03's hardened trait attacks a tenth faster.
 * The vanilla attack interval is a private field on MeleeAttackGoal, so this is the rank 4 replacement
 * the design calls for: the interval is derived from the vanilla one and no mixin is needed, because
 * getAttackInterval is protected. The factor is a constructor argument, so each caller owns its number.
 */
public class FasterMeleeGoal extends MeleeAttackGoal {
	/** The vanilla interval is twenty ticks; half again as fast is twenty divided by 1.5. */
	public static final double SPEED_FACTOR = 1.5;
	public static final int ATTACK_INTERVAL = (int) Math.round(20 / SPEED_FACTOR);
	/** The vanilla interval, which every factor is measured against. */
	public static final int VANILLA_INTERVAL = 20;

	private final double speedFactor;

	public FasterMeleeGoal(
		final PathfinderMob mob, final double speedModifier, final boolean followTargetEvenIfNotSeen,
		final double speedFactor
	) {
		super(mob, speedModifier, followTargetEvenIfNotSeen);
		this.speedFactor = speedFactor;
	}

	@Override
	protected int getAttackInterval() {
		return this.adjustedTickDelay(intervalFor(this.speedFactor));
	}

	/** The cadence a given factor asks for, so a test can compute the expected interval from the spec. */
	public static int intervalFor(final double speedFactor) {
		return (int) Math.round((double) VANILLA_INTERVAL / speedFactor);
	}

	/** Replaces the vanilla melee goal at its own rank, once, at the elite's own factor. */
	public static void install(final Mob mob) {
		install(mob, SPEED_FACTOR);
	}

	/** The same replacement at a named factor, so a trait's cadence is its own number. */
	public static void install(final Mob mob, final double speedFactor) {
		if (!(mob instanceof PathfinderMob pathfinder)) {
			return;
		}

		if (mob.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof FasterMeleeGoal)) {
			return;
		}

		mob.getGoalSelector().removeAllGoals(goal -> goal instanceof MeleeAttackGoal);
		mob.getGoalSelector().addGoal(
			BehaviorInstaller.MELEE_PRIORITY, new FasterMeleeGoal(pathfinder, 1.0, true, speedFactor)
		);
	}
}
