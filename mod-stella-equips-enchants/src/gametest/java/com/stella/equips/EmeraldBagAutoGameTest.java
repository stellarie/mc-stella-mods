package com.stella.equips;

import com.stella.equips.bag.EmeraldBag;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Acquired emeralds go into an emerald bag with its checkbox on, in the inventory or in the rpg bag. */
public final class EmeraldBagAutoGameTest {
	@GameTest
	public void pickupGoesToBag(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.getInventory().setItem(5, new ItemStack(EmeraldBag.ITEM));
		player.getInventory().add(new ItemStack(Items.EMERALD, 10));
		ItemEntity drop = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.EMERALD, 7));
		drop.setNoPickUpDelay();
		helper.getLevel().addFreshEntity(drop);
		drop.playerTouch(player);
		int stored = EmeraldBag.stored(player.getInventory().getItem(5));
		int loose = EmeraldBag.loose(player.getInventory());
		StellaEquips.LOGGER.info("[BEHAVIOR] test=bagAutoPickup RESULT stored={} loose={} dropGone={}", stored, loose, drop.isRemoved());
		helper.assertTrue(stored == 17 && loose == 0 && drop.isRemoved(), "the bag holds " + stored + " and " + loose + " stayed loose");
		helper.succeed();
	}

	@GameTest
	public void toggleOff(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EmeraldBag.ITEM));
		boolean set = EmeraldBag.setAuto(player, new EmeraldBag.AutoRequest(false, false));
		player.getInventory().add(new ItemStack(Items.EMERALD, 12));
		int stored = EmeraldBag.stored(player.getMainHandItem());
		int loose = EmeraldBag.loose(player.getInventory());
		StellaEquips.LOGGER.info("[BEHAVIOR] test=bagAutoOff RESULT set={} stored={} loose={}", set, stored, loose);
		helper.assertTrue(set && !EmeraldBag.isAuto(player.getMainHandItem()), "the checkbox did not turn off");
		helper.assertTrue(stored == 0 && loose == 12, "with the checkbox off the bag took " + stored);
		helper.succeed();
	}

	@GameTest
	public void withdrawStaysLoose(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack bag = new ItemStack(EmeraldBag.ITEM);
		bag.set(EmeraldBag.EMERALDS, 64);
		player.setItemInHand(InteractionHand.MAIN_HAND, bag);
		int moved = EmeraldBag.handle(player, new EmeraldBag.Request(false, false, 16));
		int stored = EmeraldBag.stored(player.getMainHandItem());
		int loose = EmeraldBag.loose(player.getInventory());
		StellaEquips.LOGGER.info("[BEHAVIOR] test=bagAutoWithdraw RESULT moved={} stored={} loose={}", moved, stored, loose);
		helper.assertTrue(moved == 16 && stored == 48 && loose == 16, "a withdraw left " + stored + " in the bag and " + loose + " loose");
		helper.succeed();
	}

	/** Without the rpg mod, a stand-in share proves the path: a bag kept in the rpg bag fills through setItem. */
	@GameTest
	public void rpgBagShare(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		SimpleContainer rpgBag = new SimpleContainer(27);
		rpgBag.setItem(3, new ItemStack(EmeraldBag.ITEM));
		Function<Player, Container> view = owner -> owner == player ? rpgBag : new SimpleContainer(0);
		var share = FabricLoader.getInstance().getObjectShare();
		Object previous = share.get(EmeraldBag.RPG_BAG_SHARE);
		share.put(EmeraldBag.RPG_BAG_SHARE, view);
		try {
			player.getInventory().add(new ItemStack(Items.EMERALD, 9));
		} finally {
			if (previous == null) {
				share.remove(EmeraldBag.RPG_BAG_SHARE);
			} else {
				share.put(EmeraldBag.RPG_BAG_SHARE, previous);
			}
		}
		int stored = EmeraldBag.stored(rpgBag.getItem(3));
		int loose = EmeraldBag.loose(player.getInventory());
		StellaEquips.LOGGER.info("[BEHAVIOR] test=bagAutoRpg RESULT stored={} loose={}", stored, loose);
		helper.assertTrue(stored == 9 && loose == 0, "the rpg bag's emerald bag holds " + stored + " and " + loose + " stayed loose");
		helper.succeed();
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		return player;
	}
}
