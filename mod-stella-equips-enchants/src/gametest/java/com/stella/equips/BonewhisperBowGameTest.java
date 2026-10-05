package com.stella.equips;

import com.stella.equips.unique.BonewhisperBow;
import com.stella.equips.unique.UniqueArrows;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.Uniques;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The Bonewhisper Bow: the extra arrows of its base effect, each tier gate, and the sure triple at +15. */
public class BonewhisperBowGameTest {
	private static final int ARROWS = 64;
	private static final long SETTLE = 14L;

	private static ServerPlayer archer(final GameTestHelper helper, final int refine) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(4.5, 2.0, 4.5));
		player.setXRot(-90.0F);
		ItemStack bow = UniqueTestKit.stack(def, refine);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		player.getInventory().add(new ItemStack(Items.ARROW, ARROWS));
		return player;
	}

	private static int arrowItems(final ServerPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.ARROW)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void shoot(final ServerPlayer player) {
		ItemStack bow = player.getMainHandItem();
		bow.getItem().releaseUsing(bow, player.level(), player, 0);
	}

	/** Base effect: a passing roll looses two more arrows 3 ticks apart, at no arrow cost, and none can be picked up. */
	@GameTest(maxTicks = 100)
	public void aPassingRollLoosesTwoMoreArrowsAtNoCost(final GameTestHelper helper) {
		ServerPlayer player = archer(helper, 0);
		ServerLevel level = player.level();
		Vec3 around = player.position();
		UniqueTestKit.rigBone(player.getUUID(), true);
		int before = arrowItems(player);
		shoot(player);
		int atOnce = UniqueTestKit.arrowsOf(level, player, around).size();
		int spentAtOnce = before - arrowItems(player);
		helper.runAfterDelay(SETTLE, () -> {
			List<AbstractArrow> all = UniqueTestKit.arrowsOf(level, player, around);
			List<AbstractArrow> extras = UniqueTestKit.boneExtras(player.getUUID());
			List<UniqueTestKit.BoneCall> calls = UniqueTestKit.boneCalls(player.getUUID());
			int spent = before - arrowItems(player);
			boolean noPickup = extras.stream().allMatch(arrow -> arrow.pickup == AbstractArrow.Pickup.DISALLOWED);
			boolean marked = extras.stream().allMatch(arrow -> {
				UniqueArrows.Mark mark = UniqueArrows.of(arrow);
				return mark != null && mark.id().equals(BonewhisperBow.ID) && mark.refine() == 0;
			});
			List<AbstractArrow> originals = all.stream().filter(arrow -> extras.stream().noneMatch(extra -> extra.getUUID().equals(arrow.getUUID()))).toList();
			boolean originalPickup = originals.size() == 1 && originals.get(0).pickup == AbstractArrow.Pickup.ALLOWED;
			UniqueTestKit.result("bonewhisperBase", "atOnce=" + atOnce + " total=" + all.size() + " extras=" + extras.size() + " spent=" + spent
				+ " noPickup=" + noPickup + " chance=" + calls.get(0).chance());
			helper.assertTrue(atOnce == 1, "only the first arrow flies at once, got " + atOnce);
			helper.assertTrue(spentAtOnce == 1, "the shot spent " + spentAtOnce + " arrows");
			helper.assertTrue(calls.size() == 1 && calls.get(0).chance() == BonewhisperBow.TUNING.base(), "the roller saw " + calls);
			helper.assertTrue(extras.size() == BonewhisperBow.TUNING.extraArrows(), "the bow loosed " + extras.size() + " extra arrows");
			helper.assertTrue(all.size() == 1 + BonewhisperBow.TUNING.extraArrows(), "arrows in flight: " + all.size());
			helper.assertTrue(spent == 1, "the extra arrows cost " + (spent - 1) + " arrows from the inventory");
			helper.assertTrue(noPickup && originalPickup, "pickup rules: extras closed " + noPickup + ", original open " + originalPickup);
			helper.assertTrue(marked, "an extra arrow is not marked as a Bonewhisper arrow");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aFailingRollLoosesOnlyTheShot(final GameTestHelper helper) {
		ServerPlayer player = archer(helper, 0);
		ServerLevel level = player.level();
		Vec3 around = player.position();
		UniqueTestKit.rigBone(player.getUUID(), false);
		shoot(player);
		helper.runAfterDelay(SETTLE, () -> {
			int total = UniqueTestKit.arrowsOf(level, player, around).size();
			UniqueTestKit.result("bonewhisperMiss", "arrows=" + total + " calls=" + UniqueTestKit.boneCalls(player.getUUID()).size());
			helper.assertTrue(total == 1, "a failed roll left " + total + " arrows");
			helper.assertTrue(UniqueTestKit.boneExtras(player.getUUID()).isEmpty(), "extras were recorded");
			helper.succeed();
		});
	}

	@GameTest
	public void theChanceGrowsAtPlus7AndPlus12(final GameTestHelper helper) {
		BonewhisperBow.Tuning tuning = BonewhisperBow.TUNING;
		List<Double> seen = new ArrayList<>();
		for (int level : List.of(0, 6, 7, 11, 12, 15)) {
			ServerPlayer player = archer(helper, level);
			UniqueTestKit.rigBone(player.getUUID(), false);
			shoot(player);
			List<UniqueTestKit.BoneCall> calls = UniqueTestKit.boneCalls(player.getUUID());
			helper.assertTrue(calls.size() == 1, "+" + level + " rolled " + calls.size() + " times");
			seen.add(calls.get(0).chance());
			double expected = level >= 12 ? tuning.plus12() : level >= 7 ? tuning.plus7() : tuning.base();
			helper.assertTrue(calls.get(0).chance() == expected, "+" + level + " rolled at " + calls.get(0).chance() + ", expected " + expected);
			helper.assertTrue(BonewhisperBow.chanceAt(level) == expected, "chanceAt(" + level + ") is " + BonewhisperBow.chanceAt(level));
		}
		UniqueTestKit.result("bonewhisperChance", "chances=" + seen);
		helper.assertTrue(tuning.base() < tuning.plus7() && tuning.plus7() < tuning.plus12(), "the tuning does not grow");
		helper.succeed();
	}

	/** +10: extras pierce one more mob than the shot. Below +10 they pierce the same. */
	@GameTest(maxTicks = 100)
	public void extraArrowsPierceOneMoreFromPlus10(final GameTestHelper helper) {
		ServerPlayer low = archer(helper, Uniques.PLUS_10 - 1);
		ServerPlayer high = archer(helper, Uniques.PLUS_10);
		ServerLevel level = low.level();
		UniqueTestKit.rigBone(low.getUUID(), true);
		UniqueTestKit.rigBone(high.getUUID(), true);
		shoot(low);
		shoot(high);
		helper.runAfterDelay(SETTLE, () -> {
			List<AbstractArrow> lowExtras = UniqueTestKit.boneExtras(low.getUUID());
			List<AbstractArrow> highExtras = UniqueTestKit.boneExtras(high.getUUID());
			// The shot is a plain arrow with no Piercing enchantment, so its pierce level is 0.
			List<Integer> lowPierce = lowExtras.stream().map(UniqueTestKit::pierceAtSpawn).toList();
			List<Integer> highPierce = highExtras.stream().map(UniqueTestKit::pierceAtSpawn).toList();
			UniqueTestKit.result("bonewhisperPierce", "low=" + lowPierce + " high=" + highPierce);
			helper.assertTrue(lowExtras.size() == 2 && highExtras.size() == 2, "extras: " + lowExtras.size() + " and " + highExtras.size());
			helper.assertTrue(lowPierce.stream().allMatch(pierce -> pierce == 0), "at +9 the extras pierce " + lowPierce + ", like the shot");
			helper.assertTrue(highPierce.stream().allMatch(pierce -> pierce == BonewhisperBow.TUNING.piercePlus()),
				"at +10 the extras pierce " + highPierce + ", expected " + BonewhisperBow.TUNING.piercePlus() + " more than the shot");
			helper.succeed();
		});
	}

	/** +15: every 5th shot is a sure triple, and +14 has no such shot. A failing roll cannot stop it. */
	@GameTest(maxTicks = 200)
	public void everyFifthShotIsASureTripleAtPlus15(final GameTestHelper helper) {
		ServerPlayer at15 = archer(helper, Uniques.PLUS_15);
		ServerPlayer at14 = archer(helper, Uniques.PLUS_15 - 1);
		ServerLevel level = at15.level();
		UniqueTestKit.rigBone(at15.getUUID(), false);
		UniqueTestKit.rigBone(at14.getUUID(), false);
		int every = BonewhisperBow.TUNING.guaranteedEvery();
		for (int shot = 1; shot <= every; shot++) {
			shoot(at15);
			shoot(at14);
		}
		helper.runAfterDelay(SETTLE + 10L, () -> {
			int total15 = UniqueTestKit.boneExtras(at15.getUUID()).size();
			int total14 = UniqueTestKit.boneExtras(at14.getUUID()).size();
			int flying15 = UniqueTestKit.arrowsOf(level, at15, at15.position()).size();
			UniqueTestKit.result("bonewhisperTriple", "shots=" + BonewhisperBow.shots(at15) + " extras15=" + total15 + " extras14=" + total14
				+ " flying15=" + flying15);
			helper.assertTrue(BonewhisperBow.shots(at15) == every, "the shot counter reads " + BonewhisperBow.shots(at15));
			helper.assertTrue(total15 == BonewhisperBow.TUNING.extraArrows(), "five shots at +15 with failing rolls gave " + total15 + " extras, expected exactly one triple");
			helper.assertTrue(flying15 == every + BonewhisperBow.TUNING.extraArrows(), "arrows in flight at +15: " + flying15);
			helper.assertTrue(total14 == 0, "five shots at +14 gave " + total14 + " extras with failing rolls");
			helper.succeed();
		});
	}
}
