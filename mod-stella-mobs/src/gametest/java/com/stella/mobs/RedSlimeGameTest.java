package com.stella.mobs;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.entity.RedSlime;
import com.stella.mobs.entity.StellaEntities;
import com.stella.mobs.entity.StellaSlime;
import com.stella.mobs.entity.spawn.RedSlimeSpawnRule;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/** The red slime: a beginner mob with a per-biome cap, a refill delay, two spawn windows, and its own loot. */
public class RedSlimeGameTest {
	private static final int LOOT_ROLLS = 400;

	@GameTest(maxTicks = 100)
	public void statsAndTargeting(final GameTestHelper helper) {
		// The test level starts peaceful, where no mob can target a player.
		helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		RedSlime slime = helper.spawn(StellaEntities.RED_SLIME, new Vec3(2.5, 1.0, 2.5));
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		Vec3 spot = helper.absoluteVec(new Vec3(5.5, 1.0, 5.5));
		player.setPos(spot.x, spot.y, spot.z);
		double health = slime.getAttributeBaseValue(Attributes.MAX_HEALTH);
		double damage = slime.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		MobCategory category = StellaEntities.RED_SLIME.getCategory();
		boolean elite = Elite.canBeElite(slime);
		helper.succeedWhen(() -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "redSlimeStats", "health=" + health + " damage=" + damage + " category=" + category
				+ " size=" + slime.getSize() + " elite=" + elite + " target=" + slime.getTarget());
			helper.assertTrue(health == RedSlime.BASE_HEALTH && damage == RedSlime.ATTACK_DAMAGE,
				"base health " + health + " and damage " + damage);
			helper.assertTrue(category == MobCategory.MISC && slime.getSize() == StellaSlime.SIZE && !elite,
				"category " + category + ", size " + slime.getSize() + ", elite " + elite);
			helper.assertTrue(slime.getTarget() == player, "the red slime targets " + slime.getTarget());
		});
	}

	@GameTest
	public void neverSplits(final GameTestHelper helper) {
		RedSlime slime = helper.spawn(StellaEntities.RED_SLIME, new Vec3(2.5, 1.0, 2.5));
		slime.kill(helper.getLevel());
		helper.runAfterDelay(2, () -> {
			int left = helper.getLevel().getEntities(StellaEntities.RED_SLIME,
				other -> other.isAlive() && other.distanceToSqr(slime) < 64.0).size();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "redSlimeSplit", "left=" + left);
			helper.assertTrue(left == 0, "a dead red slime left " + left + " slimes");
			helper.succeed();
		});
	}

	@GameTest
	public void timeWindows(final GameTestHelper helper) {
		long[] inside = {0L, 2999L, 11000L, 13999L, 24000L + 1000L};
		long[] outside = {3000L, 6000L, 10999L, 14000L, 18000L, 23999L};
		List<Long> wrong = new ArrayList<>();
		for (long time : inside) {
			if (!RedSlimeSpawnRule.inWindow(time)) {
				wrong.add(time);
			}
		}
		for (long time : outside) {
			if (RedSlimeSpawnRule.inWindow(time)) {
				wrong.add(time);
			}
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "redSlimeWindows", "wrong=" + wrong);
		helper.assertTrue(wrong.isEmpty(), "the spawn window is wrong at " + wrong);
		helper.succeed();
	}

	/** The rule fills a biome to five, then a death closes that biome for ten seconds. */
	@GameTest(maxTicks = 100)
	public void capAndRefill(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ChunkPos chunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
		Holder<Biome> biome = level.getBiome(origin);
		long clock = 1_000_000L;
		java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
		level.getEntities(StellaEntities.RED_SLIME, slime -> true).forEach(slime -> before.add(slime.getUUID()));
		com.stella.mobs.entity.spawn.SlimeFalloff.setRoller((random, chance) -> true);
		com.stella.mobs.entity.spawn.RedSlimeSpawnRule.setPlayerGap(0.0);
		try {
			int spawned = 0;
			for (int attempt = 0; attempt < 40 && RedSlimeSpawnRule.countInBiome(level, biome) < RedSlimeSpawnRule.MAX_PER_BIOME; attempt++) {
				if (RedSlimeSpawnRule.INSTANCE.trySpawn(level, chunk, clock)) {
					spawned++;
				}
			}
			boolean overCap = RedSlimeSpawnRule.INSTANCE.trySpawn(level, chunk, clock);
			int full = RedSlimeSpawnRule.countInBiome(level, biome);
			List<? extends RedSlime> ours = level.getEntities(StellaEntities.RED_SLIME,
				slime -> slime.isAlive() && !before.contains(slime.getUUID()) && level.getBiome(slime.blockPosition()).is(biome));
			helper.assertTrue(!ours.isEmpty(), "the rule spawned no red slime");
			ours.getFirst().kill(level);
			long refill = RedSlimeSpawnRule.INSTANCE.cooldownUntil(level, biome);
			boolean early = RedSlimeSpawnRule.INSTANCE.trySpawn(level, chunk, refill - 1);
			boolean late = false;
			for (int attempt = 0; attempt < 10 && !late; attempt++) {
				late = RedSlimeSpawnRule.INSTANCE.trySpawn(level, chunk, refill);
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "redSlimeCap", "spawned=" + spawned + " full=" + full + " overCap=" + overCap
				+ " refillDelay=" + (refill - com.stella.mobs.entity.spawn.StellaSpawner.clock(level)) + " early=" + early + " late=" + late);
			helper.assertTrue(full == RedSlimeSpawnRule.MAX_PER_BIOME && !overCap, "the biome holds " + full + ", over cap " + overCap);
			helper.assertTrue(refill - com.stella.mobs.entity.spawn.StellaSpawner.clock(level) == RedSlimeSpawnRule.REFILL_TICKS,
				"the refill waits " + (refill - com.stella.mobs.entity.spawn.StellaSpawner.clock(level)) + " ticks");
			helper.assertTrue(!early && late, "the refill spawned early=" + early + " late=" + late);
			for (RedSlime slime : level.getEntities(StellaEntities.RED_SLIME, slime -> slime.isAlive() && !before.contains(slime.getUUID()))) {
				slime.discard();
			}
		} finally {
			com.stella.mobs.entity.spawn.SlimeFalloff.setRoller(null);
			com.stella.mobs.entity.spawn.RedSlimeSpawnRule.setPlayerGap(null);
		}
		RedSlimeSpawnRule.INSTANCE.reset();
		helper.succeed();
	}

	/** A player at the chunk's centre keeps every spot in it inside the 24-block ring, so no slime spawns. */
	@GameTest(maxTicks = 100)
	public void noSpawnNearAPlayer(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ChunkPos chunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		player.snapTo(chunk.getMiddleBlockX() + 0.5, origin.getY() + 1, chunk.getMiddleBlockZ() + 0.5, 0.0F, 0.0F);
		com.stella.mobs.entity.spawn.SlimeFalloff.setRoller((random, chance) -> true);
		int red = 0;
		try {
			for (int attempt = 0; attempt < 20; attempt++) {
				if (RedSlimeSpawnRule.INSTANCE.trySpawn(level, chunk, 1_000_000L)) {
					red++;
				}
			}
		} finally {
			com.stella.mobs.entity.spawn.SlimeFalloff.setRoller(null);
			RedSlimeSpawnRule.INSTANCE.reset();
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "noSpawnNearAPlayer", "red=" + red);
		helper.assertTrue(red == 0, red + " red slimes spawned within 24 blocks of a player");
		helper.succeed();
	}

	@GameTest
	public void loot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RedSlime slime = StellaEntities.RED_SLIME.create(level, EntitySpawnReason.EVENT);
		slime.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)), 0.0F, 0.0F);
		int potions = 0;
		int badBalls = 0;
		int badPotions = 0;
		for (int roll = 0; roll < LOOT_ROLLS; roll++) {
			List<ItemStack> drops = new ArrayList<>();
			slime.dropFromLootTable(level, level.damageSources().generic(), false, slime.getLootTable().orElseThrow(), drops::add);
			int balls = drops.stream().filter(stack -> stack.is(Items.SLIME_BALL)).mapToInt(ItemStack::getCount).sum();
			if (balls < 1 || balls > 3) {
				badBalls++;
			}
			for (ItemStack stack : drops) {
				if (stack.is(Items.POTION)) {
					potions++;
					if (!stack.get(DataComponents.POTION_CONTENTS).is(Potions.HEALING)) {
						badPotions++;
					}
				}
			}
		}
		double rate = potions / (double) LOOT_ROLLS;
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", "redSlimeLoot", "rolls=" + LOOT_ROLLS + " potionRate=" + rate + " badBalls=" + badBalls + " badPotions=" + badPotions);
		helper.assertTrue(badBalls == 0, badBalls + " rolls fell outside 1 to 3 slime balls");
		// Five standard deviations at p = 0.5 over 400 rolls is 0.125.
		helper.assertTrue(Math.abs(rate - 0.5) <= 0.125 && badPotions == 0, "potion rate " + rate + ", wrong potions " + badPotions);
		helper.succeed();
	}
}
