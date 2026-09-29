package com.stella.learn;

import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

/** Adds this mod's goals to every monster the mobs mod drives. */
public final class LearnGoals {
	/** Flagless goals only ask to run, so any rank works; the mobs mod's own ranks are 0 to 8. */
	static final int PRIORITY = 9;

	private LearnGoals() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> install(entity));
	}

	static void install(final Entity entity) {
		if (!(entity instanceof Monster monster) || monster.entityTags().contains(StellaMobs.NO_BEHAVIOR_TAG)) {
			return;
		}

		Mob mob = monster;
		mob.getGoalSelector().addGoal(PRIORITY, new LearnedBandGoal(mob));
	}
}
