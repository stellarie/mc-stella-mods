package com.stella.learn;

import com.stella.learn.model.ArcherObservation;
import com.stella.learn.model.ArcherState;
import com.stella.learn.model.Contract;
import com.stella.mobs.mixin.MobTargetSelectorAccessor;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The observation has the size and order of the contract, and a target behind a wall is not leaked through it. */
public class ModelContractGameTest {
	private static final int LONG = 400;
	private static final int WALL_Z = 29;
	/** The target fields that may stay set while the target is hidden. */
	private static final Set<String> LAST_SEEN = Set.of("target.visible", "target.rel_x", "target.rel_y", "target.rel_z", "target.ticks_since_seen");

	private static Mob still(final GameTestHelper helper) {
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		// No goals, so it neither walks nor shoots, but it still stands on the floor.
		archer.getGoalSelector().removeAllGoals(goal -> true);
		((MobTargetSelectorAccessor) archer).stellaMobs$targetSelector().removeAllGoals(goal -> true);
		return archer;
	}

	private static float at(final float[] observation, final String name) {
		int index = Contract.archer().indexOf(name);
		if (index < 0) {
			throw new IllegalStateException("the contract has no field " + name);
		}

		return observation[index];
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void observationHasContractSize(final GameTestHelper helper) {
		LearnArena.floor(helper);
		// A distance that is not a whole number, since the sight line compares distances that round differently.
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.7);
		Mob archer = still(helper);
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, archer::onGround, 40, () -> {
			Contract contract = Contract.archer();
			String mismatch = ArcherObservation.mismatch(contract);
			helper.assertTrue(mismatch == null, "the builder and the contract disagree: " + mismatch);
			float[] observation = ArcherObservation.build(archer, player, new ArcherState());
			helper.assertTrue(observation.length == contract.observationSize(), "the observation holds " + observation.length + " values");
			helper.assertTrue(observation.length == 121, "the contract holds " + observation.length + " values, not 121");
			for (int i = 0; i < observation.length; i++) {
				helper.assertTrue(Float.isFinite(observation[i]) && Math.abs(observation[i]) <= Contract.CLAMP, contract.name(i) + " is " + observation[i]);
			}

			// Named fields show the builder writes each value where the contract says it goes.
			helper.assertTrue(at(observation, "self.on_ground") == 1.0F, "the archer is not on the ground");
			helper.assertTrue(at(observation, "self.health_fraction") == 1.0F, "the archer is hurt");
			helper.assertTrue(at(observation, "target.visible") == 1.0F, "the target is in the open and not seen");
			helper.assertTrue(at(observation, "target.rel_z") > 0.5F && Math.abs(at(observation, "target.rel_x")) < 0.05F, "a target ten blocks ahead reads x=" + at(observation, "target.rel_x") + " z=" + at(observation, "target.rel_z"));
			helper.assertTrue(at(observation, "target.held_other") == 1.0F && at(observation, "target.held_sword") == 0.0F, "an empty hand is not read as 'other'");
			helper.assertTrue(at(observation, "ray.d0") < 0.05F, "the floor is not under the feet");
			StellaLearn.LOGGER.info("[BEHAVIOR] test=observationHasContractSize RESULT size={} contract_hash={}", observation.length, contract.hashHex());

			// The mob's right is world -x at yaw 0, so a target there reads positive x.
			Vec3 right = helper.absoluteVec(new Vec3(14.5, 1.0, 24.5));
			player.setPos(right.x, right.y, right.z);
			float[] toTheRight = ArcherObservation.build(archer, player, new ArcherState());
			helper.assertTrue(at(toTheRight, "target.rel_x") > 0.5F && Math.abs(at(toTheRight, "target.rel_z")) < 0.05F, "a target on the right reads x=" + at(toTheRight, "target.rel_x") + " z=" + at(toTheRight, "target.rel_z"));
			helper.succeed();
		}, () -> "the archer never landed"));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void hiddenTargetIsBlank(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = still(helper);
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, archer::onGround, 40, () -> {
			ArcherState seen = new ArcherState();
			float[] open = ArcherObservation.build(archer, player, seen);
			helper.assertTrue(at(open, "target.visible") == 1.0F && at(open, "target.distance") > 0.0F, "the open target is not read");
			float lastZ = at(open, "target.rel_z");
			// The ray straight ahead runs its full 16 blocks in the open, and stops at the wall once one stands there.
			helper.assertTrue(at(open, "ray.h0") == 1.0F, "a clear ray reads " + at(open, "ray.h0"));
			for (int x = 18; x <= 31; x++) {
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(x, y, WALL_Z, Blocks.STONE);
				}
			}

			// The line-of-sight answer is cached for the tick, so the wall is read two ticks on.
			helper.runAfterDelay(2L, () -> {
				float[] behind = ArcherObservation.build(archer, player, seen);
				float[] never = ArcherObservation.build(archer, player, new ArcherState());
				Contract contract = Contract.archer();
				for (int i = 0; i < contract.observationSize(); i++) {
					String name = contract.name(i);
					if (name.startsWith("target.") && !LAST_SEEN.contains(name)) {
						helper.assertTrue(behind[i] == 0.0F, name + " leaks through the wall: " + behind[i]);
						helper.assertTrue(never[i] == 0.0F, name + " leaks through the wall on a first look: " + never[i]);
					}
				}

				helper.assertTrue(at(behind, "target.visible") == 0.0F && at(never, "target.visible") == 0.0F, "a hidden target reads as visible");
				helper.assertTrue(Math.abs(at(never, "ray.h0") - 4.5F / 16.0F) < 0.02F, "the ray at a wall 4.5 blocks ahead reads " + at(never, "ray.h0"));
				helper.assertTrue(Math.abs(at(behind, "target.rel_z") - lastZ) < 0.01F && lastZ > 0.5F, "the last seen position was lost: " + at(behind, "target.rel_z") + " against " + lastZ);
				helper.assertTrue(at(never, "target.rel_x") == 0.0F && at(never, "target.rel_z") == 0.0F, "a target never seen has a position");
				StellaLearn.LOGGER.info("[BEHAVIOR] test=hiddenTargetIsBlank RESULT last_seen_z={} never_seen_z={}", at(behind, "target.rel_z"), at(never, "target.rel_z"));
				helper.succeed();
			});
		}, () -> "the archer never landed"));
	}
}
