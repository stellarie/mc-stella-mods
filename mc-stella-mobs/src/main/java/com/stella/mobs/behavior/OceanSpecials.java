package com.stella.mobs.behavior;

import java.util.List;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.squid.GlowSquid;

/**
 * P3 and P4: the one non-Monster install path. Neither a glow squid nor a pufferfish is an Enemy, so
 * the shared elite roll can never mark one, and neither is a Monster, so the behavior installer
 * returns before it. The roll, the mark and the goal add all happen here, keyed on the same config
 * chance every other mob uses.
 */
public final class OceanSpecials {
	private OceanSpecials() {
	}

	/** The elite roll for the two ocean mobs the shared path cannot reach. Idempotent. */
	public static void roll(final Mob mob, final double chance) {
		if (!(mob instanceof GlowSquid) && !(mob instanceof Pufferfish)) {
			return;
		}

		if (EliteState.isSummoned(mob) || Elite.isElite(mob) || !Elite.roll(mob.getRandom(), chance)) {
			return;
		}

		Elite.mark(mob, Elite.pickTier(mob.getRandom()), Elite.HEALTH_MULTIPLIER);
		install(mob);
	}

	/** Idempotent: one goal per mob, so a reload or a hand mark cannot stack a second. */
	public static void install(final Mob mob) {
		if (mob instanceof GlowSquid squid) {
			if (!has(squid, EliteAuraGoal.class)) {
				squid.getGoalSelector().addGoal(
					BehaviorInstaller.OCEAN_AURA_PRIORITY,
					new EliteAuraGoal<>(squid, EliteAuraGoal.RADIUS, List.of(MobEffects.BLINDNESS))
				);
			}
		} else if (mob instanceof Pufferfish puffer) {
			if (!has(puffer, PufferfishMineGoal.class)) {
				puffer.getGoalSelector().addGoal(PufferfishMineGoal.PRIORITY, new PufferfishMineGoal(puffer));
			}
		}
	}

	private static boolean has(final Mob mob, final Class<? extends Goal> type) {
		return mob.getGoalSelector().getAvailableGoals().stream()
			.anyMatch(wrapped -> type.isInstance(wrapped.getGoal()));
	}
}
