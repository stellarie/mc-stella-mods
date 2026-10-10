package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A plain arrow hit rolls the DEX dice and the die of the bow, and lands inside the range the Atk readout shows.
 * The roll already lives in {@link RpgCombat#scaleOutgoing}, so a new scaling step would double count. These cases pin that.
 */
public final class ArrowDiceGameTest {
	private static final int SAMPLES = 3000;

	private static DamageSource arrowFrom(final ServerLevel level, final ServerPlayer player) {
		Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem().copy());
		level.addFreshEntity(arrow);
		return level.damageSources().arrow(arrow, player);
	}

	/** The shot damage in K units that the readout compares, with the arrow's own damage left out. */
	private static double[] sample(final GameTestHelper helper, final int dex) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.setStat(player, Stat.DEX, dex);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		double[] preview = AttackPreview.ranged(player, dex);
		DamageSource source = arrowFrom(level, player);
		double min = Double.MAX_VALUE;
		double max = 0.0;
		double total = 0.0;
		// LUK is 0, so no shot crits and no roller needs replacing.
		for (int i = 0; i < SAMPLES; i++) {
			double hit = RpgCombat.scaleOutgoing(victim, source, 0.0F) * RpgScale.k();
			min = Math.min(min, hit);
			max = Math.max(max, hit);
			total += hit;
		}
		return new double[] {preview[0], preview[1], min, max, total / SAMPLES};
	}

	@GameTest
	public void aPlainArrowLandsInsideTheReadout(final GameTestHelper helper) {
		for (int dex : new int[] {20, 70}) {
			double[] result = sample(helper, dex);
			RpgTestSupport.result("aPlainArrowLandsInsideTheReadout", "dex=" + dex + " readout=" + result[0] + "-" + result[1]
				+ " seen=" + result[2] + "-" + result[3] + " mean=" + result[4]);
			helper.assertTrue(result[2] >= result[0] - 1e-3 && result[3] <= result[1] + 1e-3,
				"DEX " + dex + " shots " + result[2] + "-" + result[3] + " left the readout " + result[0] + "-" + result[1]);
			helper.assertTrue(result[3] > result[2], "DEX " + dex + " rolled one number: " + result[2]);
			// DEX raises the chance of a maximum die, so the mean sits above the middle but inside the range.
			helper.assertTrue(result[4] > result[0] + (result[1] - result[0]) * 0.2 && result[4] < result[1], "DEX " + dex + " mean " + result[4] + " readout " + result[0] + "-" + result[1]);
		}
		helper.succeed();
	}

	@GameTest
	public void aPlainArrowDiffersBetweenDexValues(final GameTestHelper helper) {
		double[] low = sample(helper, 20);
		double[] high = sample(helper, 70);
		RpgTestSupport.result("aPlainArrowDiffersBetweenDexValues", "mean20=" + low[4] + " mean70=" + high[4]);
		helper.assertTrue(high[4] > low[4] * 1.1, "DEX 70 averaged " + high[4] + " against " + low[4] + " at DEX 20");
		helper.assertTrue(high[1] > low[1] && high[0] > low[0], "the readout did not move with DEX");
		helper.succeed();
	}

	/**
	 * Two real arrows hit two husks. At DEX 300 every die rolls its maximum, so that hit is exact. A DEX 0 hit lies inside its readout.
	 * The arrow's own damage is the same in both, so the difference of the health lost is the difference of the dice alone.
	 * No global roller is set, because the cases run in parallel and this one spans ticks.
	 */
	@GameTest(maxTicks = 200)
	public void aFlyingArrowHitRollsTheDice(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		Husk low = bigHusk(helper, 1);
		Husk high = bigHusk(helper, 6);
		RpgTestSupport.setStat(player, Stat.DEX, 0);
		double[] lowReadout = AttackPreview.ranged(player, 0);
		fire(level, player, low);
		// The stat is read when the arrow lands, so the first arrow lands before DEX changes.
		helper.runAfterDelay(15, () -> {
			double lostLow = low.getMaxHealth() - low.getHealth();
			RpgTestSupport.setStat(player, Stat.DEX, 300);
			double[] highReadout = AttackPreview.ranged(player, 300);
			fire(level, player, high);
			helper.runAfterDelay(15, () -> {
				double lostHigh = high.getMaxHealth() - high.getHealth();
				double guard = RpgTestSupport.mobGuard(2.0F) - RpgTestSupport.mobGuard(1.0F);
				// Health is in K units, the same units as the readout.
				double least = (highReadout[1] - lowReadout[1]) * guard;
				double most = (highReadout[1] - lowReadout[0]) * guard;
				RpgTestSupport.result("aFlyingArrowHitRollsTheDice", "lost=" + lostLow + "/" + lostHigh + " delta=" + (lostHigh - lostLow) + " between=" + least + ".." + most);
				helper.assertTrue(lostLow > 0.0 && lostHigh > 0.0, "an arrow missed: " + lostLow + " " + lostHigh);
				double delta = lostHigh - lostLow;
				helper.assertTrue(delta >= least * 0.999 - 0.5 && delta <= most * 1.001 + 0.5, "a real hit at DEX 300 lost " + delta + " more, the dice give " + least + " to " + most);
				helper.assertTrue(delta > 0.5, "the dice did not move the hit: " + delta);
				helper.succeed();
			});
		});
	}

	private static Husk bigHusk(final GameTestHelper helper, final int x) {
		Husk zombie = helper.spawnWithNoFreeWill(EntityTypes.HUSK, x, 1, 6);
		RpgTestSupport.bare(zombie);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0E7);
		zombie.setHealth(zombie.getMaxHealth());
		return zombie;
	}

	/** An arrow that names the held bow, aimed along the row from three blocks before the zombie. */
	private static void fire(final ServerLevel level, final ServerPlayer player, final Husk target) {
		Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem().copy());
		arrow.setPos(target.getX(), target.getY() + 1.2, target.getZ() - 3.0);
		arrow.shoot(0.0, 0.0, 1.0, 2.0F, 0.0F);
		level.addFreshEntity(arrow);
	}
}
