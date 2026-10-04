package com.stella.spells;

import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Items left in the retired quick slots move back into the inventory, and the slots are cleared. */
public class QuickSlotsDrainGameTest {
	@GameTest
	public void retiredSlotsEmptyIntoTheInventory(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		((AttachmentTarget) player).setAttached(QuickSlots.TYPE, List.of(
			new ItemStack(Items.BREAD, 5), ItemStack.EMPTY, new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY));
		int moved = QuickSlots.drain(player);
		int bread = player.getInventory().countItem(Items.BREAD);
		int swords = player.getInventory().countItem(Items.DIAMOND_SWORD);
		boolean cleared = ((AttachmentTarget) player).getAttached(QuickSlots.TYPE) == null;
		Spellbooks.LOGGER.info("[BEHAVIOR] test=quickSlotsDrain RESULT moved={} bread={} swords={} cleared={}", moved, bread, swords, cleared);
		helper.assertTrue(moved == 2 && bread == 5 && swords == 1 && cleared, "drain moved " + moved + ", bread " + bread + ", swords " + swords);
		helper.succeed();
	}
}
