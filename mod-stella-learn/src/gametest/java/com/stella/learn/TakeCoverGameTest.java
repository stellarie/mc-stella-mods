package com.stella.learn;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Melee mobs advance on a known shooter from cover to cover, and walk straight at anyone else. */
public class TakeCoverGameTest {
	private static final int LONG = 900;
	private static final double EYE = 1.6;

	private static void pillar(final GameTestHelper helper, final int x, final int z) {
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				helper.setBlock(x + dx, 1, z + dz, Blocks.STONE);
				helper.setBlock(x + dx, 2, z + dz, Blocks.STONE);
				helper.setBlock(x + dx, 3, z + dz, Blocks.STONE);
			}
		}
	}

	private static boolean hidden(final GameTestHelper helper, final ServerPlayer player, final Mob mob) {
		return !Cover.exposed(helper.getLevel(), player, mob.position().add(0.0, EYE, 0.0));
	}

	private static void shooter(final ServerPlayer player, final double times) {
		for (int i = 0; i < times; i++) {
			Profiles.record(player, Habit.RANGED);
		}
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aShootersTargetAdvancesThroughCover(final GameTestHelper helper) {
		LearnArena.floor(helper);
		// A pillar off the straight line: a mob that walks straight at the player never gets behind it.
		pillar(helper, 16, 28);
		ServerPlayer player = LearnArena.player(helper, 24.5, 20.5);
		shooter(player, LearnConfigStore.get().rangedSaturation());
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 40).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		LearnArena.whenReady(helper, List.of(player, zombie), () -> LearnArena.pollUntil(
			helper, () -> hidden(helper, player, zombie) && zombie.distanceTo(player) > 6.0, 800, () -> {
				StellaLearn.LOGGER.info(
					"[BEHAVIOR] test=aShootersTargetAdvancesThroughCover RESULT distance={} x={}", zombie.distanceTo(player), zombie.getX()
				);
				helper.succeed();
			}, () -> "the zombie never got out of the shooter's sight on its way in"
		));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aPlayerWhoNeverShootsIsWalkedStraightAt(final GameTestHelper helper) {
		LearnArena.floor(helper);
		pillar(helper, 16, 28);
		ServerPlayer player = LearnArena.player(helper, 24.5, 20.5);
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 40).spawn();
		LearnArena.keepTargeting(helper, zombie, player);
		boolean[] everHidden = {false};
		helper.onEachTick(() -> everHidden[0] |= hidden(helper, player, zombie));
		LearnArena.whenReady(helper, List.of(player, zombie), () -> LearnArena.pollUntil(
			helper, () -> zombie.distanceTo(player) < 3.0, 800, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=aPlayerWhoNeverShootsIsWalkedStraightAt RESULT everHidden={}", everHidden[0]);
				helper.assertTrue(!everHidden[0], "the zombie took cover from a player who never shot");
				helper.succeed();
			}, () -> "the zombie never reached the player"
		));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theCoverSpotIsHiddenAndNearerThePlayer(final GameTestHelper helper) {
		LearnArena.floor(helper);
		pillar(helper, 16, 28);
		ServerPlayer player = LearnArena.player(helper, 24.5, 20.5);
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 19, LearnArena.SPAWN_Y, 38).spawn();
		LearnArena.whenReady(helper, List.of(player, zombie), () -> LearnArena.pollUntil(helper, zombie::onGround, 60, () -> {
				Vec3 spot = Cover.find(zombie, player);
				helper.assertTrue(spot != null, "no cover was found beside a pillar");
				boolean hiddenThere = !Cover.exposed(helper.getLevel(), player, spot.add(0.0, EYE, 0.0));
				double gain = zombie.position().distanceTo(player.position()) - spot.distanceTo(player.position());
				StellaLearn.LOGGER.info("[BEHAVIOR] test=theCoverSpotIsHiddenAndNearerThePlayer RESULT hidden={} gain={}", hiddenThere, gain);
				helper.assertTrue(hiddenThere, "the cover spot is in the shooter's sight");
				helper.assertTrue(gain > 1.0, "the cover spot is no nearer the player: " + gain);
				helper.succeed();
		}, () -> "the zombie never landed"));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void noCoverIsFoundOnOpenGround(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 20.5);
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 24, LearnArena.SPAWN_Y, 40).spawn();
		LearnArena.whenReady(helper, List.of(player, zombie), () -> {
			Vec3 spot = Cover.find(zombie, player);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=noCoverIsFoundOnOpenGround RESULT spot={}", spot);
			helper.assertTrue(spot == null, "cover was found on open ground: " + spot);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aKnownShooterHasFullPressure(final GameTestHelper helper) {
		ServerPlayer player = LearnArena.player(helper, 24.5, 20.5);
		shooter(player, LearnConfigStore.get().rangedSaturation());
		double full = Cover.shooterPressure(player);
		ServerPlayer stranger = LearnArena.player(helper, 20.5, 20.5);
		double none = Cover.shooterPressure(stranger);
		StellaLearn.LOGGER.info("[BEHAVIOR] test=aKnownShooterHasFullPressure RESULT shooter={} stranger={}", full, none);
		helper.assertTrue(full > 0.99 && none == 0.0, "pressures were " + full + " and " + none);
		helper.succeed();
	}
}
