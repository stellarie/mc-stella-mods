package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** The floor spawner: it fills a floor with a player near, stops at the cap, refills, and leaves other floors alone. */
public class FloorSpawnerGameTest {
	/** Ticks to reach the cap: a top-up every {@link FloorSpawner#INTERVAL} adds {@link FloorSpawner#BATCH}, plus slack. */
	static final int FILL_TICKS = (FloorSpawner.CAP / FloorSpawner.BATCH + 4) * FloorSpawner.INTERVAL;

	/** Live entities of the End carrying a floor's tag, counted from the level and not from the spawner's record. */
	static List<Mob> inWorld(final ServerLevel end, final int floor) {
		List<Mob> found = new ArrayList<>();
		end.getAllEntities().forEach(entity -> {
			if (entity instanceof Mob mob && mob.isAlive() && mob.entityTags().contains(EndMobs.FLOOR_TAG + floor)) {
				found.add(mob);
			}
		});
		return found;
	}

	@GameTest(environment = "mod-stella-end-test:spawner_levels_formula", maxTicks = 20)
	public void floorLevelsRunFromOneHundredInStepsOfThree(final GameTestHelper helper) {
		int[] plan = {100, 103, 106, 109, 112, 115};
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			if (EndLevels.floorLevel(floor) != plan[floor - 1]) {
				bad.append(" floor ").append(floor).append(" is ").append(EndLevels.floorLevel(floor));
			}
		}
		EndTestSupport.result("spawnerLevelsFormula", "floor1=" + EndLevels.floorLevel(1) + " floor6=" + EndLevels.floorLevel(6));
		helper.assertTrue(bad.isEmpty(), "levels off the plan:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:spawner_cap", maxTicks = FILL_TICKS + 400)
	public void aFloorWithAPlayerTopsUpToTwentyFourAndNeverMore(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, floor);
		int[] peak = new int[EndCastle.FLOORS + 1];
		helper.onEachTick(() -> {
			for (int f = 1; f <= EndCastle.FLOORS; f++) {
				peak[f] = Math.max(peak[f], Math.max(FloorSpawner.hostiles(end, f).size(), inWorld(end, f).size()));
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, floor).size() == FloorSpawner.CAP,
				"floor " + floor + " holds " + FloorSpawner.hostiles(end, floor).size() + " of " + FloorSpawner.CAP))
			.thenIdle(300)
			.thenExecute(() -> {
				int recorded = FloorSpawner.hostiles(end, floor).size();
				int physical = inWorld(end, floor).size();
				StringBuilder over = new StringBuilder();
				for (int f = 1; f <= EndCastle.FLOORS; f++) {
					if (peak[f] > FloorSpawner.CAP) {
						over.append(" floor ").append(f).append(" peaked at ").append(peak[f]);
					}
				}
				EndTestSupport.result("spawnerCap", "recorded=" + recorded + " physical=" + physical + " peaks=" + java.util.Arrays.toString(peak));
				EndTestSupport.fresh(helper);
				helper.assertTrue(recorded == FloorSpawner.CAP, "the record holds " + recorded);
				helper.assertTrue(physical == recorded, "the level holds " + physical + " castle mobs on floor " + floor + " but the record holds " + recorded);
				helper.assertTrue(over.isEmpty(), "floors over the cap:" + over);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:spawner_refill", maxTicks = 2 * FILL_TICKS + 200)
	public void aKilledMobIsReplacedSoTheFloorStaysFull(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 2;
		EndTestSupport.joinOnFloor(helper, end, floor);
		int[] peak = new int[1];
		helper.onEachTick(() -> peak[0] = Math.max(peak[0], FloorSpawner.hostiles(end, floor).size()));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, floor).size() == FloorSpawner.CAP, "floor " + floor + " is not full yet"))
			.thenExecute(() -> {
				List<Mob> victims = FloorSpawner.hostiles(end, floor).subList(0, 10);
				for (Mob victim : List.copyOf(victims)) {
					victim.discard();
				}
				helper.assertTrue(FloorSpawner.hostiles(end, floor).size() <= FloorSpawner.CAP - 10, "the record kept the dead");
			})
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, floor).size() == FloorSpawner.CAP, "floor " + floor + " holds "
				+ FloorSpawner.hostiles(end, floor).size() + " after the kills"))
			.thenExecute(() -> {
				EndTestSupport.result("spawnerRefill", "peak=" + peak[0]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(peak[0] <= FloorSpawner.CAP, "the floor peaked at " + peak[0]);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:spawner_far", maxTicks = FILL_TICKS)
	public void noFloorFillsWhileNoPlayerIsNearIt(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer faraway = EndTestSupport.join(helper, end, new Vec3(600.5, 90.0, 600.5));
		helper.startSequence()
			.thenIdle(FILL_TICKS - 40)
			.thenExecute(() -> {
				int total = 0;
				for (int f = 1; f <= EndCastle.FLOORS; f++) {
					total += FloorSpawner.hostiles(end, f).size() + inWorld(end, f).size();
				}
				EndTestSupport.result("spawnerFar", "mobs=" + total + " playerAt=" + faraway.blockPosition());
				EndTestSupport.fresh(helper);
				helper.assertTrue(total == 0, total + " castle mobs spawned with no player near");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:spawner_floor_six", maxTicks = FILL_TICKS + 100)
	public void floorSixHasNoSpawnerButTheFloorsBelowItFill(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.joinOnFloor(helper, end, EndCastle.FLOORS);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, 5).size() == FloorSpawner.CAP, "floor 5 is not full yet"))
			.thenExecute(() -> {
				int six = FloorSpawner.hostiles(end, EndCastle.FLOORS).size() + inWorld(end, EndCastle.FLOORS).size();
				EndTestSupport.result("spawnerFloorSix", "floorSixMobs=" + six + " floorFive=" + FloorSpawner.hostiles(end, 5).size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(six == 0, "floor 6 holds " + six + " castle mobs");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:spawner_spots", maxTicks = FILL_TICKS + 100)
	public void everySpawnStandsInARoomAndAtLeastTenBlocksFromEveryPlayer(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 4;
		EndTestSupport.joinOnFloor(helper, end, floor);
		EndGameTestInit.SPAWNS.clear();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, floor).size() == FloorSpawner.CAP, "floor " + floor + " is not full yet"))
			.thenExecute(() -> {
				StringBuilder bad = new StringBuilder();
				CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
				double nearest = Double.MAX_VALUE;
				int onFloor = 0;
				for (EndGameTestInit.Spawn spawn : EndGameTestInit.SPAWNS) {
					if (EndCastle.floorAt(spawn.at().getY()) != floor) {
						continue;
					}
					onFloor++;
					int lx = spawn.at().getX() - EndCastle.MIN_X;
					int lz = spawn.at().getZ() - EndCastle.MIN_Z;
					boolean inRoom = lx >= 0 && lz >= 0 && lx < EndCastle.SIZE && lz < EndCastle.SIZE && plan.room(lx, lz) > 0;
					if (!inRoom) {
						bad.append(" a mob loaded outside every room at ").append(spawn.at());
					}
					nearest = Math.min(nearest, spawn.nearestPlayer());
				}
				EndTestSupport.result("spawnerSpots", "spawned=" + onFloor + " nearestAtSpawn=" + nearest + " bad=" + bad.length());
				EndTestSupport.fresh(helper);
				helper.assertTrue(bad.isEmpty(), "spawns outside rooms:" + bad);
				helper.assertTrue(onFloor >= FloorSpawner.CAP, "only " + onFloor + " spawns were seen");
				helper.assertTrue(nearest >= FloorSpawner.SAFE_DISTANCE, "a mob spawned " + nearest + " blocks from a player");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:spawner_levels", maxTicks = FILL_TICKS + 100)
	public void everyCastleMobHasLevelOneHundredOrMoreAndItsFloorsLevelOrTheArchersFixedOne(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.joinOnFloor(helper, end, 3);
		helper.startSequence()
			.thenWaitUntil(() -> {
				for (int f = 1; f <= 5; f++) {
					helper.assertTrue(FloorSpawner.hostiles(end, f).size() == FloorSpawner.CAP, "floor " + f + " is not full yet");
				}
			})
			.thenExecute(() -> {
				StringBuilder bad = new StringBuilder();
				int checked = 0;
				int lowest = Integer.MAX_VALUE;
				for (int f = 1; f <= 5; f++) {
					for (Mob mob : FloorSpawner.hostiles(end, f)) {
						int level = LevelBridge.read(mob);
						int expected = EndMobs.isArcher(mob) ? EndLevels.ARCHER : EndLevels.floorLevel(f);
						checked++;
						lowest = Math.min(lowest, level);
						if (level < 100 || level != expected) {
							bad.append(" floor ").append(f).append(' ').append(mob.getType().getDescriptionId()).append(" level ").append(level).append(" expected ").append(expected);
						}
					}
				}
				EndTestSupport.result("spawnerLevels", "checked=" + checked + " lowest=" + lowest + " bad=" + bad.length());
				EndTestSupport.fresh(helper);
				helper.assertTrue(bad.isEmpty(), "mobs off their floor's level:" + bad);
			})
			.thenSucceed();
	}
}
