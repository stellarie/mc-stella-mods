package com.stella.end;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The blinks of the castle mobs: the wither skeleton toward a far target, the archer away from a blow, each on its own cooldown. */
public class CastleTeleportGameTest {
	/** The goal of a type that a mob holds. The spawn path put it there, so a mob without it never got the behaviour. */
	static <T extends Goal> T goal(final Mob mob, final Class<T> type) {
		return mob.getGoalSelector().getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(type::isInstance).map(type::cast).findFirst()
			.orElseThrow(() -> new AssertionError(mob.getType().getDescriptionId() + " holds no " + type.getSimpleName()));
	}

	/**
	 * Whether a landing spot is a stand for the mover, judged by the blocks alone: its body fits, a whole block is under its
	 * middle, and the spot is inside the castle volume. A creature that walks in afterwards cannot change the answer.
	 */
	static boolean standable(final ServerLevel end, final Mob mover, final Vec3 at) {
		AABB body = mover.getBoundingBox().move(at.subtract(mover.position()));
		BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
		return end.noCollision(body) && end.getBlockState(under).isFaceSturdy(end, under, Direction.UP) && EndCastle.volumeContains(BlockPos.containing(at));
	}

	private static double flat(final Vec3 one, final Vec3 other) {
		return Math.hypot(one.x - other.x, one.z - other.z);
	}

