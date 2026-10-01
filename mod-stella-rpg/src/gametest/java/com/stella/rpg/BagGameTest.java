package com.stella.rpg;

import com.stella.rpg.bag.Bag;
import com.stella.rpg.tomb.TombstoneBlockEntity;
import com.stella.rpg.tomb.Tombstones;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** The bag: saved, opened as a three-row chest menu, filled by pickups, moved by shift-click, and buried. */
public final class BagGameTest {
	@GameTest
	public void persists(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		Bag bag = Bag.of(player);
		bag.set(0, new ItemStack(Items.DIAMOND, 3));
		bag.set(Bag.SIZE - 1, new ItemStack(Items.IRON_SWORD));
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = Bag.CODEC.encodeStart(ops, bag).getOrThrow();
		Bag loaded = Bag.CODEC.parse(ops, saved).getOrThrow();
		boolean same = ItemStack.matches(loaded.get(0), bag.get(0)) && ItemStack.matches(loaded.get(Bag.SIZE - 1), bag.get(Bag.SIZE - 1))
			&& loaded.get(1).isEmpty();
		RpgTestSupport.result("persists", "first=" + loaded.get(0) + " last=" + loaded.get(Bag.SIZE - 1));
		helper.assertTrue(same, "the bag codec lost a stack");
		helper.succeed();
	}

	@GameTest
	public void pickupOverflow(final GameTestHelper helper) {
		ServerPlayer roomy = RpgTestSupport.player(helper);
		boolean roomyAdded = roomy.getInventory().add(new ItemStack(Items.DIAMOND, 5));
		boolean bagUntouched = Bag.of(roomy).get(0).isEmpty();
		ServerPlayer full = RpgTestSupport.player(helper);
		full.setGameMode(GameType.SURVIVAL);
		for (int i = 0; i < 36; i++) {
			full.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
		}
		ItemStack pickup = new ItemStack(Items.DIAMOND, 5);
		boolean fullAdded = full.getInventory().add(pickup);
		ItemStack inBag = Bag.of(full).get(0);
		RpgTestSupport.result("pickupOverflow", "roomyAdded=" + roomyAdded + " bagUntouched=" + bagUntouched
			+ " fullAdded=" + fullAdded + " inBag=" + inBag + " left=" + pickup.getCount());
		helper.assertTrue(roomyAdded && bagUntouched, "a pickup skipped the main inventory");
		helper.assertTrue(fullAdded && inBag.is(Items.DIAMOND) && inBag.getCount() == 5 && pickup.isEmpty(), "a full inventory did not overflow");
		helper.succeed();
	}

	@GameTest
	public void bagMenu(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		ChestMenu menu = Bag.menu(1, player.getInventory(), player);
		player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 5));
		int inventorySlot = Bag.SIZE;
		menu.clicked(inventorySlot, 0, ContainerInput.PICKUP, player);
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack inBag = Bag.of(player).get(0);
		RpgTestSupport.result("bagMenu", "rows=" + menu.getRowCount() + " slots=" + menu.slots.size() + " inBag=" + inBag);
		helper.assertTrue(menu.getRowCount() == Bag.ROWS && menu.slots.size() == Bag.SIZE + 36, "the bag menu has the wrong shape");
		helper.assertTrue(inBag.is(Items.DIAMOND) && inBag.getCount() == 5 && menu.getCarried().isEmpty(), "the click did not move the stack");
		helper.succeed();
	}

	@GameTest
	public void shiftClickBothWays(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		ChestMenu menu = Bag.menu(1, player.getInventory(), player);
		Bag.of(player).set(0, new ItemStack(Items.EMERALD, 7));
		menu.quickMoveStack(player, 0);
		int inMain = player.getInventory().countItem(Items.EMERALD);
		boolean out = inMain == 7 && Bag.of(player).get(0).isEmpty();
		player.getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 3));
		menu.quickMoveStack(player, Bag.SIZE);
		boolean in = Bag.of(player).get(0).is(Items.GOLD_INGOT) && player.getInventory().getItem(9).isEmpty();
		RpgTestSupport.result("shiftClickBothWays", "out=" + out + " in=" + in + " bag0=" + Bag.of(player).get(0));
		helper.assertTrue(out && in, "shift-click did not move between the bag and the inventory");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void tombstone(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Bag.of(player).set(0, new ItemStack(Items.DIAMOND, 3));
		Bag.of(player).set(1, new ItemStack(Items.EMERALD, 2));
		BlockPos death = player.blockPosition();
		Tombstones.setPicker((candidates, random) -> List.of(Tombstones.BAG_CANDIDATE_BASE));
		ServerPlayer reborn;
		try {
			reborn = TombstoneGameTest.dieAndRespawn(helper, player);
		} finally {
			Tombstones.resetPicker();
		}
		TombstoneBlockEntity tomb = TombstoneGameTest.tombstoneOf(helper.getLevel(), death, player.getUUID());
		Bag kept = Bag.of(reborn);
		RpgTestSupport.result("tombstone", "tomb=" + (tomb == null ? null : tomb.contents()) + " kept0=" + kept.get(0) + " kept1=" + kept.get(1));
		helper.assertTrue(tomb != null && tomb.contents().size() == 1 && tomb.contents().getFirst().is(Items.DIAMOND), "the bag stack is not buried");
		helper.assertTrue(kept.get(0).isEmpty() && kept.get(1).is(Items.EMERALD) && kept.get(1).getCount() == 2, "the bag did not keep the rest");
		helper.succeed();
	}
}
