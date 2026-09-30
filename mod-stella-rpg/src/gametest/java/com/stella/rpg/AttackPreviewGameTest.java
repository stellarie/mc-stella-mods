package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Regression guard: the Atk range comes from the held item, because the client never syncs attack damage. */
public final class AttackPreviewGameTest {
	@GameTest
	public void rangeUsesTheHeldWeapon(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.bare(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		// No tick has run, so the attribute still reads the bare base, as it does on the client.
		double attribute = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		double[] weak = AttackPreview.range(player, 0);
		double[] strong = AttackPreview.range(player, 50);
		RpgTestSupport.result("rangeUsesTheHeldWeapon", "attribute=" + attribute + " weak=" + weak[0] + "-" + weak[1]
			+ " strong=" + strong[0] + "-" + strong[1]);
		helper.assertTrue(attribute == 1.0, "the fixture attribute already held the sword: " + attribute);
		// Iron sword: attack 6, iron weapon die 1d4, material x1.2. STR 0 rolls 1d4, STR 50 rolls 3d4 and adds status ATK 75 / K.
		helper.assertTrue(close(weak[0], (6.0 + 2 * 1.2) * 15.0) && close(weak[1], (6.0 + 8 * 1.2) * 15.0),
			"STR 0 iron sword read " + weak[0] + "-" + weak[1]);
		helper.assertTrue(close(strong[0], (6.0 + 4 * 1.2 + 5.0) * 15.0) && close(strong[1], (6.0 + 16 * 1.2 + 5.0) * 15.0),
			"STR 50 iron sword read " + strong[0] + "-" + strong[1]);
		helper.succeed();
	}

	@GameTest
	public void rangeIncludesWeaponDiceAndMultipliers(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.bare(player);
		player.experienceLevel = 20;
		// A +15 diamond sword: attack 7, STR 0 dice 1d4, weapon die 1d6 plus 3 refine dice,
		// and the dice multiplier (1 + 0.02 x 20) x 1.4 x 3.45.
		player.setItemInHand(InteractionHand.MAIN_HAND, WeaponDiceGameTest.refined(Items.DIAMOND_SWORD, 15));
		double[] range = AttackPreview.range(player, 0);
		double multiplier = 1.4 * 1.4 * 3.45;
		double low = (7.0 + (1 + 4) * multiplier) * 15.0;
		double high = (7.0 + (4 + 24) * multiplier) * 15.0;
		RpgTestSupport.result("rangeIncludesWeaponDiceAndMultipliers", "range=" + range[0] + "-" + range[1]);
		helper.assertTrue(close(range[0], low) && close(range[1], high), "a +15 diamond sword at level 20 read " + range[0] + "-" + range[1]
			+ " expected " + low + "-" + high);
		helper.succeed();
	}

	private static boolean close(final double actual, final double expected) {
		return Math.abs(actual - expected) < 1.0E-6;
	}
}
