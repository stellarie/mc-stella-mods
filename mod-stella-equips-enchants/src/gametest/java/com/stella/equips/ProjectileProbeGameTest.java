package com.stella.equips;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.phys.Vec3;

/**
 * The two opening probes for slice 2. Probe 1 counts the PROJECTILE_SPAWNED
 * hook per arrow. Probe 2 finds the explode fields that spare a block.
 */
public class ProjectileProbeGameTest {
	private static final String OBJECTIVE_NAME = "probe.spawned";
	private static final Identifier COUNT_FUNCTION =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "probe_count_spawned");
	private static final Identifier COUNT_ENCHANTMENT =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "probe_spawn_count");
	private static final Identifier COUNT_PLUS_EXPLODE_ENCHANTMENT =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "probe_explode_none");
	private static final Identifier HIT_BLOCK_NONE_ENCHANTMENT =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "probe_hit_block_explode_none");
	private static final Identifier HIT_BLOCK_BLOCK_ENCHANTMENT =
		Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "probe_hit_block_explode_block");

	private static final int ARROW_DROP_BLOCKS = 5;
	private static final int FLOOR_RADIUS = 2;
	private static final int WALL_X = 6;
	private static final int WALL_RADIUS = 1;
	private static final int WALL_HEIGHT = 2;
	private static final int PROBE_READ_TICK = 20;

	/** Probe 1 answer: PROJECTILE_SPAWNED recordings for one arrow of one shot. */
	private static final int EXPECTED_SPAWN_COUNT = 1;

	@GameTest(maxTicks = 200)
	public void probeCountFunctionIsLoaded(final GameTestHelper helper) {
		boolean present = helper.getLevel().getServer().getFunctions().get(COUNT_FUNCTION).isPresent();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeCountFunctionLoaded RESULT id={} present={}",
			COUNT_FUNCTION, present
		);
		helper.assertTrue(present, "the probe function " + COUNT_FUNCTION + " is not loaded");
		helper.succeed();
	}

	/** Probe 1. The count comes from this objective alone, read right after the shot. */
	@GameTest(maxTicks = 200)
	public void probeProjectileSpawnedCountForOneArrow(final GameTestHelper helper) {
		Scoreboard scoreboard = resetObjective(helper);
		BlockPos floorCenter = helper.absolutePos(new BlockPos(4, 0, 4));
		buildFloor(helper, floorCenter);

		shootDownward(helper, bowWith(helper, COUNT_ENCHANTMENT), floorCenter);
		int hookCalls = objectiveScoreCount(scoreboard);

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeProjectileSpawnedCount RESULT spawnCountPerArrow={} arrowsFired={}",
			hookCalls, 1
		);
		helper.assertTrue(
			hookCalls == EXPECTED_SPAWN_COUNT,
			"one arrow recorded " + hookCalls + " PROJECTILE_SPAWNED calls"
		);
		clearObjective(scoreboard);
		helper.succeed();
	}

	/** Probe 2a. A hit_block explode with block_interaction none must leave the wall. */
	@GameTest(maxTicks = 200)
	public void probeExplodeNoneKeepsTheWall(final GameTestHelper helper) {
		runWallProbe(helper, HIT_BLOCK_NONE_ENCHANTMENT, "hit-block-none", true);
	}

	/** Probe 2b. Control. The same effect with block_interaction block breaks the wall. */
	@GameTest(maxTicks = 200)
	public void probeExplodeBlockBreaksTheWall(final GameTestHelper helper) {
		runWallProbe(helper, HIT_BLOCK_BLOCK_ENCHANTMENT, "hit-block-block", false);
	}

	/** Probe 2c. A spawn-time explode never reaches a block, so block_interaction cannot show there. */
	@GameTest(maxTicks = 200)
	public void probeExplodeOnSpawnMissesTheWall(final GameTestHelper helper) {
		runWallProbe(helper, COUNT_PLUS_EXPLODE_ENCHANTMENT, "spawn-none", true);
	}

	private static void runWallProbe(
		final GameTestHelper helper,
		final Identifier enchantment,
		final String label,
		final boolean expectWallIntact
	) {
		BlockPos floorCenter = helper.absolutePos(new BlockPos(4, 0, 4));
		buildFloor(helper, floorCenter);
		BlockPos wallCenter = helper.absolutePos(new BlockPos(WALL_X, 1, 4));
		buildWall(helper, wallCenter);
		shootAtWall(helper, bowWith(helper, enchantment), wallCenter);

		helper.runAfterDelay(PROBE_READ_TICK, () -> {
			boolean wallIntact = helper.getLevel().getBlockState(wallCenter).is(Blocks.STONE);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=probeExplode label={} RESULT wallIntact={} wallPos={}",
				label, wallIntact, wallCenter
			);
			helper.assertTrue(
				wallIntact == expectWallIntact,
				"probe " + label + " left the wall intact=" + wallIntact
					+ " but expected intact=" + expectWallIntact
			);
			helper.succeed();
		});
	}

	private static Scoreboard resetObjective(final GameTestHelper helper) {
		Scoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
		clearObjective(scoreboard);
		scoreboard.addObjective(
			OBJECTIVE_NAME,
			ObjectiveCriteria.DUMMY,
			Component.literal(OBJECTIVE_NAME),
			ObjectiveCriteria.RenderType.INTEGER,
			false,
			null
		);
		return scoreboard;
	}

	private static void clearObjective(final Scoreboard scoreboard) {
		Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
		if (objective != null) {
			scoreboard.removeObjective(objective);
		}
	}

	/** Scores on this one objective. getTrackedPlayers() is not per objective. */
	private static int objectiveScoreCount(final Scoreboard scoreboard) {
		Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
		if (objective == null) {
			throw new IllegalStateException("the probe objective vanished before the count was read");
		}

		return scoreboard.listPlayerScores(objective).size();
	}

	private static void buildFloor(final GameTestHelper helper, final BlockPos floorCenter) {
		for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++) {
			for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++) {
				helper.getLevel().setBlockAndUpdate(floorCenter.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
			}
		}
	}

	private static void buildWall(final GameTestHelper helper, final BlockPos wallCenter) {
		for (int dy = -1; dy <= WALL_HEIGHT; dy++) {
			for (int dz = -WALL_RADIUS; dz <= WALL_RADIUS; dz++) {
				helper.getLevel().setBlockAndUpdate(wallCenter.offset(0, dy, dz), Blocks.STONE.defaultBlockState());
			}
		}
	}

	/** Mirrors ProjectileWeaponItem.shoot for one arrow: the ammo stack draws with the weapon. */
	private static void shootDownward(final GameTestHelper helper, final ItemStack bow, final BlockPos target) {
		Vec3 spawn = new Vec3(
			target.getX() + 0.5,
			target.getY() + ARROW_DROP_BLOCKS,
			target.getZ() + 0.5
		);
		ItemStack ammo = Items.ARROW.getDefaultInstance();
		ProbeArrow arrow = new ProbeArrow(helper.getLevel(), spawn.x, spawn.y, spawn.z, ammo, bow);
		Projectile.spawnProjectile(arrow, helper.getLevel(), ammo);
		arrow.shoot(0.0, -2.0, 0.0, 2.0F, 0.0F);
	}

	private static void shootAtWall(final GameTestHelper helper, final ItemStack bow, final BlockPos wallCenter) {
		Vec3 spawn = new Vec3(
			wallCenter.getX() - 3.0,
			wallCenter.getY() + 0.5,
			wallCenter.getZ() + 0.5
		);
		ItemStack ammo = Items.ARROW.getDefaultInstance();
		ProbeArrow arrow = new ProbeArrow(helper.getLevel(), spawn.x, spawn.y, spawn.z, ammo, bow);
		Projectile.spawnProjectile(arrow, helper.getLevel(), ammo);
		arrow.shoot(1.0, 0.0, 0.0, 2.0F, 0.0F);
	}

	private static ItemStack bowWith(final GameTestHelper helper, final Identifier... ids) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (Identifier id : ids) {
			Enchantment enchantment = registry.getValue(id);
			if (enchantment == null) {
				throw new IllegalStateException("the probe enchantment " + id + " is not in the registry");
			}

			enchantments.set(registry.wrapAsHolder(enchantment), 1);
		}

		ItemStack bow = new ItemStack(Items.BOW);
		EnchantmentHelper.setEnchantments(bow, enchantments.toImmutable());
		return bow;
	}
}
