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
	/** Above the vanilla melee goals at rank 2, and level with the mob's own shield, which a tie never preempts. */
	static final int MELEE_WAIT_PRIORITY = 1;
	/** Below the mobs mod's distance goal at rank 2, so a retreat still wins, and above the bow goal at rank 4. */
	static final int RANGED_WAIT_PRIORITY = 3;

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
		mob.getGoalSelector().addGoal(MELEE_WAIT_PRIORITY, new WaitOutShieldGoal(mob, true));
		mob.getGoalSelector().addGoal(RANGED_WAIT_PRIORITY, new WaitOutShieldGoal(mob, false));
		mob.getGoalSelector().addGoal(MELEE_WAIT_PRIORITY, new AnswerRallyGoal(mob, true));
		mob.getGoalSelector().addGoal(MELEE_WAIT_PRIORITY, new TakeCoverGoal(mob));
		mob.getGoalSelector().addGoal(MELEE_WAIT_PRIORITY, new HoldAtChokeGoal(mob));
		mob.getGoalSelector().addGoal(MELEE_WAIT_PRIORITY, new InterceptGoal(mob));
		mob.getGoalSelector().addGoal(MELEE_WAIT_PRIORITY, new DodgeCritGoal(mob));
		mob.getGoalSelector().addGoal(RANGED_WAIT_PRIORITY, new AnswerRallyGoal(mob, false));
	}
}
