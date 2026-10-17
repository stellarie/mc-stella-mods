package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Who the floors spawn and where: no witches, no endermen, archers on ledges, and one wither skeleton in ten on a skeleton horse. */
public class CastleMixGameTest {
	/** A mounted skeleton has closed in on its target when its horse is this near. */
	private static final double CLOSE = 4.5;

	@GameTest(environment = "mod-stella-end-test:mix_kinds", maxTicks = FloorSpawnerGameTest.FILL_TICKS + 200)
	public void noWitchAndNoEndermanSpawnInTheCastleWhileTheFloorsFill(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.joinOnFloor(helper, end, 3);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, 3).size() == FloorSpawner.CAP && FloorSpawner.hostiles(end, 2).size() == FloorSpawner.CAP,
				"floors 2 and 3 are not full yet"))
			.thenIdle(60)
			.thenExecute(() -> {
				int witches = 0;
				int endermen = 0;
				int skeletons = 0;
				int archers = 0;
				int shulkers = 0;
				int others = 0;
				for (Entity entity : EndTestSupport.entities(end)) {
					if (!(entity instanceof Mob mob) || !EndCastle.volumeContains(mob.blockPosition())) {
						continue;
					}
					witches += mob instanceof Witch ? 1 : 0;
					endermen += mob instanceof Enderman ? 1 : 0;
					if (EndMobs.isCastleMob(mob)) {
						skeletons += mob instanceof WitherSkeleton ? 1 : 0;
						archers += EndMobs.isArcher(mob) ? 1 : 0;
						shulkers += mob.getType() == EntityTypes.SHULKER ? 1 : 0;
						others += mob instanceof WitherSkeleton || EndMobs.isArcher(mob) || mob.getType() == EntityTypes.SHULKER ? 0 : 1;
					}
				}
				int kinds = EndMobs.Kind.values().length;
				EndTestSupport.result("mixKinds", "witches=" + witches + " endermen=" + endermen + " witherSkeletons=" + skeletons + " archers=" + archers + " shulkers=" + shulkers
					+ " others=" + others + " kinds=" + kinds);
				EndTestSupport.fresh(helper);
				helper.assertTrue(witches == 0, witches + " witches stand in the castle");
				helper.assertTrue(endermen == 0, endermen + " endermen stand in the castle");
				helper.assertTrue(others == 0, others + " castle mobs are neither wither skeletons, archers nor shulkers");
				helper.assertTrue(skeletons > 0 && archers > 0 && shulkers > 0, "the mix is missing a kind: " + skeletons + "/" + archers + "/" + shulkers);
				helper.assertTrue(kinds == 3, "the spawn kinds number " + kinds);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:mix_ledge_spawns", maxTicks = FloorSpawnerGameTest.FILL_TICKS + 200)
	public void everySkeletonArcherSpawnsOnAPlankOfALedgeOrMezzanineOfItsFloor(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.joinOnFloor(helper, end, 3);
		EndGameTestInit.SPAWNS.clear();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, 3).size() == FloorSpawner.CAP && FloorSpawner.hostiles(end, 2).size() == FloorSpawner.CAP
				&& FloorSpawner.hostiles(end, 4).size() == FloorSpawner.CAP, "floors 2 to 4 are not full yet"))
			.thenExecute(() -> {
				StringBuilder bad = new StringBuilder();
				int archers = 0;
				for (EndGameTestInit.Spawn spawn : EndGameTestInit.SPAWNS) {
					if (!spawn.archer()) {
						continue;
					}
					archers++;
					int floor = EndCastle.floorAt(spawn.at().getY());
					CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
					int lx = spawn.at().getX() - EndCastle.MIN_X;
					int lz = spawn.at().getZ() - EndCastle.MIN_Z;
					boolean onLedge = plan.ledges().stream().anyMatch(ledge -> {
						for (int i = 0; i < ledge.length(); i++) {
							if (ledge.x(i) == lx && ledge.z(i) == lz) {
								return true;
							}
						}
						return false;
					});
					boolean high = spawn.at().getY() == EndCastle.standY(floor) + CastleLayout.Ledge.RISE;
					boolean planks = end.getBlockState(spawn.at().below()).is(Blocks.SPRUCE_PLANKS);
					if (!onLedge || !high || !planks) {
						bad.append(" archer at ").append(spawn.at()).append(" onLedge=").append(onLedge).append(" high=").append(high).append(" planks=").append(planks);
					}
				}
				EndTestSupport.result("mixLedgeSpawns", "archers=" + archers + " spawns=" + EndGameTestInit.SPAWNS.size() + " bad=" + bad.length());
				EndTestSupport.fresh(helper);
				helper.assertTrue(archers >= 8, "only " + archers + " archers spawned to inspect");
				helper.assertTrue(bad.isEmpty(), "archers off the ledges:" + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:mix_ledge_plan", maxTicks = 40)
	public void everyFloorFromOneToFiveHoldsAtLeastTwoLedgesAgainstAWallForEverySeed(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		int fewest = Integer.MAX_VALUE;
		int seeds = 300;
		for (long seed = 0; seed < seeds; seed++) {
			CastleLayout layout = CastleLayout.generate(seed);
			for (int floor = 1; floor <= 5; floor++) {
				CastleLayout.FloorPlan plan = layout.floor(floor);
				fewest = Math.min(fewest, plan.ledges().size());
				if (plan.ledges().size() < CastleLayout.Ledge.WANTED) {
					bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" holds ").append(plan.ledges().size());
				}
				for (CastleLayout.Ledge ledge : plan.ledges()) {
					if (ledge.length() < CastleLayout.Ledge.SHORTEST || ledge.length() > CastleLayout.Ledge.LONGEST) {
						bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" ledge of ").append(ledge.length());
					}
					for (int i = 0; i <= ledge.length(); i++) {
						int bx = ledge.x(i) + ledge.toWall().getStepX();
						int bz = ledge.z(i) + ledge.toWall().getStepZ();
						if (plan.kind(ledge.x(i), ledge.z(i)) != CastleLayout.OPEN || plan.kind(bx, bz) != CastleLayout.WALL) {
							bad.append(" seed ").append(seed).append(" floor ").append(floor).append(" ledge cell ").append(i).append(" has no wall behind it");
						}
					}
				}
			}
		}
		EndTestSupport.result("mixLedgePlan", "seeds=" + seeds + " fewestOnAFloor=" + fewest + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "ledge plans off the rule:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:mix_mount_rate", maxTicks = 40)
	public void oneCastleWitherSkeletonInTenRidesAcrossAFixedCountOfSeededRolls(final GameTestHelper helper) {
		int rolls = 20000;
		RandomSource random = RandomSource.create(20260);
		int mounted = 0;
		for (int i = 0; i < rolls; i++) {
			mounted += EndMobs.rollMount(random) ? 1 : 0;
		}
		double p = EndMobs.MOUNT_SHARE / (double) EndMobs.ROLLS;
		double expected = rolls * p;
		double slack = 5.0 * Math.sqrt(rolls * p * (1.0 - p));
		EndTestSupport.result("mixMountRate", "rolls=" + rolls + " mounted=" + mounted + " expected=" + expected + " slack=" + slack);
		helper.assertTrue(Math.abs(mounted - expected) <= slack, mounted + " of " + rolls + " skeletons ride; expected " + expected + " give or take " + slack);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:mix_mount_spawn", maxTicks = 40)
	public void aMountedWitherSkeletonSitsOnATamedLevelNinetyFiveSkeletonHorseThatTheFloorCapDoesNotCount(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 4;
		int[] cell = EndCastles.layout(end).floor(floor).spawnCells().get(0);
		BlockPos at = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(floor), cell[1]);
		Mob rider = EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, true);
		helper.assertTrue(rider != null, "the mounted skeleton was refused");
		Entity seat = rider.getVehicle();
		boolean horse = seat instanceof SkeletonHorse;
		int level = horse ? LevelBridge.read(seat) : -1;
		boolean tamed = horse && ((SkeletonHorse) seat).isTamed();
		boolean tagged = seat != null && seat.entityTags().contains(EndMobs.MOUNT_TAG);
		int counted = FloorSpawner.hostiles(end, floor).size();
		EndTestSupport.result("mixMountSpawn", "horse=" + horse + " level=" + level + " tamed=" + tamed + " counted=" + counted);
		EndTestSupport.fresh(helper);
		helper.assertTrue(horse, "the skeleton rides " + seat);
		helper.assertTrue(level == EndLevels.MOUNT, "the horse is level " + level);
		helper.assertTrue(tamed, "the horse is wild");
		helper.assertTrue(tagged, "the horse carries no mount tag");
		helper.assertTrue(counted == 1, "the floor counts " + counted + " mobs for one rider on one horse");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:mix_mount_aura", maxTicks = 160)
	public void aPlayerWithinFiveBlocksOfTheHorseGetsSlownessOneAndOneBeyondDoesNot(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 4;
		int[] cell = EndCastles.layout(end).floor(floor).spawnCells().get(0);
		BlockPos at = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(floor), cell[1]);
		Mob rider = EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, true);
		helper.assertTrue(rider != null && rider.getVehicle() instanceof SkeletonHorse, "the mounted skeleton was refused");
		SkeletonHorse horse = (SkeletonHorse) rider.getVehicle();
		rider.setNoAi(true);
		Vec3 spot = horse.position();
		ServerPlayer near = EndTestSupport.join(helper, end, new Vec3(spot.x + MountGoals.Aura.RANGE - 0.5, spot.y, spot.z));
		ServerPlayer far = EndTestSupport.join(helper, end, new Vec3(spot.x - MountGoals.Aura.RANGE - 0.5, spot.y, spot.z));
		List<Double> apart = new ArrayList<>();
		helper.onEachTick(() -> {
			horse.getNavigation().stop();
			horse.setPos(spot);
			horse.setDeltaMovement(Vec3.ZERO);
			apart.clear();
			apart.add((double) near.distanceTo(horse));
			apart.add((double) far.distanceTo(horse));
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(near.hasEffect(MobEffects.SLOWNESS), "the near player is not slowed; distance " + apart))
			.thenIdle(30)
			.thenExecute(() -> {
				int amplifier = near.getEffect(MobEffects.SLOWNESS).getAmplifier();
				boolean farSlowed = far.hasEffect(MobEffects.SLOWNESS);
				EndTestSupport.result("mixMountAura", "nearAmplifier=" + amplifier + " farSlowed=" + farSlowed + " distances=" + apart);
				EndTestSupport.fresh(helper);
				helper.assertTrue(amplifier == 0, "the slowness is level " + (amplifier + 1));
				helper.assertTrue(!farSlowed, "the player beyond " + MountGoals.Aura.RANGE + " blocks is slowed, at " + apart);
			})
			.thenSucceed();
	}
	@GameTest(environment = "mod-stella-end-test:mix_mount_charge", maxTicks = 200)
	public void aMountedWitherSkeletonRidesTowardItsTargetWithinEightBlocksWithoutBlinking(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, floor);
		BlockPos at = EndTestSupport.cellNear(helper, plan, floor, plan.landingX(), plan.landingZ(), 6.0, 7.5);
		Mob rider = EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, true);
		helper.assertTrue(rider != null && rider.getVehicle() instanceof SkeletonHorse, "the mounted skeleton was refused");
		SkeletonHorse horse = (SkeletonHorse) rider.getVehicle();
		TeleportChaseGoal chase = CastleTeleportGameTest.goal(rider, TeleportChaseGoal.class);
		double start = horse.distanceTo(player);
		helper.onEachTick(() -> rider.setTarget(player));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(horse.distanceTo(player) <= CLOSE, "the horse is still " + horse.distanceTo(player) + " blocks from the target"))
			.thenExecute(() -> {
				EndTestSupport.result("mixMountCharge", "start=" + start + " end=" + horse.distanceTo(player) + " hops=" + chase.hops().size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(start > CLOSE && start <= TeleportChaseGoal.MIN_DISTANCE, "the test began " + start + " blocks away");
				helper.assertTrue(chase.hops().isEmpty(), "the rider blinked instead of riding");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:mix_mount_orphan", maxTicks = 260)
	public void aSkeletonHorseLeftWithoutARiderForTwoHundredTicksIsGone(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 4;
		int[] cell = EndCastles.layout(end).floor(floor).spawnCells().get(0);
		BlockPos at = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(floor), cell[1]);
		Mob rider = EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, true);
		helper.assertTrue(rider != null && rider.getVehicle() instanceof SkeletonHorse, "the mounted skeleton was refused");
		SkeletonHorse horse = (SkeletonHorse) rider.getVehicle();
		helper.startSequence()
			.thenExecute(() -> rider.discard())
			.thenIdle(MountGoals.Aura.ORPHAN_TICKS / 2)
			.thenExecute(() -> helper.assertTrue(horse.isAlive() && !horse.isRemoved(), "the horse went after only half the wait"))
			.thenWaitUntil(() -> helper.assertTrue(horse.isRemoved(), "the horse is still standing"))
			.thenExecute(() -> {
				EndTestSupport.result("mixMountOrphan", "gone=" + horse.isRemoved());
				EndTestSupport.fresh(helper);
			})
			.thenSucceed();
	}
}
