package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.BlazeAttackGoal;
import com.stella.mobs.behavior.HoldStandoffGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.ShieldUseGoal;
import com.stella.mobs.projectile.StellaSmallFireball;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * A blaze ball splits in three at the middle of its flight. The children fan out twelve degrees around the
 * heading of the ball, carry its owner and its explosion, never split again, and never strike each other or
 * their owner. A ball that hits something first explodes as it always did.
 */
public class BlazeSplitGameTest {
	private static final String TEST = "blazeSplit";
	private static final double OVERSHOOT_BOUND = 1.5;
	private static final double ANGLE_TOLERANCE_DEGREES = 0.5;
	private static final float CUSTOM_RADIUS = 2.0F;
	/** The splits that happened, keyed by the parent ball. */
	private static final Map<UUID, Split> SPLITS = new ConcurrentHashMap<>();
	/** Explosion hits a blaze's ball dealt, keyed by the victim. */
	private static final Map<UUID, List<UUID>> BLASTED = new ConcurrentHashMap<>();

	private record Split(StellaSmallFireball parent, Vec3 at, Vec3 heading, List<StellaSmallFireball> children, List<Vec3> childHeadings) {
	}

	static {
		StellaSmallFireball.setRecorder(new StellaSmallFireball.Recorder() {
			@Override
			public void split(final StellaSmallFireball parent, final Vec3 heading, final List<StellaSmallFireball> children) {
				List<Vec3> headings = new ArrayList<>();
				for (StellaSmallFireball child : children) {
					headings.add(child.getDeltaMovement().normalize());
				}
				SPLITS.put(parent.getUUID(), new Split(parent, parent.position(), heading, List.copyOf(children), headings));
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
			if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() instanceof Blaze blaze) {
				BLASTED.computeIfAbsent(victim.getUUID(), key -> new CopyOnWriteArrayList<>()).add(blaze.getUUID());
			}
		});
	}