	@GameTest(environment = "mod-stella-end-test:tele_chase", maxTicks = 140)
	public void aWitherSkeletonMoreThanEightBlocksFromItsTargetBlinksNextToItOnSafeGroundInsideTheCastle(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, floor);
		BlockPos at = EndTestSupport.cellNear(helper, plan, floor, plan.landingX(), plan.landingZ(), 12.0, 24.0);
		WitherSkeleton skeleton = (WitherSkeleton) EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, false);
		TeleportChaseGoal chase = goal(skeleton, TeleportChaseGoal.class);
		helper.onEachTick(() -> skeleton.setTarget(player));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!chase.hops().isEmpty(), "no blink yet; distance " + skeleton.distanceTo(player) + " alive=" + skeleton.isAlive()))
			.thenExecute(() -> {
				TeleportChaseGoal.Hop hop = chase.hops().get(0);
				double from = hop.from().distanceTo(player.position());
				double reach = flat(hop.to(), player.position());
				boolean safe = standable(end, skeleton, hop.to());
				boolean inside = EndCastle.volumeContains(BlockPos.containing(hop.to()));
				EndTestSupport.result("teleChase", "from=" + from + " landed=" + reach + " safe=" + safe + " inside=" + inside + " floor=" + EndCastle.floorAt(hop.to().y));
				EndTestSupport.fresh(helper);
				helper.assertTrue(from > TeleportChaseGoal.MIN_DISTANCE, "the skeleton blinked from only " + from + " blocks away");
				helper.assertTrue(reach >= CastleTeleport.NEAR_MIN - 1.0 && reach <= CastleTeleport.NEAR_MAX + 1.0, "the skeleton landed " + reach + " blocks from its target");
				helper.assertTrue(safe, "the landing spot " + hop.to() + " is not a safe stand");
				helper.assertTrue(inside, "the landing spot " + hop.to() + " lies outside the castle volume");
				helper.assertTrue(EndCastle.floorAt(hop.to().y) == floor, "the skeleton left floor " + floor);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:tele_near", maxTicks = 100)
	public void aWitherSkeletonWithinEightBlocksOfItsTargetDoesNotBlink(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, floor);
		BlockPos at = EndTestSupport.cellNear(helper, plan, floor, plan.landingX(), plan.landingZ(), 5.0, 7.0);
		WitherSkeleton skeleton = (WitherSkeleton) EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, false);
		TeleportChaseGoal chase = goal(skeleton, TeleportChaseGoal.class);
		double[] farthest = new double[1];
		helper.onEachTick(() -> {
			skeleton.setTarget(player);
			farthest[0] = Math.max(farthest[0], skeleton.distanceTo(player));
		});
		helper.startSequence()
			.thenIdle(80)
			.thenExecute(() -> {
				EndTestSupport.result("teleNear", "hops=" + chase.hops().size() + " farthest=" + farthest[0]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(farthest[0] <= TeleportChaseGoal.MIN_DISTANCE, "the skeleton strayed " + farthest[0] + " blocks from its target, so the test did not hold it near");
				helper.assertTrue(chase.hops().isEmpty(), "the skeleton blinked " + chase.hops().size() + " times from within reach");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:tele_cooldown", maxTicks = 220)
	public void aWitherSkeletonBlinksAgainOnlyOnceTheCooldownOfOneHundredSixtyTicksHasRunOnTheEndClock(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, floor);
		BlockPos at = EndTestSupport.cellNear(helper, plan, floor, plan.landingX(), plan.landingZ(), 18.0, 30.0);
		WitherSkeleton skeleton = (WitherSkeleton) EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, false);
		TeleportChaseGoal chase = goal(skeleton, TeleportChaseGoal.class);
		helper.onEachTick(() -> skeleton.setTarget(player));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(chase.hops().size() == 1, "no first blink yet"))
			.thenExecute(() -> skeleton.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))
			.thenIdle(30)
			.thenExecute(() -> helper.assertTrue(chase.hops().size() == 1, "the skeleton blinked again " + chase.hops().size() + " hops in, inside its cooldown"))
			.thenExecute(() -> EndClock.advance(end, TeleportChaseGoal.COOLDOWN))
			.thenWaitUntil(() -> helper.assertTrue(chase.hops().size() >= 2, "no second blink after the cooldown; distance " + skeleton.distanceTo(player)))
			.thenExecute(() -> {
				long gap = chase.hops().get(1).clock() - chase.hops().get(0).clock();
				EndTestSupport.result("teleCooldown", "hops=" + chase.hops().size() + " gap=" + gap + " cooldown=" + TeleportChaseGoal.COOLDOWN);
				EndTestSupport.fresh(helper);
				helper.assertTrue(gap >= TeleportChaseGoal.COOLDOWN, "the second blink came " + gap + " ticks after the first");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:tele_mounted", maxTicks = 140)
	public void aMountedWitherSkeletonBlinksWithItsHorseAndStaysInTheSaddle(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, floor);
		BlockPos at = EndTestSupport.cellNear(helper, plan, floor, plan.landingX(), plan.landingZ(), 12.0, 24.0);
		WitherSkeleton skeleton = (WitherSkeleton) EndMobs.spawn(end, floor, EndMobs.Kind.WITHER_SKELETON, at, true);
		helper.assertTrue(skeleton != null && skeleton.getVehicle() instanceof SkeletonHorse, "the skeleton does not ride a skeleton horse");
		SkeletonHorse horse = (SkeletonHorse) skeleton.getVehicle();
		TeleportChaseGoal chase = goal(skeleton, TeleportChaseGoal.class);
		helper.onEachTick(() -> skeleton.setTarget(player));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!chase.hops().isEmpty(), "no blink yet; distance " + skeleton.distanceTo(player)))
			.thenExecute(() -> {
				TeleportChaseGoal.Hop hop = chase.hops().get(0);
				boolean seated = skeleton.getVehicle() == horse;
				double apart = skeleton.distanceTo(horse);
				double landed = flat(horse.position(), hop.to());
				EndTestSupport.result("teleMounted", "seated=" + seated + " apart=" + apart + " horseFromLanding=" + landed);
				EndTestSupport.fresh(helper);
				helper.assertTrue(seated, "the skeleton fell from its horse in the blink");
				helper.assertTrue(apart < 2.5, "the skeleton and its horse stand " + apart + " blocks apart");
				helper.assertTrue(landed < CastleTeleport.NEAR_MAX + 2.0, "the horse did not make the blink: " + landed + " from the landing");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:tele_hurt", maxTicks = 260)
	public void aHurtSkeletonArcherBlinksAwayAndAgainOnlyOnceSixHundredTicksHaveRunOnTheEndClock(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int floor = 3;
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		EndTestSupport.joinOnFloor(helper, end, floor);
		BlockPos at = EndTestSupport.cellNear(helper, plan, floor, plan.landingX(), plan.landingZ(), 8.0, 16.0);
		Skeleton archer = (Skeleton) EndMobs.spawn(end, floor, EndMobs.Kind.ARCHER, at);
		HurtTeleportGoal escape = goal(archer, HurtTeleportGoal.class);
		helper.startSequence()
			.thenExecute(() -> archer.hurtServer(end, end.damageSources().generic(), 1.0F))
			.thenWaitUntil(() -> helper.assertTrue(escape.hops().size() == 1, "the hurt archer has not blinked; hurtTime " + archer.hurtTime))
			.thenExecute(() -> {
				TeleportChaseGoal.Hop hop = escape.hops().get(0);
				double moved = hop.from().distanceTo(hop.to());
				boolean safe = standable(end, archer, hop.to());
				boolean inside = EndCastle.volumeContains(BlockPos.containing(hop.to()));
				EndTestSupport.result("teleHurt", "moved=" + moved + " safe=" + safe + " inside=" + inside);
				helper.assertTrue(moved >= HurtTeleportGoal.MIN_DISTANCE, "the archer blinked only " + moved + " blocks");
				helper.assertTrue(safe, "the landing spot " + hop.to() + " is not a safe stand");
				helper.assertTrue(inside, "the landing spot " + hop.to() + " lies outside the castle volume");
				archer.damageCooldownTime = 0;
				archer.hurtServer(end, end.damageSources().generic(), 1.0F);
			})
			.thenIdle(30)
			.thenExecute(() -> helper.assertTrue(escape.hops().size() == 1, "the archer blinked again " + escape.hops().size() + " hops in, inside its cooldown"))
			.thenExecute(() -> {
				EndClock.advance(end, HurtTeleportGoal.COOLDOWN);
				archer.damageCooldownTime = 0;
				archer.hurtServer(end, end.damageSources().generic(), 1.0F);
			})
			.thenWaitUntil(() -> helper.assertTrue(escape.hops().size() >= 2, "no second blink after the cooldown"))
			.thenExecute(() -> {
				long gap = escape.hops().get(1).clock() - escape.hops().get(0).clock();
				EndTestSupport.result("teleHurtCooldown", "hops=" + escape.hops().size() + " gap=" + gap + " cooldown=" + HurtTeleportGoal.COOLDOWN);
				EndTestSupport.fresh(helper);
				helper.assertTrue(gap >= HurtTeleportGoal.COOLDOWN, "the second blink came " + gap + " ticks after the first");
			})
			.thenSucceed();
	}
}
