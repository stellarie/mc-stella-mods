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
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** The three bag rows: saved, shown in the inventory menu, filled by pickups, emptied by shift-click, and buried. */
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
	public void menuSlots(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		InventoryMenu menu = player.inventoryMenu;
		int first = firstBagSlot(menu);
		long count = menu.slots.stream().filter(slot -> slot instanceof Bag.BagSlot).count();
		player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 5));
		menu.clicked(9, 0, ContainerInput.PICKUP, player);
		menu.clicked(first, 0, ContainerInput.PICKUP, player);
		ItemStack inBag = Bag.of(player).get(0);
		RpgTestSupport.result("menuSlots", "count=" + count + " first=" + first + " size=" + menu.slots.size() + " inBag=" + inBag);
		helper.assertTrue(count == Bag.SIZE && first == menu.slots.size() - Bag.SIZE, "bag slots missing or not last");
		helper.assertTrue(inBag.is(Items.DIAMOND) && inBag.getCount() == 5 && menu.getCarried().isEmpty(), "the click did not move the stack");
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
	public void shiftClickOut(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		Bag.of(player).set(0, new ItemStack(Items.EMERALD, 7));
		InventoryMenu menu = player.inventoryMenu;
		menu.quickMoveStack(player, firstBagSlot(menu));
		int inMain = player.getInventory().countItem(Items.EMERALD);
		RpgTestSupport.result("shiftClickOut", "inMain=" + inMain + " bag=" + Bag.of(player).get(0));
		helper.assertTrue(inMain == 7 && Bag.of(player).get(0).isEmpty(), "shift-click left the bag stack");
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

	private static int firstBagSlot(final InventoryMenu menu) {
		for (Slot slot : menu.slots) {
			if (slot instanceof Bag.BagSlot) {
				return slot.index;
			}
		}
		return -1;
	}
}