	@GameTest
	public void theSplitIsThreeChildrenTwelveDegreesApart(final GameTestHelper helper) {
		helper.assertTrue(StellaSmallFireball.SPLIT_COUNT == 3, "a ball splits into " + StellaSmallFireball.SPLIT_COUNT + ", not 3");
		helper.assertTrue(StellaSmallFireball.SPLIT_SPREAD_DEGREES == 12.0, "the children spread " + StellaSmallFireball.SPLIT_SPREAD_DEGREES + " degrees, not 12");
		helper.assertTrue(BlazeAttackGoal.SPLIT_AT_FRACTION == 0.5, "a ball splits at " + BlazeAttackGoal.SPLIT_AT_FRACTION + " of its flight, not the middle");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aBallSplitsIntoThreeChildrenAndLeavesNoParent(final GameTestHelper helper) {
		StellaSmallFireball ball = launch(helper, 3.0, new Vec3(3.5, 3.0, 0.5));
		helper.succeedWhen(() -> {
			Split split = SPLITS.get(ball.getUUID());
			helper.assertTrue(split != null, "the ball has not split yet");
			helper.assertTrue(split.children().size() == StellaSmallFireball.SPLIT_COUNT, "the ball split into " + split.children().size() + " children");
			helper.assertTrue(ball.isRemoved(), "the parent ball still flies after it split");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aBallSplitsIntoThreeChildrenAndLeavesNoParent children={}", TEST, split.children().size());
			split.children().forEach(StellaSmallFireball::discard);
		});
	}

	@GameTest(maxTicks = 80)
	public void aBallSplitsAtItsSplitDistance(final GameTestHelper helper) {
		double distance = 3.0;
		Vec3 start = helper.absoluteVec(new Vec3(3.5, 3.0, 0.5));
		StellaSmallFireball ball = launch(helper, distance, new Vec3(3.5, 3.0, 0.5));
		helper.succeedWhen(() -> {
			Split split = SPLITS.get(ball.getUUID());
			helper.assertTrue(split != null, "the ball has not split yet");
			double travelled = split.at().distanceTo(start);
			helper.assertTrue(travelled >= distance && travelled <= distance + OVERSHOOT_BOUND, "the ball split after " + travelled + " blocks, not near " + distance);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aBallSplitsAtItsSplitDistance distance={} travelled={}", TEST, distance, travelled);
			split.children().forEach(StellaSmallFireball::discard);
		});
	}

	@GameTest(maxTicks = 80)
	public void theChildrenFanOutTwelveDegreesAroundTheHeading(final GameTestHelper helper) {
		StellaSmallFireball ball = launch(helper, 3.0, new Vec3(3.5, 3.0, 0.5));
		helper.succeedWhen(() -> {
			Split split = SPLITS.get(ball.getUUID());
			helper.assertTrue(split != null, "the ball has not split yet");
			List<Double> angles = new ArrayList<>();
			for (Vec3 heading : split.childHeadings()) {
				angles.add(Math.toDegrees(Math.acos(Math.min(1.0, heading.dot(split.heading())))));
			}
			angles.sort(Comparator.naturalOrder());
			helper.assertTrue(Math.abs(angles.get(0)) <= ANGLE_TOLERANCE_DEGREES, "no child keeps the heading: angles " + angles);
			helper.assertTrue(
				Math.abs(angles.get(1) - StellaSmallFireball.SPLIT_SPREAD_DEGREES) <= ANGLE_TOLERANCE_DEGREES
					&& Math.abs(angles.get(2) - StellaSmallFireball.SPLIT_SPREAD_DEGREES) <= ANGLE_TOLERANCE_DEGREES,
				"the side children are not " + StellaSmallFireball.SPLIT_SPREAD_DEGREES + " degrees off: angles " + angles
			);
			double sideDot = Double.MAX_VALUE;
			for (int i = 0; i < split.childHeadings().size(); i++) {
				for (int j = i + 1; j < split.childHeadings().size(); j++) {
					sideDot = Math.min(sideDot, split.childHeadings().get(i).dot(split.childHeadings().get(j)));
				}
			}
			helper.assertTrue(sideDot < 0.999, "two children fly the same way: dot " + sideDot);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=theChildrenFanOutTwelveDegreesAroundTheHeading angles={}", TEST, angles);
			split.children().forEach(StellaSmallFireball::discard);
		});
	}

	@GameTest(maxTicks = 80)
	public void theChildrenCarryTheOwnerAndTheExplosion(final GameTestHelper helper) {
		Blaze owner = owner(helper, 6.5);
		StellaSmallFireball ball = launch(helper, 3.0, new Vec3(3.5, 3.0, 0.5));
		ball.setOwner(owner);
		ball.setExplosionRadius(CUSTOM_RADIUS);
		helper.succeedWhen(() -> {
			Split split = SPLITS.get(ball.getUUID());
			helper.assertTrue(split != null, "the ball has not split yet");
			for (StellaSmallFireball child : split.children()) {
				helper.assertTrue(child.getOwner() == owner, "a child lost its owner");
				helper.assertTrue(child.getExplosionRadius() == CUSTOM_RADIUS, "a child explodes with radius " + child.getExplosionRadius() + ", not " + CUSTOM_RADIUS);
				helper.assertTrue(child.isSplitChild() && child.getSplitDistance() == 0.0, "a child is not marked as a child that never splits");
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=theChildrenCarryTheOwnerAndTheExplosion radius={}", TEST, CUSTOM_RADIUS);
			split.children().forEach(StellaSmallFireball::discard);
			owner.discard();
		});
	}

	@GameTest(maxTicks = 80)
	public void aChildNeverSplitsAgain(final GameTestHelper helper) {
		StellaSmallFireball ball = launch(helper, 1.5, new Vec3(3.5, 3.0, 0.5));
		helper.runAfterDelay(40L, () -> {
			Split split = SPLITS.get(ball.getUUID());
			helper.assertTrue(split != null, "the ball never split, so the case proves nothing");
			for (StellaSmallFireball child : split.children()) {
				helper.assertTrue(!SPLITS.containsKey(child.getUUID()), "a child split again");
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aChildNeverSplitsAgain splits={}", TEST, SPLITS.size());
			split.children().forEach(StellaSmallFireball::discard);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void theChildrenSpareEachOther(final GameTestHelper helper) {
		StellaSmallFireball ball = launch(helper, 0.0, new Vec3(3.5, 3.0, 0.5));
		List<StellaSmallFireball> children = ball.splitNow();
		helper.assertTrue(children.size() == StellaSmallFireball.SPLIT_COUNT, "the ball split into " + children.size() + " children");
		// A fireball that meets another is turned aside rather than burst, so the heading shows a meeting as well as a loss.
		List<Vec3> headings = children.stream().map(child -> child.getDeltaMovement().normalize()).toList();
		helper.runAfterDelay(3L, () -> {
			long flying = children.stream().filter(child -> !child.isRemoved()).count();
			double leastAlignment = 1.0;
			for (int i = 0; i < children.size(); i++) {
				leastAlignment = Math.min(leastAlignment, children.get(i).getDeltaMovement().normalize().dot(headings.get(i)));
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=theChildrenSpareEachOther flying={} leastAlignment={}", TEST, flying, leastAlignment);
			children.forEach(StellaSmallFireball::discard);
			helper.assertTrue(flying == children.size(), "only " + flying + " of " + children.size() + " children flew on, so they struck each other");
			helper.assertTrue(leastAlignment > 0.999, "a child turned from its heading, so it met a sibling: alignment " + leastAlignment);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void theChildrenFlyThroughTheirOwner(final GameTestHelper helper) {
		Blaze owner = owner(helper, 3.5);
		StellaSmallFireball ball = launch(helper, 0.0, new Vec3(3.5, 3.0, 1.5));
		ball.setOwner(owner);
		List<StellaSmallFireball> children = ball.splitNow();
		double[] farthest = {Double.NEGATIVE_INFINITY};
		helper.onEachTick(() -> children.stream().filter(child -> !child.isRemoved()).forEach(child -> farthest[0] = Math.max(farthest[0], child.getZ())));
		helper.runAfterDelay(14L, () -> {
			double pastOwner = farthest[0] - owner.getZ();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=theChildrenFlyThroughTheirOwner pastOwner={}", TEST, pastOwner);
			children.forEach(StellaSmallFireball::discard);
			owner.discard();
			helper.assertTrue(pastOwner > 0.8, "no child got past its owner: the farthest is " + pastOwner + " blocks beyond it");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void aBallThatHitsSomethingFirstDoesNotSplit(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 3.0, 3.5));
		StellaSmallFireball ball = new StellaSmallFireball(helper.getLevel(), at.x, at.y, at.z, new Vec3(0.0, -1.0, 0.0));
		ball.setSplitDistance(10.0);
		helper.getLevel().addFreshEntity(ball);
		helper.runAfterDelay(30L, () -> {
			boolean landed = ball.isRemoved();
			boolean split = SPLITS.containsKey(ball.getUUID());
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aBallThatHitsSomethingFirstDoesNotSplit landed={} split={}", TEST, landed, split);
			helper.assertTrue(landed, "the ball never landed, so the case proves nothing");
			helper.assertFalse(split, "a ball that hit the ground before its split distance split anyway");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void aBlazeBallSplitsAndTheChildrenBlastTheTargetForTheBlaze(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Blaze blaze = EntityTypes.BLAZE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
		blaze.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		blaze.snapTo(helper.absoluteVec(new Vec3(1.5, 3.0, 0.5)), 0.0F, 0.0F);
		blaze.setPersistenceRequired();
		helper.getLevel().addFreshEntity(blaze);
		BehaviorInstaller.installBlazeAttack(blaze);
		// The mod's distance goals sit above the attack goal and walk the blaze off its target, so the rig strips them.
		blaze.getGoalSelector().removeAllGoals(goal -> goal instanceof KeepDistanceGoal
			|| goal instanceof RetreatGoal
			|| goal instanceof HoldStandoffGoal
			|| goal instanceof ShieldUseGoal);
		Zombie target = helper.spawnMob(EntityTypes.ZOMBIE, 6, 1, 7).withNoFreeWill().spawn();
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		target.setHealth(1000.0F);
		helper.onEachTick(() -> {
			target.setInvulnerableTime(0);
			blaze.setTarget(target);
		});
		helper.succeedWhen(() -> {
			List<Split> mine = SPLITS.values().stream().filter(split -> split.parent().getOwner() == blaze).toList();
			helper.assertTrue(!mine.isEmpty(), "no ball of the blaze has split yet");
			helper.assertTrue(BLASTED.getOrDefault(target.getUUID(), List.of()).contains(blaze.getUUID()), "no blast from the blaze has reached the target yet");
			Split first = mine.get(0);
			boolean childHit = first.children().stream().anyMatch(StellaSmallFireball::isRemoved);
			helper.assertTrue(childHit, "no child of the first split has landed yet");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aBlazeBallSplitsAndTheChildrenBlastTheTargetForTheBlaze splits={}", TEST, mine.size());
			mine.forEach(split -> split.children().forEach(StellaSmallFireball::discard));
			blaze.discard();
		});
	}

	/** A ball of the shipped kind, flying along +z, with no owner and no target. */
	private static StellaSmallFireball launch(final GameTestHelper helper, final double splitDistance, final Vec3 from) {
		Arena.buildFloor(helper);
		Vec3 at = helper.absoluteVec(from);
		StellaSmallFireball ball = new StellaSmallFireball(helper.getLevel(), at.x, at.y, at.z, new Vec3(0.0, 0.0, 1.0));
		ball.setSplitDistance(splitDistance);
		helper.getLevel().addFreshEntity(ball);
		return ball;
	}

	/** A blaze that only floats where it is put, so it never fires and never moves. */
	private static Blaze owner(final GameTestHelper helper, final double z) {
		Blaze owner = EntityTypes.BLAZE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
		owner.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		owner.snapTo(helper.absoluteVec(new Vec3(3.5, 3.0, z)), 0.0F, 0.0F);
		owner.setPersistenceRequired();
		owner.setNoAi(true);
		owner.setNoGravity(true);
		helper.getLevel().addFreshEntity(owner);
		return owner;
	}
}
