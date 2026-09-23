package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * R22: the elite skeleton attacks half again as often. The vanilla attack interval is a private field
 * on MeleeAttackGoal, so this is the rank 4 replacement the design calls for: the interval is derived
 * from the vanilla one and no mixin is needed, because getAttackInterval is protected.
 */
public class FasterMeleeGoal extends MeleeAttackGoal {
	/** The vanilla interval is twenty ticks; half again as fast is twenty divided by 1.5. */
	public static final double SPEED_FACTOR = 1.5;
	public static final int ATTACK_INTERVAL = (int) Math.round(20 / SPEED_FACTOR);

	public FasterMeleeGoal(final PathfinderMob mob, final double speedModifier, final boolean followTargetEvenIfNotSeen) {
		super(mob, speedModifier, followTargetEvenIfNotSeen);
	}

	@Override
	protected int getAttackInterval() {
		return this.adjustedTickDelay(ATTACK_INTERVAL);
	}

	/** Replaces the vanilla melee goal at its own rank, once. */
	public static void install(final Mob mob) {
		if (!(mob instanceof PathfinderMob pathfinder)) {
			return;
		}

		if (mob.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof FasterMeleeGoal)) {
			return;
		}

		mob.getGoalSelector().removeAllGoals(goal -> goal instanceof MeleeAttackGoal);
		mob.getGoalSelector().addGoal(BehaviorInstaller.MELEE_PRIORITY, new FasterMeleeGoal(pathfinder, 1.0, true));
	}
}
