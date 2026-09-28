package com.stella.tower;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** The Void Key: given on entering, one at a time, and it brings its holder back to the portal. */
public class VoidKeyGameTest {
	private static TeleportTransition enter(final GameTestHelper helper, final ServerPlayer player, final BlockPos inside) {
		BlockPos portal = helper.absolutePos(inside);
		return TowerBlocks.TOWER_PORTAL.getPortalDestination(helper.getLevel(), player, portal);
	}

	private static int keys(final ServerPlayer player) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			count += stack.is(TowerItems.VOID_KEY) ? stack.getCount() : 0;
		}
		return count;
	}

	@GameTest
	public void enteringGrantsAVoidKeyThatReturnsYouToThePortal(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 5.5);
		player.teleport(enter(helper, player, inside));
		boolean inTower = player.level() == tower;
		int granted = keys(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TowerItems.VOID_KEY));
		TowerItems.VOID_KEY.use(player.level(), player, InteractionHand.MAIN_HAND);
		double fromPortal = player.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(inside)));
		boolean home = player.level() == helper.getLevel();
		boolean outOfPortal = !helper.getLevel().getBlockState(player.blockPosition()).is(TowerBlocks.TOWER_PORTAL);
		TowerTestSupport.result("voidKeyReturns", "inTower=" + inTower + " granted=" + granted + " home=" + home
			+ " fromPortal=" + fromPortal + " outOfPortal=" + outOfPortal);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(inTower && granted == 1, "entering left the player inTower=" + inTower + " with " + granted + " keys");
		helper.assertTrue(home && fromPortal < 3.0 && outOfPortal, "the key left the player home=" + home + " " + fromPortal + " from the portal");
		helper.succeed();
	}

	@GameTest
	public void aPlayerHoldingAKeyIsGivenNoSecond(final GameTestHelper helper) {
		BlockPos inside = TowerTestSupport.frame(helper, 2, Blocks.CRYING_OBSIDIAN);
		helper.setBlock(inside, Blocks.FIRE);
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 5.5);
		player.teleport(enter(helper, player, inside));
		VoidKeyItem.returnHome(player);
		player.teleport(enter(helper, player, inside));
		int held = keys(player);
		TowerTestSupport.result("oneVoidKey", "held=" + held);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(held == 1, "two entries left " + held + " void keys");
		helper.succeed();
	}

	@GameTest
	public void theVoidKeyDoesNothingOutsideTheTower(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 5.5);
		Vec3 before = player.position();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TowerItems.VOID_KEY));
		TowerItems.VOID_KEY.use(player.level(), player, InteractionHand.MAIN_HAND);
		boolean stayed = player.level() == helper.getLevel() && player.position().distanceTo(before) < 0.01;
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(stayed, "the Void Key moved a player outside the tower");
		helper.succeed();
	}
}
