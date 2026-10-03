package com.stella.learn;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.StanceGoal;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mobs and players with a known stance. The mobs mod sets a stance from the health share of the mob and the health share
 * of the player it hunts. A case therefore fixes both: the mob is spawned at full health, and the player's share is held
 * on every tick, so no blow can move it. A healthy mob that hunts a healthy player is in FIGHT. A healthy mob that hunts
 * a player at 10 percent health is in PURSUE.
 *
 * <p>The mobs mod adds its goals when a mob joins the level. A mob that is spawned into a chunk that is not ready yet
 * joins later, so a goal change right after the spawn call can find no goal to change. The helpers that change the stance
 * goal therefore keep the mob without AI until it has the goal, and only then act on it.
 */
final class StanceFixtures {
	static final double HEALTHY = 1.0;
	/** Hurt enough that a healthy mob pursues. */
	static final double HURT = 0.10;

	private StanceFixtures() {
	}

	/**
	 * Sets the share at once, and again each tick.
	 * A hit would otherwise move the stance.
	 */
	static void holdHealth(final GameTestHelper helper, final ServerPlayer player, final double share) {
		player.setHealth((float) (share * player.getMaxHealth()));
		helper.onEachTick(() -> player.setHealth((float) (share * player.getMaxHealth())));
		// A hurt player left in the server draws mobs.
		helper.runBeforeTestEnd(() -> player.setHealth(player.getMaxHealth()));
	}

	/**
	 * A mob tagged out of level scaling and gear rolls.
	 * A far arena sets its level in the thousands.
	 * The tag goes on before the mob joins the level.
	 */
	static Mob unscaled(final GameTestHelper helper, final EntityType<? extends Mob> type, final int x, final int z) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.setPersistenceRequired();
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(x, LearnArena.SPAWN_Y, z)));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	/** A mob with no stance goal stays in NORMAL. Call it from the test body. */
	static void dropStance(final GameTestHelper helper, final Mob mob) {
		whenStanceGoalIsThere(helper, mob, "drop", goal -> mob.getGoalSelector().removeGoal(goal));
	}

	/**
	 * Reads the stance before the mob's first AI pass.
	 * Mod load order varies between runs.
	 * Learn's goals can then run before the stance goal.
	 * A wait could start before the stance is known.
	 * Call it from the test body.
	 */
	static void readStance(final GameTestHelper helper, final Mob mob, final ServerPlayer player) {
		whenStanceGoalIsThere(helper, mob, "read", goal -> {
			mob.setTarget(player);
			goal.canUse();
		});
	}

	/** Runs `action` on the mob's stance goal as soon as it exists. The mob has no AI until then. */
	private static void whenStanceGoalIsThere(final GameTestHelper helper, final Mob mob, final String what, final Consumer<StanceGoal<?>> action) {
		boolean hadNoAi = mob.isNoAi();
		mob.setNoAi(true);
		int[] waited = {0};
		boolean[] done = {false};
		Runnable attempt = () -> {
			if (done[0]) {
				return;
			}

			StanceGoal<?> goal = stanceGoalOf(mob);
			// A ticking mob has joined the level.
			// No goal by then means none will come.
			if (goal == null && !helper.getLevel().isPositionEntityTicking(mob.blockPosition())) {
				waited[0]++;
				return;
			}

			done[0] = true;
			if (goal != null) {
				action.accept(goal);
			}

			if (waited[0] > 0) {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=stanceFixture LATE what={} id={} found={} ticks={}", what, mob.getUUID(), goal != null, waited[0]);
			}

			mob.setNoAi(hadNoAi);
		};
		attempt.run();
		helper.onEachTick(attempt);
	}

	private static @Nullable StanceGoal<?> stanceGoalOf(final Mob mob) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof StanceGoal<?> goal) {
				return goal;
			}
		}

		return null;
	}
}
