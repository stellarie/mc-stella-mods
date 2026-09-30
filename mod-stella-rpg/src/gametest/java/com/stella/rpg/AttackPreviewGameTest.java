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
		helper.assertTrue(close(weak[0], 105.0) && close(weak[1], 150.0), "STR 0 iron sword read " + weak[0] + "-" + weak[1]);
		helper.assertTrue(close(strong[0], 210.0) && close(strong[1], 345.0), "STR 50 iron sword read " + strong[0] + "-" + strong[1]);
		helper.succeed();
	}

	private static boolean close(final double actual, final double expected) {
		return Math.abs(actual - expected) < 1.0E-6;
	}
}
