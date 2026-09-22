package com.stella.mobs.behavior;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

public final class BehaviorInstaller {
	public static final int SHIELD_PRIORITY = 1;
	public static final int RETREAT_PRIORITY = 2;
	public static final int DEFEND_PRIORITY = 3;

	private BehaviorInstaller() {
	}

	public static void install(final Mob mob) {
		if (!(mob instanceof Monster monster)) {
			return;
		}

		ReactiveState state = ReactiveState.get(mob);
		if (state.isInstalled()) {
			return;
		}

		state.setInstalled(true);

		mob.getGoalSelector().addGoal(RETREAT_PRIORITY, new RetreatGoal<>(monster));
		mob.getGoalSelector().addGoal(DEFEND_PRIORITY, new DefendAllyGoal<>(monster));

		if (mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			mob.getGoalSelector().addGoal(SHIELD_PRIORITY, new ShieldUseGoal<>(monster));
		}
	}
}
