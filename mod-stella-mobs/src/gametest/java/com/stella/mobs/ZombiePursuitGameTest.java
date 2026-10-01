package com.stella.mobs;

import com.stella.mobs.mixin.MobTargetSelectorAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

/**
 * A zombie keeps hunting a player that it no longer sees. Vanilla drops the target after 60 unseen ticks and
 * stops at the end of its path. The scene holds the zombie still with a zero speed until its own target goal
 * has taken the player, then raises a wall between them and counts the unseen ticks. The wall stands close in
 * front of the zombie. A path that was made before the wall therefore ends at the wall, and only a goal that
 * repaths without sight walks round it. The target is set at spawn, because the vanilla melee goal spends its
 * one start check on the first AI step and the test clock never opens a second one.
 */
public class ZombiePursuitGameTest {
	private static final String TEST = "zombiePursuit";
	private static final int MAX_TICKS = 400;
	private static final int HOLD_TICKS = 100;
	private static final int CHASE_TICKS = 100;
	private static final double CLOSE_BLOCKS = 3.0;
	private static final double PLAYER_X = 16.5;
	private static final double PLAYER_Z = 28.5;
	private static final int ZOMBIE_X = 16;
	private static final int ZOMBIE_Z = 12;
	private static final int WALL_Z = 14;
	private static final int WALL_MIN_X = 14;
	private static final int WALL_MAX_X = 18;
	private static final int WALL_TOP_Y = 6;

	private record Scene(MeleeKitFloor floor, ServerPlayer player, Zombie zombie, double walkSpeed) {
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void keepsUnseenTarget(final GameTestHelper helper) {
		Scene scene = scene(helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(holdsPlayer(scene), "the zombie's own target goal never took the player"))
			.thenExecute(() -> hide(helper))
			.thenIdle(HOLD_TICKS)
			.thenExecute(() -> {
				String seen = "target=" + scene.zombie().getTarget() + " sees=" + scene.zombie().hasLineOfSight(scene.player())
					+ " mobTick=" + scene.zombie().tickCount;
				helper.assertFalse(scene.zombie().hasLineOfSight(scene.player()), "the wall did not hide the player: " + seen);
				helper.assertTrue(scene.zombie().getTarget() == scene.player(), "the zombie dropped an unseen target: " + seen);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.keepsUnseenTarget RESULT {}", TEST, seen);
				scene.floor().end();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = MAX_TICKS, padding = MeleeKitFloor.PADDING)
	public void closesOnHiddenPlayer(final GameTestHelper helper) {
		Scene scene = scene(helper);
		double[] startGap = {-1.0};
		double[] nearest = {Double.MAX_VALUE};
		boolean[] chasing = {false};

		helper.onEachTick(() -> {
			if (chasing[0]) {
				nearest[0] = Math.min(nearest[0], MeleeKitFloor.flat(scene.zombie(), scene.player()));
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(holdsPlayer(scene), "the zombie's own target goal never took the player"))
			.thenExecute(() -> hide(helper))
			.thenIdle(HOLD_TICKS)
			.thenExecute(() -> {
				helper.assertFalse(scene.zombie().hasLineOfSight(scene.player()), "the wall did not hide the player");
				scene.zombie().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(scene.walkSpeed());
				startGap[0] = MeleeKitFloor.flat(scene.zombie(), scene.player());
				chasing[0] = true;
			})
			.thenIdle(CHASE_TICKS)
			.thenExecute(() -> {
				String seen = "startGap=" + startGap[0] + " nearest=" + nearest[0] + " target=" + scene.zombie().getTarget()
					+ " sees=" + scene.zombie().hasLineOfSight(scene.player());
				helper.assertTrue(startGap[0] - nearest[0] >= CLOSE_BLOCKS, "the zombie did not close on the hidden player: " + seen);
				StellaMobs.LOGGER.info("[BEHAVIOR] test={}.closesOnHiddenPlayer RESULT {}", TEST, seen);
				scene.floor().end();
			})
			.thenSucceed();
	}

	private static Scene scene(final GameTestHelper helper) {
		MeleeKitFloor floor = MeleeKitFloor.build(helper);
		ServerPlayer player = floor.player(PLAYER_X, PLAYER_Z);
		Zombie zombie = floor.mob(EntityTypes.ZOMBIE, ZOMBIE_X, MeleeKitFloor.SPAWN_Y, ZOMBIE_Z);
		zombie.getGoalSelector().removeAllGoals(goal -> goal instanceof WaterAvoidingRandomStrollGoal);
		double walkSpeed = zombie.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		zombie.setTarget(player);
		helper.onEachTick(() -> player.setInvulnerableTime(0));
		return new Scene(floor, player, zombie, walkSpeed);
	}

	// The wall must find the target goal running.
	private static boolean holdsPlayer(final Scene scene) {
		if (!scene.floor().ticking() || scene.zombie().getTarget() != scene.player() || !scene.zombie().hasLineOfSight(scene.player())) {
			return false;
		}

		for (WrappedGoal wrapped : ((MobTargetSelectorAccessor) scene.zombie()).stellaMobs$targetSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> && wrapped.isRunning()) {
				return true;
			}
		}

		return false;
	}

	private static void hide(final GameTestHelper helper) {
		for (int x = WALL_MIN_X; x <= WALL_MAX_X; x++) {
			for (int y = MeleeKitFloor.FLOOR_TOP_Y; y <= WALL_TOP_Y; y++) {
				helper.setBlock(x, y, WALL_Z, Blocks.STONE);
			}
		}
	}
}
