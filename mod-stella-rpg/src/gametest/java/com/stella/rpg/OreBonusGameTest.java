package com.stella.rpg;

import com.stella.rpg.roll.MiningRolls;
import com.stella.rpg.roll.OreExperience;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Depth and LUK at the ore: experience grows with depth, and each LUK point raises the chance of a rolled drop. */
public final class OreBonusGameTest {
	private static final int BREAKS = 200;
	private static final int DEEP_Y = -64;

	@GameTest(maxTicks = 100)
	public void oreExperienceGrowsWithDepth(final GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.getMinY() <= DEEP_Y, "the test level starts at " + level.getMinY());
		ServerPlayer miner = RpgTestSupport.realPlayer(helper);
		BlockPos column = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos surface = new BlockPos(column.getX(), OreExperience.SURFACE_Y, column.getZ());
		BlockPos deep = new BlockPos(column.getX(), DEEP_Y, column.getZ());
		long surfaceTotal = 0;
		long deepTotal = 0;
		long machineTotal = 0;
		for (int i = 0; i < BREAKS; i++) {
			int atSurface = playerBreak(level, miner, surface);
			int atDepth = playerBreak(level, miner, deep);
			int unattended = machineBreak(level, deep);
			// Diamond ore gives 3 to 7. At y -64 every amount is a multiple of 3, and a machine's break is not scaled.
			helper.assertTrue(atSurface >= 3 && atSurface <= 7, "a surface break gave " + atSurface);
			helper.assertTrue(atDepth >= 9 && atDepth <= 21 && atDepth % 3 == 0, "a break at y -64 gave " + atDepth);
			helper.assertTrue(unattended >= 3 && unattended <= 7, "a break with no player at y -64 gave " + unattended);
			surfaceTotal += atSurface;
			deepTotal += atDepth;
			machineTotal += unattended;
		}
		double ratio = deepTotal / (double) surfaceTotal;
		double expected = OreExperience.multiplier(DEEP_Y) / OreExperience.multiplier(OreExperience.SURFACE_Y);
		RpgTestSupport.result("oreExperienceGrowsWithDepth", "surface=" + surfaceTotal + " deep=" + deepTotal + " machine=" + machineTotal + " ratio=" + ratio + " expected=" + expected);
		helper.assertTrue(Math.abs(ratio - expected) <= expected * 0.15, "ore experience at y -64 was " + ratio + " times the surface's, not " + expected);
		helper.succeed();
	}

	@GameTest
	public void lukRaisesTheBonusRollChance(final GameTestHelper helper) {
		int luk = 40;
		double base = RpgConfig.DEFAULT_MATERIAL_ROLLS.rollChance();
		double plain = rolledRate(helper, 0, 6000, 8_000_000L);
		double lucky = rolledRate(helper, luk, 6000, 9_000_000L);
		double expected = MiningRolls.rollChance(base, luk);
		double bound = 4.5 * Math.sqrt(expected * (1 - expected) / 6000);
		RpgTestSupport.result("lukRaisesTheBonusRollChance", "luk0=" + plain + " luk" + luk + "=" + lucky + " expected=" + expected + " bound=" + bound);
		helper.assertTrue(Math.abs(lucky - expected) <= bound, "LUK " + luk + " rolled " + lucky + " of drops, not " + expected + " +- " + bound);
		helper.assertTrue(plain < base + 4.5 * Math.sqrt(base * (1 - base) / 6000), "LUK 0 rolled " + plain);
		helper.assertTrue(lucky > plain * 4, "LUK " + luk + " rolled " + lucky + " against " + plain + " at LUK 0");
		helper.succeed();
	}

	/** The share of raw iron drops that rolled, over many iron ore breaks by a player of the given LUK. */
	private static double rolledRate(final GameTestHelper helper, final int luk, final int breaks, final long seed) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.LUK, luk);
		int drops = 0;
		int rolled = 0;
		for (int i = 0; i < breaks; i++) {
			for (ItemStack stack : MiningRollGameTest.mine(helper, "iron_ore", MiningRollGameTest.ORES.get("iron_ore"), 0.5, new ItemStack(Items.IRON_PICKAXE), player, seed + i)) {
				if (stack.is(Items.RAW_IRON)) {
					drops++;
					rolled += MiningRollGameTest.rolled(stack) ? 1 : 0;
				}
			}
		}
		return rolled / (double) drops;
	}

	/** Breaks a diamond ore as the player, and returns the experience it gave. */
	private static int playerBreak(final ServerLevel level, final ServerPlayer miner, final BlockPos at) throws ReflectiveOperationException {
		// A fresh pickaxe each time, because 600 breaks wear an iron one out and a bare hand drops nothing.
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		miner.setItemInHand(InteractionHand.MAIN_HAND, pick);
		miner.getInventory().setSelectedItem(pick);
		level.setBlockAndUpdate(at, Blocks.DIAMOND_ORE.defaultBlockState());
		if (!miner.gameMode.destroyBlock(at)) {
			throw new AssertionError("the ore at " + at + " did not break");
		}
		return collect(level, at);
	}

	/** Drops a diamond ore with no player, as a piston or an explosion would, and returns the experience it gave. */
	private static int machineBreak(final ServerLevel level, final BlockPos at) throws ReflectiveOperationException {
		level.setBlockAndUpdate(at, Blocks.DIAMOND_ORE.defaultBlockState());
		Block.dropResources(level.getBlockState(at), level, at, null, null, new ItemStack(Items.IRON_PICKAXE));
		level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
		return collect(level, at);
	}

	/** The experience on the ground near a block, counting merged orbs, and clears it with the items. */
	private static int collect(final ServerLevel level, final BlockPos at) throws ReflectiveOperationException {
		Field count = ExperienceOrb.class.getDeclaredField("count");
		count.setAccessible(true);
		int total = 0;
		AABB box = new AABB(at).inflate(3.0);
		for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
			total += orb.getValue() * count.getInt(orb);
			orb.discard();
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
			item.discard();
		}
		return total;
	}
}
