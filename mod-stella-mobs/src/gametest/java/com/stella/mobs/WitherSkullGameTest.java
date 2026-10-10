package com.stella.mobs;

import com.stella.mobs.behavior.WitherShots;
import com.stella.mobs.behavior.WitherVolleyGoal;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The encounter skull hurts bodies within two blocks of its impact, scales once, breaks no block, adds Wither and now
 * and then chill or burning. The Wither looses them faster as its health falls.
 */
public final class WitherSkullGameTest {
	private static final long SEED = 20260710L;
	private static final int RIDER_DRAWS = 400;
	private static final double TOLERANCE = 0.05;

	private static WitherSkullShot loose(final WitherBoss wither, final Vec3 at) {
		WitherSkullShot skull = WitherShots.fire(wither, 0, at, WitherVolleyGoal.aimedSpec(WitherConfigStore.get()));
		if (skull == null) {
			throw new AssertionError("the volley made no encounter skull");
		}
		return skull;
	}

	@GameTest(maxTicks = 60, padding = WideArena.PADDING)
	public void damageIsOnceScaled(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		arena.whenReady(List.of(new Vec3(28, 12, 28), new Vec3(28, 3, 40)), () -> {
			WitherBoss wither = WitherRig.wither(helper, arena, 28, 12, 28, 0);
			Cow cow = WitherRig.cow(helper, arena, 28, WideArena.SPAWN_Y, 40);
			WitherSkullShot skull = loose(wither, cow.position());
			float before = cow.getHealth();
			skull.detonate(cow.position(), RandomSource.create(SEED));
			float lost = before - cow.getHealth();
			int level = ThreatState.level(wither);
			double expected = skull.spec().share() * (8.0 * SpawnScaling.damageMultiplier(level) + SpawnScaling.tailFlat(level));
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSkullDamage RESULT level={} lost={} expected={}", level, lost, expected);
			helper.assertTrue(Math.abs(lost - expected) < TOLERANCE, "the cow lost " + lost + ", want " + expected + " (scaled once)");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80, padding = WideArena.PADDING)
	public void blocksStayUnchanged(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		int wallZ = 36;
		// Dirt and glass break to a vanilla blast, so a surviving wall shows no vanilla explosion ran.
		for (int x = 25; x <= 31; x++) {
			for (int y = 1; y <= 5; y++) {
				helper.setBlock(x, y, wallZ, (x + y) % 2 == 0 ? Blocks.DIRT : Blocks.GLASS);
			}
		}
		List<BlockState> before = wall(helper, wallZ);
		Cow[] cow = new Cow[1];
		WitherSkullShot[] skull = new WitherSkullShot[1];
		float[] start = new float[1];
		int[] readyAt = {-1};
		helper.onEachTick(() -> {
			if (readyAt[0] < 0 || helper.getTick() < readyAt[0] + 50 || cow[0] == null) {
				return;
			}
			readyAt[0] = Integer.MAX_VALUE;
			List<BlockState> after = wall(helper, wallZ);
			float lost = start[0] - cow[0].getHealth();
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSkullBlocks RESULT skullGone={} cowLost={} wallSame={}", skull[0].isRemoved(), lost, before.equals(after));
			helper.assertTrue(skull[0].isRemoved() && lost > 0.0F, "the skull never burst on the cow: gone=" + skull[0].isRemoved() + " lost=" + lost);
			helper.assertTrue(before.equals(after), "the blast changed the wall");
			helper.succeed();
		});
		arena.whenReady(List.of(new Vec3(28, 3, 8), new Vec3(28, 3, 34)), () -> {
			WitherBoss wither = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 8, 0);
			cow[0] = WitherRig.cow(helper, arena, 29.6, WideArena.SPAWN_Y, wallZ - 0.8);
			start[0] = cow[0].getHealth();
			skull[0] = loose(wither, arena.at(28, WideArena.SPAWN_Y + 0.5, wallZ + 0.5));
			readyAt[0] = (int) helper.getTick();
		});
	}

	private static List<BlockState> wall(final GameTestHelper helper, final int wallZ) {
		List<BlockState> states = new ArrayList<>();
		for (int x = 23; x <= 33; x++) {
			for (int y = 0; y <= 7; y++) {
				for (int z = wallZ - 3; z <= wallZ + 3; z++) {
					states.add(helper.getBlockState(new BlockPos(x, y, z)));
				}
			}
		}
		return states;
	}

	@GameTest(maxTicks = 60, padding = WideArena.PADDING)
	public void radiusIsTwo(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		// The brief says two blocks, so the distances are literal and a drifted constant fails here.
		double halfWidth = 0.45;
		double insideGap = 1.9;
		double outsideGap = 2.5;
		arena.whenReady(List.of(new Vec3(28, 12, 28), new Vec3(28, 3, 20)), () -> {
			WitherBoss wither = WitherRig.wither(helper, arena, 28, 12, 28, 0);
			Vec3 point = arena.at(28, WideArena.SPAWN_Y + 0.5, 20);
			Cow inside = WitherRig.cow(helper, arena, 28 + insideGap + halfWidth, WideArena.SPAWN_Y, 20);
			Cow outside = WitherRig.cow(helper, arena, 28 - outsideGap - halfWidth, WideArena.SPAWN_Y, 20);
			float insideBefore = inside.getHealth();
			float outsideBefore = outside.getHealth();
			WitherSkullShot skull = loose(wither, point);
			skull.detonate(point, RandomSource.create(SEED));
			float insideLost = insideBefore - inside.getHealth();
			float outsideLost = outsideBefore - outside.getHealth();
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSkullRadius RESULT inside={} outside={} insideLost={} outsideLost={}", insideGap, outsideGap, insideLost, outsideLost);
			helper.assertTrue(insideLost > 0.0F, "a body " + insideGap + " blocks from the blast was not hurt");
			helper.assertTrue(outsideLost == 0.0F, "a body " + outsideGap + " blocks from the blast lost " + outsideLost);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = WideArena.PADDING)
	public void everyVictimWithersAndSomeChillOrBurn(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		arena.whenReady(List.of(new Vec3(28, 12, 28), new Vec3(28, 3, 40)), () -> {
			WitherBoss wither = WitherRig.wither(helper, arena, 28, 12, 28, 0);
			Cow cow = WitherRig.cow(helper, arena, 28, WideArena.SPAWN_Y, 40);
			WitherSkullShot skull = loose(wither, cow.position());
			RandomSource random = RandomSource.create(SEED);
			int withered = 0;
			int chilled = 0;
			int burning = 0;
			int witherTicks = 0;
			for (int draw = 0; draw < RIDER_DRAWS; draw++) {
				cow.removeAllEffects();
				cow.clearFire();
				cow.setTicksFrozen(0);
				cow.setInvulnerableTime(0);
				skull.detonate(cow.position(), random);
				if (cow.hasEffect(MobEffects.WITHER)) {
					withered++;
					witherTicks = cow.getEffect(MobEffects.WITHER).getDuration();
				}
				chilled += cow.hasEffect(MobEffects.SLOWNESS) ? 1 : 0;
				burning += cow.isOnFire() ? 1 : 0;
			}
			double chance = WitherConfigStore.get().get(WitherConfig.BLAST_RIDER_CHANCE);
			double rider = (chilled + burning) / (double) RIDER_DRAWS;
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSkullRider RESULT draws={} withered={} chilled={} burning={} witherTicks={}",
				RIDER_DRAWS, withered, chilled, burning, witherTicks);
			helper.assertTrue(withered == RIDER_DRAWS, "only " + withered + " of " + RIDER_DRAWS + " hits applied Wither");
			helper.assertTrue(witherTicks == skull.spec().witherTicks(), "Wither lasted " + witherTicks);
			helper.assertTrue(Math.abs(rider - chance) < 0.10, "the rider share was " + rider + ", want about " + chance);
			helper.assertTrue(chilled > RIDER_DRAWS * chance * 0.3 && burning > RIDER_DRAWS * chance * 0.3, "chill " + chilled + " and burning " + burning + " are not both common");
			helper.succeed();
		});
	}

	@GameTest
	public void theSpeedCurve(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		double[] health = {1.0, 0.75, 0.5, 0.25, 0.10, 0.0};
		int[] expected = {40, 33, 26, 19, 15, 12};
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < health.length; i++) {
			int actual = WitherVolleyGoal.interval(health[i], config);
			log.append(health[i]).append("->").append(actual).append(' ');
			helper.assertTrue(actual == expected[i], "at health " + health[i] + " the gap is " + actual + ", want " + expected[i]);
		}
		helper.assertTrue(WitherVolleyGoal.interval(2.0, config) == 40 && WitherVolleyGoal.interval(-1.0, config) == 12, "the fraction is not clamped");
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSpeedCurve RESULT {}", log);
		helper.succeed();
	}

	private void measureGaps(final GameTestHelper helper, final double health, final String name) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		ServerPlayer[] player = new ServerPlayer[1];
		WitherBoss[] wither = new WitherBoss[1];
		boolean[] done = new boolean[1];
		int expected = WitherVolleyGoal.interval(health, WitherConfigStore.get());
		int shotsWanted = 6;
		helper.onEachTick(() -> {
			if (wither[0] == null || done[0]) {
				return;
			}
			WideArena.keepValid(player[0]);
			List<WideArena.Shot> shots = WideArena.shots(wither[0]);
			if (shots.size() >= shotsWanted) {
				done[0] = true;
				List<Integer> gaps = new ArrayList<>();
				for (int i = 1; i < shots.size(); i++) {
					gaps.add(shots.get(i).ownerTick() - shots.get(i - 1).ownerTick());
				}
				StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT health={} expected={} gaps={}", name, health, expected, gaps);
				for (int gap : gaps) {
					helper.assertTrue(Math.abs(gap - expected) <= 1, "a gap of " + gap + " ticks, want " + expected + " in " + gaps);
				}
				helper.succeed();
			}
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			WitherBoss spawned = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
			spawned.setHealth((float) (spawned.getMaxHealth() * health));
			WideArena.watch(spawned, () -> player[0]);
			wither[0] = spawned;
		});
	}

	@GameTest(maxTicks = 400, padding = WideArena.PADDING)
	public void volleysAtFullHealthFollowTheCurve(final GameTestHelper helper) {
		this.measureGaps(helper, 1.0, "witherVolleyFull");
	}

	@GameTest(maxTicks = 300, padding = WideArena.PADDING)
	public void volleysAtAQuarterFollowTheCurve(final GameTestHelper helper) {
		this.measureGaps(helper, 0.25, "witherVolleyQuarter");
	}
}
