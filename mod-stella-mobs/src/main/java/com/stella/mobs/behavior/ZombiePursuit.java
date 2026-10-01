package com.stella.mobs.behavior;

import com.stella.mobs.mixin.MobTargetSelectorAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * A zombie keeps hunting a player that it cannot see. Vanilla stops the chase at the end of the path and drops the
 * target after 60 unseen ticks. This class gives every zombie class mob a melee goal that follows its target without
 * sight, and a target memory of {@link #UNSEEN_MEMORY_TICKS}. The follow range does not change, because a larger
 * range also widens how far a zombie picks a target.
 */
public final class ZombiePursuit {
	public static final int UNSEEN_MEMORY_TICKS = 600;

	// Its own type lets install find it again.
	private static final class PursuitAttackGoal extends ZombieAttackGoal {
		private PursuitAttackGoal(final Zombie zombie) {
			super(zombie, 1.0, true);
		}
	}

	private ZombiePursuit() {
	}

	// Call it from the installer only.
	// It removes a goal. Never call it in a callback.
	public static void install(final Mob mob) {
		if (!(mob instanceof Zombie zombie)) {
			return;
		}

		swapMelee(zombie);
		rememberUnseen(zombie);
	}

	private static void swapMelee(final Zombie zombie) {
		GoalSelector goals = zombie.getGoalSelector();
		WrappedGoal vanilla = null;
		for (WrappedGoal wrapped : goals.getAvailableGoals()) {
			Goal goal = wrapped.getGoal();
			// Elite zombies already follow unseen.
			if (goal instanceof FasterMeleeGoal || goal instanceof PursuitAttackGoal) {
				return;
			}

			// Exact class: a drowned keeps its own goal.
			if (goal.getClass() == ZombieAttackGoal.class) {
				vanilla = wrapped;
			}
		}

		if (vanilla != null) {
			int priority = vanilla.getPriority();
			goals.removeGoal(vanilla.getGoal());
			goals.addGoal(priority, new PursuitAttackGoal(zombie));
		}
	}

	private static void rememberUnseen(final Zombie zombie) {
		for (WrappedGoal wrapped : ((MobTargetSelectorAccessor) zombie).stellaMobs$targetSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof TargetGoal target) {
				target.setUnseenMemoryTicks(UNSEEN_MEMORY_TICKS);
			}
		}
	}
}
