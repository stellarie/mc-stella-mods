package com.stella.mobs;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.EvokerStorm;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearTier;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Slice B: the elite evoker's rain of fire. The trigger, the one-shot flag, the ball count over the
 * spec's window, the absence of fire and block damage, and the two negative cases.
 *
 * Every number here comes from the board's spec, never from the implementation's own arithmetic: the
 * ball count is the literal 40 that 1200 ticks at one ball per 30 ticks gives, and the numbers are read
 * back from the world rather than recomputed.
 */
public class StormGameTest {
	private static final String TEST = "evokerStorm";
	private static final double EPSILON = 1.0e-6;
	private static final int SPAWN_X = 3;
	private static final int SPAWN_Y = 3;
	private static final int SPAWN_Z = 3;
	/** The board's window and cadence, restated here as literals so the test cannot inherit a typo. */
	private static final int SPEC_WINDOW_TICKS = 1200;
	private static final int SPEC_BALLS = 40;
	private static final int WINDOW_SETTLE_TICKS = 60;
	/** The area the storm covers, plus room above for the balls to fall in. */
	private static final int AREA_RADIUS = 8;
	private static final int AREA_ABOVE = 12;
	private static final String QUIET = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}""";

	@GameTest(maxTicks = 200)
	public void stormStartsAtHalfHealthAndOnlyOnce(final GameTestHelper helper) {
		Evoker evoker = spawnEliteEvoker(helper);
		float half = evoker.getMaxHealth() / 2.0F;
		// Above half: a real hit, but the health stays over the line, so nothing casts.
		evoker.setHealth(half + 5.0F);
		hurt(helper, evoker, 1.0F);
		boolean aboveHalf = EvokerStorm.isActive(evoker);
		boolean flagAbove = EliteState.isStormCast(evoker);
		// At or below half: the storm begins.
		evoker.setHealth(half + 1.0F);
		hurt(helper, evoker, 2.0F);
		boolean atHalf = EvokerStorm.isActive(evoker);
		boolean flagAtHalf = EliteState.isStormCast(evoker);
		// Caster-scoped: the level holds every arena at once, so a level-wide count mixes the storms.
		int stormsAtFirstCast = EvokerStorm.stormsFor(evoker);
		float healthAtCast = evoker.getHealth();
		// A second crossing must not arm a second storm.
		evoker.setHealth(half + 1.0F);
		hurt(helper, evoker, 2.0F);
		int stormsAfterSecondCrossing = EvokerStorm.stormsFor(evoker);

		result(
			"stormTrigger",
			"max=" + evoker.getMaxHealth() + " half=" + half + " aboveHalfActive=" + aboveHalf
				+ " flagAbove=" + flagAbove + " atHalfActive=" + atHalf + " flagAtHalf=" + flagAtHalf
				+ " healthAtCast=" + healthAtCast + " stormsAtFirstCast=" + stormsAtFirstCast
				+ " stormsAfterSecondCrossing=" + stormsAfterSecondCrossing
		);

		helper.assertFalse(aboveHalf, "the storm cast while the evoker was above half health");
		helper.assertFalse(flagAbove, "the storm flag was set while the evoker was above half health");
		helper.assertTrue(healthAtCast <= half, "the health at the cast was " + healthAtCast + ", above half " + half);
		helper.assertTrue(atHalf, "the storm did not cast at or below half health");
		helper.assertTrue(flagAtHalf, "the durable storm flag was not set at the cast");
		helper.assertValueEqual(Integer.valueOf(stormsAtFirstCast), Integer.valueOf(1), "storms after the first cast");
		helper.assertValueEqual(
			Integer.valueOf(stormsAfterSecondCrossing), Integer.valueOf(1), "storms after a second crossing"
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void nonEliteEvokerNeverCasts(final GameTestHelper helper) {
		Evoker evoker = spawnPlainEvoker(helper);
		float half = evoker.getMaxHealth() / 2.0F;
		evoker.setHealth(half + 1.0F);
		hurt(helper, evoker, 2.0F);
		boolean active = EvokerStorm.isActive(evoker);
		boolean flag = EliteState.isStormCast(evoker);

		result("stormNonElite", "elite=" + Elite.isElite(evoker) + " active=" + active + " flag=" + flag);

		helper.assertFalse(Elite.isElite(evoker), "the subject was elite, so the case proves nothing");
		helper.assertFalse(active, "a non-elite evoker cast the storm");
		helper.assertFalse(flag, "a non-elite evoker set the storm flag");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void eliteEvokerAboveHalfNeverCasts(final GameTestHelper helper) {
		Evoker evoker = spawnEliteEvoker(helper);
		float half = evoker.getMaxHealth() / 2.0F;
		evoker.setHealth(evoker.getMaxHealth());
		hurt(helper, evoker, 1.0F);
		boolean active = EvokerStorm.isActive(evoker);
		boolean flag = EliteState.isStormCast(evoker);

		result(
			"stormAboveHalf",
			"elite=" + Elite.isElite(evoker) + " health=" + evoker.getHealth() + " half=" + half
				+ " active=" + active + " flag=" + flag
		);

		helper.assertTrue(Elite.isElite(evoker), "the subject was not elite, so the case proves nothing");
		helper.assertTrue(evoker.getHealth() > half, "the subject was already at or below half health");
		helper.assertFalse(active, "an elite evoker above half health cast the storm");
		helper.assertFalse(flag, "an elite evoker above half health set the storm flag");
		helper.succeed();
	}

	@GameTest(maxTicks = SPEC_WINDOW_TICKS + WINDOW_SETTLE_TICKS + 60)
	public void stormSpendsTheSpecBallCountWithoutFireOrBlockDamage(final GameTestHelper helper) {
		Evoker evoker = spawnEliteEvoker(helper);
		ServerLevel level = helper.getLevel();
		AABB area = stormArea(evoker);
		Set<UUID> balls = new HashSet<>();
		BlockPos corner = corner(evoker);
		BlockState[] before = census(level, corner);
		int fireBefore = countFire(level, corner);

		startStorm(helper, evoker);
		helper.onEachTick(() -> {
			for (SmallFireball ball : level.getEntitiesOfClass(SmallFireball.class, area, b -> b.getOwner() == evoker)) {
				balls.add(ball.getUUID());
			}
		});

		helper.runAfterDelay(SPEC_WINDOW_TICKS + WINDOW_SETTLE_TICKS, () -> {
			int fireAfter = countFire(level, corner);
			int changed = changedBlocks(level, corner, before);
			int alive = EvokerStorm.activeCount(level);

			result(
				"stormWindow",
				"window=" + SPEC_WINDOW_TICKS + " specBalls=" + SPEC_BALLS + " countedBalls=" + balls.size()
					+ " fireBefore=" + fireBefore + " fireAfter=" + fireAfter + " changedBlocks=" + changed
					+ " activeStorms=" + alive
			);

			helper.assertValueEqual(
				Integer.valueOf(balls.size()), Integer.valueOf(SPEC_BALLS),
				"balls counted in the window, against the spec's " + SPEC_BALLS
			);
			helper.assertValueEqual(
				Integer.valueOf(fireAfter), Integer.valueOf(fireBefore), "fire blocks in the area after the storm"
			);
			helper.assertValueEqual(Integer.valueOf(changed), Integer.valueOf(0), "blocks changed in the area");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void stormStopsWhenTheEvokerDies(final GameTestHelper helper) {
		Evoker evoker = spawnEliteEvoker(helper);
		ServerLevel level = helper.getLevel();
		AABB area = stormArea(evoker);
		Set<UUID> afterDeath = new HashSet<>();
		startStorm(helper, evoker);
		boolean startedActive = EvokerStorm.isActive(evoker);
		hurt(helper, evoker, 1000.0F);
		boolean dead = !evoker.isAlive();
		// Registered here, from the test body. Registering it from inside a delayed callback mutates the
		// framework's run-at-tick map while that map is being iterated, which corrupts it and crashes the
		// server. The collector idles for ten ticks first, so a ball already in flight can land.
		long collectFrom = helper.getTick() + 10L;
		helper.onEachTick(() -> {
			if (helper.getTick() < collectFrom) {
				return;
			}

			for (SmallFireball ball : level.getEntitiesOfClass(SmallFireball.class, area, b -> b.getOwner() == evoker)) {
				afterDeath.add(ball.getUUID());
			}
		});

		helper.runAfterDelay(210L, () -> {
			boolean stillActive = EvokerStorm.isActive(evoker);
			int active = EvokerStorm.activeCount(level);

			result(
				"stormDeath",
				"startedActive=" + startedActive + " dead=" + dead + " stillActive=" + stillActive
					+ " ballsAfterDeath=" + afterDeath.size() + " stormsInLevel=" + active
			);

			helper.assertTrue(dead, "the evoker survived a lethal hit, so the case proves nothing");
			helper.assertTrue(startedActive, "the storm never started, so the case proves nothing");
			// Caster-scoped, because the level is shared with the other storm tests running beside this one.
			helper.assertFalse(stillActive, "the storm stayed active after its caster died");
			helper.assertValueEqual(Integer.valueOf(afterDeath.size()), Integer.valueOf(0), "balls after death");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void theStormFlagIsDurableAndAnOlderSaveStillDecodes(final GameTestHelper helper) {
		// A save written before the storm field existed carries none of the keys.
		EliteState legacy = EliteState.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).resultOrPartial().orElseThrow();
		EliteState marked = new EliteState(
			false, true, Optional.of(GearTier.DIAMOND), 0, false, true, false, false, false
		);
		var encoded = EliteState.CODEC.encodeStart(JsonOps.INSTANCE, marked).resultOrPartial().orElseThrow();
		EliteState decoded = EliteState.CODEC.parse(JsonOps.INSTANCE, encoded).resultOrPartial().orElseThrow();

		result(
			"stormFlag",
			"legacyStormCast=" + legacy.stormCast() + " decodedStormCast=" + decoded.stormCast()
				+ " decodedElite=" + decoded.elite()
		);

		helper.assertFalse(legacy.stormCast(), "an older save invented a spent storm");
		helper.assertTrue(decoded.stormCast(), "the storm flag did not survive a codec round trip");
		helper.assertTrue(decoded.elite(), "the elite flag did not survive a codec round trip");
		helper.succeed();
	}

	private static Evoker spawnEliteEvoker(final GameTestHelper helper) {
		prepare(helper);
		Evoker evoker = spawnEvoker(helper);
		Elite.mark(evoker, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		return evoker;
	}

	private static Evoker spawnPlainEvoker(final GameTestHelper helper) {
		prepare(helper);
		return spawnEvoker(helper);
	}

	private static void prepare(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(QUIET));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
	}

	private static Evoker spawnEvoker(final GameTestHelper helper) {
		Evoker evoker = helper.spawnMob(EntityTypes.EVOKER, SPAWN_X, SPAWN_Y, SPAWN_Z)
			.spawnReason(EntitySpawnReason.NATURAL)
			.spawn();
		evoker.setPersistenceRequired();
		evoker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		return evoker;
	}

	/** One real hit, not a health write, because the trigger rides the damage event. */
	private static void hurt(final GameTestHelper helper, final Evoker evoker, final float amount) {
		evoker.setInvulnerableTime(0);
		evoker.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), amount);
	}

	private static void startStorm(final GameTestHelper helper, final Evoker evoker) {
		float half = evoker.getMaxHealth() / 2.0F;
		evoker.setHealth(half + 1.0F);
		hurt(helper, evoker, 2.0F);
	}

	private static AABB stormArea(final Evoker evoker) {
		double x = evoker.getX();
		double y = evoker.getY();
		double z = evoker.getZ();
		return new AABB(
			x - AREA_RADIUS, y - AREA_RADIUS, z - AREA_RADIUS, x + AREA_RADIUS, y + AREA_ABOVE, z + AREA_RADIUS
		);
	}

	private static BlockPos corner(final Evoker evoker) {
		return BlockPos.containing(evoker.getX() - AREA_RADIUS, evoker.getY() - AREA_RADIUS, evoker.getZ() - AREA_RADIUS);
	}

	private static int size() {
		return (AREA_RADIUS * 2 + 1) * (AREA_RADIUS + AREA_ABOVE + 1) * (AREA_RADIUS * 2 + 1);
	}

	private static BlockState[] census(final ServerLevel level, final BlockPos corner) {
		BlockState[] states = new BlockState[size()];
		int index = 0;

		for (int dx = 0; dx <= AREA_RADIUS * 2; dx++) {
			for (int dy = 0; dy <= AREA_RADIUS + AREA_ABOVE; dy++) {
				for (int dz = 0; dz <= AREA_RADIUS * 2; dz++) {
					states[index++] = level.getBlockState(corner.offset(dx, dy, dz));
				}
			}
		}

		return states;
	}

	private static int changedBlocks(final ServerLevel level, final BlockPos corner, final BlockState[] before) {
		int changed = 0;
		int index = 0;

		for (int dx = 0; dx <= AREA_RADIUS * 2; dx++) {
			for (int dy = 0; dy <= AREA_RADIUS + AREA_ABOVE; dy++) {
				for (int dz = 0; dz <= AREA_RADIUS * 2; dz++) {
					if (!level.getBlockState(corner.offset(dx, dy, dz)).equals(before[index++])) {
						changed++;
					}
				}
			}
		}

		return changed;
	}

	private static int countFire(final ServerLevel level, final BlockPos corner) {
		int fire = 0;

		for (int dx = 0; dx <= AREA_RADIUS * 2; dx++) {
			for (int dy = 0; dy <= AREA_RADIUS + AREA_ABOVE; dy++) {
				for (int dz = 0; dz <= AREA_RADIUS * 2; dz++) {
					BlockState state = level.getBlockState(corner.offset(dx, dy, dz));
					if (state.is(Blocks.FIRE)) {
						fire++;
					}
				}
			}
		}

		return fire;
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={}.{} RESULT {}", TEST, test, detail);
	}
}
