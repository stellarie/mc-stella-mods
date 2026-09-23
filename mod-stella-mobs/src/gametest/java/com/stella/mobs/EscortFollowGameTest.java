package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.EscortSquad;
import com.stella.mobs.behavior.FollowAllyGoal;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/**
 * The escort's follow goal. A vanilla FollowMobGoal on the escort's own selector threw for an
 * amphibious or water bound escort and took the world tick with it, and it drove the elite rather than
 * the escort because its predicate excludes the mob's own class. These tests pin both, with a Drowned
 * for the crash that happened and a Guardian for the one that was armed.
 */
public class EscortFollowGameTest {
	private static final String TEST = "escortFollow";
	private static final String QUIET = """
		{
			"entities": {},
			"rolls": {
				"roll_chance": 0.0,
				"elite_chance": 0.0,
				"boss_chance": 0.0
			}
		}""";

	@GameTest(maxTicks = 200)
	public void aDrownedEliteIsEscortedAndTheWorldSurvives(final GameTestHelper helper) {
		escortCase(helper, EntityTypes.DROWNED, "drownedEscort");
	}

	@GameTest(maxTicks = 200)
	public void aGuardianEliteIsEscortedAndTheWorldSurvives(final GameTestHelper helper) {
		escortCase(helper, EntityTypes.GUARDIAN, "guardianEscort");
	}

	/** The escorts of an elite of the given kind arrive, summoned, not elite, and the tick survives. */
	private static void escortCase(
		final GameTestHelper helper, final EntityType<? extends Mob> type, final String name
	) {
		Mob elite = elite(helper, type);

		helper.succeedWhen(() -> {
			List<Mob> escorts = escortsOf(helper, elite);
			int summoned = 0;
			int eliteCount = 0;

			for (Mob escort : escorts) {
				if (EliteState.isSummoned(escort)) {
					summoned++;
				}

				if (Elite.isElite(escort)) {
					eliteCount++;
				}
			}

			helper.assertValueEqual(
				Integer.valueOf(escorts.size()), Integer.valueOf(EscortSquad.ESCORT_COUNT), "escorts of a " + type
			);
			helper.assertValueEqual(
				Integer.valueOf(summoned), Integer.valueOf(EscortSquad.ESCORT_COUNT), "summoned escorts"
			);
			helper.assertValueEqual(Integer.valueOf(eliteCount), Integer.valueOf(0), "escorts that rolled elite");
			result(name, "escorts=" + escorts.size() + " summoned=" + summoned + " elite=" + eliteCount + " type=" + type);
		});
	}

	@GameTest(maxTicks = 400)
	public void escortsFollowTheirOwnEliteAndNothingElse(final GameTestHelper helper) {
		Mob elite = elite(helper, EntityTypes.ZOMBIE);
		List<Mob> moved = new ArrayList<>();
		double[] before = new double[1];

		helper.startSequence()
			.thenWaitUntil(() -> helper.assertValueEqual(
				Integer.valueOf(escortsOf(helper, elite).size()), Integer.valueOf(EscortSquad.ESCORT_COUNT),
				"escorts before the walk"
			))
			.thenExecute(() -> {
				// One escort is put across the arena, further than its stop distance from the elite.
				Mob walker = escortsOf(helper, elite).getFirst();
				walker.setPos(elite.getX() + 6.0, elite.getY() + 2.0, elite.getZ() + 6.0);
				moved.add(walker);
				before[0] = walker.distanceTo(elite);
			})
			.thenWaitUntil(() -> {
				List<Mob> escorts = escortsOf(helper, elite);
				int vanilla = 0;
				int following = 0;
				int foreign = 0;

				for (Mob escort : escorts) {
					for (WrappedGoal wrapped : escort.getGoalSelector().getAvailableGoals()) {
						Goal goal = wrapped.getGoal();

						if (goal instanceof FollowMobGoal) {
							vanilla++;
						}

						if (goal instanceof FollowAllyGoal ally) {
							following++;

							if (ally.leader() != elite) {
								foreign++;
							}
						}
					}
				}

				double after = moved.getFirst().distanceTo(elite);
				helper.assertValueEqual(Integer.valueOf(vanilla), Integer.valueOf(0), "vanilla follow goals on an escort");
				helper.assertValueEqual(
					Integer.valueOf(following), Integer.valueOf(EscortSquad.ESCORT_COUNT), "escorts carrying the mod's goal"
				);
				helper.assertValueEqual(Integer.valueOf(foreign), Integer.valueOf(0), "follow goals bound to another mob");
				helper.assertTrue(
					after < before[0] - 1.0,
					"the escort did not close on its elite: " + before[0] + " blocks, then " + after
				);
				result(
					"escortFollow",
					"escorts=" + escorts.size() + " vanillaFollowGoals=" + vanilla + " modFollowGoals=" + following
						+ " foreignBound=" + foreign + " distanceBefore=" + Math.round(before[0]) + " distanceAfter="
						+ Math.round(after)
				);
			})
			.thenSucceed();
	}

	private static Mob elite(final GameTestHelper helper, final EntityType<? extends Mob> type) {
		GearConfigStore.set(GearConfigParser.parse(QUIET));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
		Mob mob = helper.spawnMob(type, 1, 3, 1).spawnReason(EntitySpawnReason.EVENT).spawn();
		mob.setPersistenceRequired();
		Elite.mark(mob, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		EscortSquad.queue(mob, GearTier.DIAMOND);
		return mob;
	}

	/** The escorts of one elite: its own kind, and flagged summoned by the squad recipe. */
	private static List<Mob> escortsOf(final GameTestHelper helper, final Mob elite) {
		return helper.getLevel().getEntitiesOfClass(
			Mob.class, elite.getBoundingBox().inflate(16.0),
			candidate -> candidate != elite && candidate.getType() == elite.getType() && EliteState.isSummoned(candidate)
		);
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {}", TEST, test, detail);
	}
}
