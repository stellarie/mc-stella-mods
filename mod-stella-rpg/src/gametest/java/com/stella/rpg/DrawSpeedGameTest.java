package com.stella.rpg;

import com.stella.rpg.job.JobItems;
import com.stella.rpg.net.StatsSyncPayload;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Full bow draw and crossbow charge shorten with AGI and DEX, on every projectile weapon, from the use tick that both sides run.
 * The headless harness has no client, so the client half is checked through the payload the client reads, and the animation is a manual item.
 */
public final class DrawSpeedGameTest {
	private static final double PER_POINT = 0.0025;
	private static final double CAP = 0.50;

	private static Method updateUsingItem;

	/** Runs the per tick use hook of the entity, the same one {@code LivingEntity#tick} runs on either side. */
	private static void useTicks(final LivingEntity entity, final int ticks) {
		try {
			if (updateUsingItem == null) {
				updateUsingItem = LivingEntity.class.getDeclaredMethod("updateUsingItem", ItemStack.class);
				updateUsingItem.setAccessible(true);
			}
			for (int i = 0; i < ticks; i++) {
				updateUsingItem.invoke(entity, entity.getUseItem());
			}
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
			throw new AssertionError(exception);
		}
	}

	private static ServerPlayer drawer(final GameTestHelper helper, final ItemStack weapon, final int agi, final int dex) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.AGI, agi);
		RpgTestSupport.setStat(player, Stat.DEX, dex);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		player.getInventory().add(new ItemStack(Items.ARROW, 16));
		player.startUsingItem(InteractionHand.MAIN_HAND);
		return player;
	}

	private static int drawnAfter(final GameTestHelper helper, final ItemStack weapon, final int agi, final int dex, final int ticks) {
		ServerPlayer player = drawer(helper, weapon, agi, dex);
		useTicks(player, ticks);
		int drawn = player.getTicksUsingItem();
		player.stopUsingItem();
		return drawn;
	}

	@GameTest
	public void reductionTable(final GameTestHelper helper) {
		int[][] cases = {{0, 0}, {40, 0}, {0, 40}, {50, 50}, {100, 100}, {150, 50}, {300, 0}, {1000, 1000}};
		StringBuilder log = new StringBuilder();
		for (int[] pair : cases) {
			double expected = Math.min(CAP, PER_POINT * (pair[0] + pair[1]));
			double actual = DrawSpeed.reduction(pair[0], pair[1]);
			log.append(pair[0]).append("+").append(pair[1]).append("=").append(actual).append(" ");
			helper.assertTrue(Math.abs(actual - expected) < 1e-9, "reduction(" + pair[0] + ", " + pair[1] + ")=" + actual + " expected " + expected);
		}
		RpgTestSupport.result("reductionTable", log.toString().trim());
		helper.assertTrue(DrawSpeed.reduction(100, 0) == DrawSpeed.reduction(0, 100), "AGI and DEX must count equally");
		helper.succeed();
	}

	@GameTest
	public void aBowDrawShortensWithAgiAndDex(final GameTestHelper helper) {
		int plain = drawnAfter(helper, new ItemStack(Items.BOW), 0, 0, 20);
		int half = drawnAfter(helper, new ItemStack(Items.BOW), 50, 50, 20);
		int dexOnly = drawnAfter(helper, new ItemStack(Items.BOW), 0, 100, 20);
		int agiOnly = drawnAfter(helper, new ItemStack(Items.BOW), 100, 0, 20);
		int capped = drawnAfter(helper, new ItemStack(Items.BOW), 1000, 1000, 20);
		RpgTestSupport.result("aBowDrawShortensWithAgiAndDex", "plain=" + plain + " 100pts=" + half + " dex100=" + dexOnly + " agi100=" + agiOnly + " capped=" + capped);
		helper.assertTrue(plain == 20, "a plain draw read " + plain + " ticks in 20");
		// 100 points is 25 percent shorter, so 20 ticks hold the work of about 26.7.
		helper.assertTrue(half >= 26 && half <= 27, "AGI 50 + DEX 50 read " + half);
		helper.assertTrue(dexOnly == agiOnly && dexOnly == half, "AGI and DEX did not count equally: " + dexOnly + " " + agiOnly + " " + half);
		// The cap is 50 percent: a full draw takes 10 ticks, so 20 ticks hold the work of 40.
		helper.assertTrue(capped >= 39 && capped <= 41, "the cap read " + capped);
		helper.succeed();
	}

	@GameTest
	public void aCrossbowChargeShortens(final GameTestHelper helper) {
		ServerPlayer probe = RpgTestSupport.player(helper);
		int duration = CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), probe);
		int plain = drawnAfter(helper, new ItemStack(Items.CROSSBOW), 0, 0, duration);
		int fast = drawnAfter(helper, new ItemStack(Items.CROSSBOW), 100, 100, duration);
		RpgTestSupport.result("aCrossbowChargeShortens", "duration=" + duration + " plain=" + plain + " fast=" + fast);
		helper.assertTrue(plain == duration, "a plain charge read " + plain + " of " + duration);
		helper.assertTrue(fast >= duration * 3 / 2, "a 50 percent charge read " + fast + " of " + duration);
		helper.succeed();
	}

	@GameTest
	public void everyProjectileWeaponIsSped(final GameTestHelper helper) {
		int mechanical = drawnAfter(helper, new ItemStack(JobItems.MECHANICAL_BOW), 100, 100, 20);
		helper.assertTrue(mechanical >= 39, "a mechanical bow read " + mechanical);
		helper.succeed();
	}

	@GameTest
	public void otherUseIsUntouched(final GameTestHelper helper) {
		int eaten = drawnAfter(helper, new ItemStack(Items.APPLE), 1000, 1000, 10);
		helper.assertTrue(eaten == 10, "eating read " + eaten + " ticks in 10");
		helper.succeed();
	}

	@GameTest
	public void mobsAreUntouched(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawnMob(EntityTypes.SKELETON, 1, 1, 2).withNoFreeWill().spawn();
		MobGrowth.grow(skeleton, Stat.AGI, 400.0);
		MobGrowth.grow(skeleton, Stat.DEX, 400.0);
		helper.assertTrue(StatBonuses.effective(skeleton, Stat.AGI) >= 400, "the skeleton has no AGI to draw with");
		skeleton.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		skeleton.startUsingItem(InteractionHand.MAIN_HAND);
		useTicks(skeleton, 20);
		int drawn = skeleton.getTicksUsingItem();
		skeleton.stopUsingItem();
		helper.assertTrue(drawn == 20, "a skeleton drew " + drawn + " ticks in 20");
		helper.succeed();
	}

	@GameTest
	public void theClientReadsTheSameStats(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RandomSource random = RandomSource.create(4L);
		boolean moved = false;
		for (int i = 0; i < 40; i++) {
			RpgTestSupport.setStat(player, Stat.AGI, random.nextInt(400));
			RpgTestSupport.setStat(player, Stat.DEX, random.nextInt(400));
			StatsSyncPayload payload = StatsSyncPayload.of(player);
			double server = DrawSpeed.reductionOf(player);
			double client = DrawSpeed.reduction(payload);
			moved |= server > 0.0;
			helper.assertTrue(Math.abs(server - client) < 1e-12, "server " + server + " client " + client);
			helper.assertTrue(Math.abs(server - DrawSpeed.reduction(StatBonuses.effective(player, Stat.AGI), StatBonuses.effective(player, Stat.DEX))) < 1e-12,
				"the entity path and the stat path disagree");
		}
		helper.assertTrue(moved, "no sampled stat gave a reduction");
		helper.succeed();
	}

	@GameTest
	public void fullPowerArrivesSooner(final GameTestHelper helper) {
		boolean slow = fullPowerAfter(helper, 0, 0, 15);
		boolean quick = fullPowerAfter(helper, 100, 100, 15);
		RpgTestSupport.result("fullPowerArrivesSooner", "plain=" + slow + " quick=" + quick);
		helper.assertTrue(!slow, "15 ticks gave full power with no stats");
		helper.assertTrue(quick, "15 ticks gave no full power at 50 percent");
		helper.succeed();
	}

	private static boolean fullPowerAfter(final GameTestHelper helper, final int agi, final int dex, final int ticks) {
		ServerLevel level = helper.getLevel();
		ItemStack bow = new ItemStack(Items.BOW);
		ServerPlayer player = drawer(helper, bow, agi, dex);
		List<Arrow> known = level.getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0));
		useTicks(player, ticks);
		player.releaseUsingItem();
		List<Arrow> fresh = level.getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0)).stream().filter(arrow -> !known.contains(arrow)).toList();
		helper.assertTrue(fresh.size() == 1, "arrows released=" + fresh.size());
		boolean full = fresh.get(0).isCritArrow();
		fresh.forEach(Arrow::discard);
		return full;
	}

	/**
	 * The pull animation and the field of view run on the client, which this harness does not have.
	 * By hand: with AGI plus DEX at 100, hold a bow and confirm the pull frames finish in 15 ticks, then the same for a crossbow.
	 */
	@GameTest(required = false)
	public void theClientAnimationNeedsAClientRequiresManualTest(final GameTestHelper helper) {
		helper.assertTrue(false, "by hand: watch the bow pull and the crossbow charge finish sooner with AGI plus DEX at 100");
	}
}
