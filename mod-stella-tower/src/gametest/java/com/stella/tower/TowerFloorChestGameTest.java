package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** The chests a built floor carries: one double chest at each of the plan's spots, stocked once when the floor goes up. */
public class TowerFloorChestGameTest {
	private static int filled(final ServerLevel tower, final BlockPos first) {
		BlockState state = tower.getBlockState(first);
		if (!(state.getBlock() instanceof ChestBlock chest)) {
			return -1;
		}
		Container container = ChestBlock.getContainer(chest, state, tower, first, true);
		int stacks = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			stacks += container.getItem(i).isEmpty() ? 0 : 1;
		}
		return stacks;
	}

	private static Container containerAt(final ServerLevel tower, final BlockPos pos) {
		BlockState state = tower.getBlockState(pos);
		return ChestBlock.getContainer((ChestBlock) state.getBlock(), state, tower, pos, true);
	}

	private static int has(final Container container, final net.minecraft.world.item.Item item) {
		int found = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			found += container.getItem(i).is(item) ? 1 : 0;
		}
		return found;
	}

	private static int enchantedBooks(final Container container) {
		int found = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			found += stack.is(Items.ENCHANTED_BOOK) && TowerChests.enchanted(stack) ? 1 : 0;
		}
		return found;
	}

	/** Every tower chest block in the floor's volume. */
	private static int chestBlocks(final ServerLevel tower, final int floor) {
		BlockPos o = TowerLayout.origin(floor);
		int found = 0;
		for (int x = 0; x < TowerLayout.SIZE; x++) {
			for (int z = 0; z < TowerLayout.SIZE; z++) {
				for (int y = 0; y < TowerLayout.HEIGHT; y++) {
					found += tower.getBlockState(o.offset(x, y, z)).is(TowerBlocks.TOWER_CHEST) ? 1 : 0;
				}
			}
		}
		return found;
	}

	/** Both halves are tower chests, a pair, facing the spot's way; returns a fault or null. */
	private static String fault(final ServerLevel tower, final TowerPlan plan, final TowerPlan.ChestSpot spot) {
		BlockPos first = plan.origin().offset(spot.x(), spot.y(), spot.z());
		TowerPlan.Spot other = spot.second();
		BlockPos second = plan.origin().offset(other.x(), spot.y(), other.z());
		BlockState a = tower.getBlockState(first);
		BlockState b = tower.getBlockState(second);
		if (!a.is(TowerBlocks.TOWER_CHEST) || !b.is(TowerBlocks.TOWER_CHEST)) {
			return "no chest at " + spot;
		}
		if (a.getValue(ChestBlock.FACING) != spot.facing() || b.getValue(ChestBlock.FACING) != spot.facing()) {
			return "wrong facing at " + spot;
		}
		if (a.getValue(ChestBlock.TYPE) == ChestType.SINGLE || b.getValue(ChestBlock.TYPE) == ChestType.SINGLE
			|| a.getValue(ChestBlock.TYPE) == b.getValue(ChestBlock.TYPE)) {
			return "not a pair at " + spot;
		}
		if (filled(tower, first) <= 0) {
			return "empty at " + spot;
		}
		return null;
	}

	private static TowerPlan build(final ServerLevel tower, final int floor) {
		TowerChests.installSpellItems(TowerChestGameTest.standIn(true));
		try {
			TowerFloors.ensure(tower, floor);
		} finally {
			TowerChests.installSpellItems(null);
		}
		return TowerPlan.at(tower, floor);
	}

	@GameTest(maxTicks = 200)
	public void ordinaryFloorHasFilledChestsAtItsSpots(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerPlan plan = build(tower, floor);
		List<String> faults = new ArrayList<>();
		for (TowerPlan.ChestSpot spot : plan.chestSpots()) {
			String fault = fault(tower, plan, spot);
			if (fault != null) {
				faults.add(fault);
			}
		}
		int blocks = chestBlocks(tower, floor);
		TowerTestSupport.result("floorChestsOrdinary", "floor=" + floor + " variation=" + plan.variation() + " spots=" + plan.chestSpots().size()
			+ " chestBlocks=" + blocks + " faults=" + faults.size());
		helper.assertTrue(plan.chestSpots().size() >= TowerChestSpots.MIN_SPOTS, "the plan has only " + plan.chestSpots().size() + " spots");
		helper.assertTrue(faults.isEmpty(), "chest faults: " + faults);
		helper.assertTrue(blocks == 2 * plan.chestSpots().size(), "the floor holds " + blocks + " chest blocks for " + plan.chestSpots().size() + " spots");
		helper.succeed();
	}

	private void guaranteed(final GameTestHelper helper, final String name, final boolean boss) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY == 0 && TowerBuffs.isBossFloor(f) == boss, 60);
		TowerPlan plan = build(tower, floor);
		List<TowerPlan.ChestSpot> kept = plan.chestSpots().stream().filter(TowerPlan.ChestSpot::guaranteed).toList();
		helper.assertTrue(kept.size() == 1, "the plan has " + kept.size() + " guaranteed spots");
		TowerPlan.ChestSpot spot = kept.get(0);
		String fault = fault(tower, plan, spot);
		TowerMaze.Zone zone = plan.safeZones().get(boss ? 1 : 0);
		TowerPlan.Spot other = spot.second();
		boolean inside = zone.contains(spot.x(), spot.z()) && zone.contains(other.x(), other.z());
		Container container = containerAt(tower, plan.origin().offset(spot.x(), spot.y(), spot.z()));
		int books = enchantedBooks(container);
		int scrolls = has(container, Items.PAPER);
		TowerTestSupport.result(name, "floor=" + floor + " fault=" + fault + " insideZone=" + inside + " enchantedBooks=" + books + " returnScrolls=" + scrolls);
		helper.assertTrue(fault == null, "guaranteed chest: " + fault);
		helper.assertTrue(inside, "the guaranteed chest is outside the " + (boss ? "boss room" : "entrance zone"));
		helper.assertTrue(books >= 1, "the guaranteed chest holds no enchanted book");
		helper.assertTrue(scrolls >= 1, "the guaranteed chest holds no return scroll");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void eliteFloorHasItsGuaranteedChestInTheEntranceZone(final GameTestHelper helper) {
		guaranteed(helper, "floorChestsElite", false);
	}

	@GameTest(maxTicks = 200)
	public void bossFloorHasItsGuaranteedChestInTheBossRoom(final GameTestHelper helper) {
		guaranteed(helper, "floorChestsBoss", true);
	}

	@GameTest(maxTicks = 200)
	public void builtFloorIsNotRestockedOnReentry(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerPlan plan = build(tower, floor);
		TowerPlan.ChestSpot spot = plan.chestSpots().get(0);
		BlockPos first = plan.origin().offset(spot.x(), spot.y(), spot.z());
		containerAt(tower, first).clearContent();
		build(tower, floor);
		int after = filled(tower, first);
		TowerTestSupport.result("floorChestsNotRestocked", "floor=" + floor + " stacksAfterReentry=" + after);
		helper.assertTrue(after == 0, "an emptied chest was refilled: " + after + " stacks");
		helper.succeed();
	}
}
