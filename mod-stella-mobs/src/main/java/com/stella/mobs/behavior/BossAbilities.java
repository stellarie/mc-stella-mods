package com.stella.mobs.behavior;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;

/**
 * The boss signature abilities. Every path that makes a boss calls install, so a rolled boss and a boss
 * promoted by hand end in the same state, and a second call adds nothing.
 */
public final class BossAbilities {
	/** The shared rank for a goal that claims no flag, so it can never tie with another goal. */
	public static final int FLAGLESS_PRIORITY = 5;

	private BossAbilities() {
	}

	public static void install(final Mob mob) {
		if (!Bosses.isBoss(mob)) {
			return;
		}

		if (mob.getType() == EntityTypes.SKELETON && !alreadyInstalled(mob, BoneFanGoal.class)) {
			BoneFanGoal.install(mob);
		}

		if (mob instanceof Spider spider && !alreadyInstalled(mob, WebFieldGoal.class)) {
			mob.getGoalSelector().addGoal(FLAGLESS_PRIORITY, new WebFieldGoal(spider));
		}

		if (mob instanceof PiglinBrute brute && !alreadyInstalled(mob, WarlordRoarGoal.class)) {
			mob.getGoalSelector().addGoal(FLAGLESS_PRIORITY, new WarlordRoarGoal(brute));
		}

		if (mob instanceof Blaze blaze && !alreadyInstalled(mob, RingOfFireGoal.class)) {
			mob.getGoalSelector().addGoal(RingOfFireGoal.PRIORITY, new RingOfFireGoal(blaze));
		}

		if (mob instanceof WitherSkeleton skeleton && !alreadyInstalled(mob, DecayAuraGoal.class)) {
			mob.getGoalSelector().addGoal(FLAGLESS_PRIORITY, new DecayAuraGoal(skeleton));
		}
	}

	/** The ability goals this mob already carries, which is what makes a second install a no-op. */
	public static int copiesOf(final Mob mob, final Class<? extends Goal> type) {
		int copies = 0;

		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (type.isInstance(wrapped.getGoal())) {
				copies++;
			}
		}

		return copies;
	}

	private static boolean alreadyInstalled(final Mob mob, final Class<? extends Goal> type) {
		return copiesOf(mob, type) > 0;
	}
}
