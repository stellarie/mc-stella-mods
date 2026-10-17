package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** The castle's treasure: chests in the plan's places, loot tables that roll, and items only the End holds. */
public class CastleTreasureGameTest {
	static List<ItemStack> roll(final ServerLevel end, final ResourceKey<LootTable> key) {
		LootTable table = end.getServer().reloadableRegistries().getLootTable(key);
		LootParams params = new LootParams.Builder(end).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
		return new ArrayList<>(table.getRandomItems(params));
	}

	static boolean holds(final List<ItemStack> stacks, final Item item) {
		return stacks.stream().anyMatch(stack -> stack.is(item));
	}

	@GameTest(environment = "mod-stella-end-test:treasure_chests", maxTicks = 20)
	public void everyFloorHoldsItsTreasureAsThreeDoubleChestsAndNoSingleOne(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		BlockPos min = EndCastle.min();
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			int[] types = new int[ChestType.values().length];
			for (int x = 0; x < EndCastle.SIZE; x++) {
				for (int z = 0; z < EndCastle.SIZE; z++) {
					for (int y = EndCastle.standY(floor); y < EndCastle.slabY(floor) + EndCastle.FLOOR_HEIGHT; y++) {
						BlockState state = EndTestSupport.state(end, min, x, y, z);
						if (state.is(Blocks.CHEST)) {
							types[state.getValue(ChestBlock.TYPE).ordinal()]++;
						}
					}
				}
			}
			if (types[ChestType.SINGLE.ordinal()] != 0 || types[ChestType.LEFT.ordinal()] != 3 || types[ChestType.RIGHT.ordinal()] != 3) {
				bad.append(" floor ").append(floor).append(" holds ").append(types[ChestType.SINGLE.ordinal()]).append(" single, ")
					.append(types[ChestType.LEFT.ordinal()]).append(" left and ").append(types[ChestType.RIGHT.ordinal()]).append(" right halves");
			}
			int y = EndCastle.standY(floor);
			for (CastleLayout.Chest spot : EndCastles.layout(end).floor(floor).chests()) {
				BlockState left = EndTestSupport.state(end, min, spot.x(), y, spot.z());
				BlockState right = EndTestSupport.state(end, min, spot.partnerX(), y, spot.partnerZ());
				boolean pair = left.is(Blocks.CHEST) && right.is(Blocks.CHEST) && left.getValue(ChestBlock.TYPE) == ChestType.LEFT
					&& right.getValue(ChestBlock.TYPE) == ChestType.RIGHT && left.getValue(ChestBlock.FACING) == right.getValue(ChestBlock.FACING)
					&& ChestBlock.getConnectedDirection(left) == spot.facing().getClockWise();
				ResourceKey<LootTable> expected = spot.vault() ? CastleTreasure.vaultLoot(floor) : CastleTreasure.floorLoot();
				ResourceKey<LootTable> onLeft = end.getBlockEntity(EndTestSupport.world(min, spot.x(), y, spot.z())) instanceof ChestBlockEntity chest ? chest.getLootTable() : null;
				ResourceKey<LootTable> onRight = end.getBlockEntity(EndTestSupport.world(min, spot.partnerX(), y, spot.partnerZ())) instanceof ChestBlockEntity chest ? chest.getLootTable() : null;
				if (!pair) {
					bad.append(" floor ").append(floor).append(" chest at ").append(spot.x()).append(',').append(spot.z()).append(" is not a double chest");
				} else if (!expected.equals(onLeft) || onRight != null) {
					bad.append(" floor ").append(floor).append(" chest at ").append(spot.x()).append(',').append(spot.z()).append(" rolls ").append(onLeft).append(" and ").append(onRight)
						.append(", expected ").append(expected.identifier()).append(" on one half");
				}
			}
		}
		EndTestSupport.result("treasureChests", "bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "chest faults:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:treasure_vaults", maxTicks = 100)
	public void everyVaultHoldsAVoidShardAndTheFloorsRelicAndABookWhenTheSpellsModIsPresent(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.end(helper);
		TagKey<Item> books = TagKey.create(Registries.ITEM, StellaEnd.id("vault_books"));
		boolean booksExist = BuiltInRegistries.ITEM.get(books).map(named -> named.size() > 0).orElse(false);
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			for (int attempt = 0; attempt < 20; attempt++) {
				List<ItemStack> loot = roll(end, CastleTreasure.vaultLoot(floor));
				if (!holds(loot, EndItems.shard())) {
					bad.append(" floor ").append(floor).append(" vault has no void shard");
				}
				// Floors 2 to 5 each hold their own relic; floor 6 holds one of the four.
				boolean relic = floor >= 2 && floor <= 5 ? holds(loot, EndItems.relics().get(floor - 2))
					: loot.stream().anyMatch(stack -> EndItems.relics().stream().anyMatch(stack::is));
				if (floor >= 2 && !relic) {
					bad.append(" floor ").append(floor).append(" vault has no relic");
				}
				if (booksExist && loot.stream().noneMatch(stack -> stack.is(books))) {
					bad.append(" floor ").append(floor).append(" vault has no spell book");
				}
			}
		}
		EndTestSupport.result("treasureVaults", "booksExist=" + booksExist + " bad=" + bad.length());
		helper.assertTrue(bad.isEmpty(), "vault faults:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:treasure_floor_table", maxTicks = 100)
	public void theRandomChestTableRollsLootEveryTime(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.end(helper);
		int empty = 0;
		int items = 0;
		for (int attempt = 0; attempt < 50; attempt++) {
			List<ItemStack> loot = roll(end, CastleTreasure.floorLoot());
			empty += loot.isEmpty() ? 1 : 0;
			items += loot.size();
		}
		EndTestSupport.result("treasureFloorTable", "empty=" + empty + " itemsPerRoll=" + (items / 50.0));
		helper.assertTrue(empty == 0, empty + " of 50 rolls were empty");
		helper.assertTrue(items >= 50 * 3, "only " + items + " items in 50 rolls");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:treasure_items", maxTicks = 20)
	public void theVoidShardAndFourRelicsExistAndEachRelicCarriesFixedAttributes(final GameTestHelper helper) {
		StringBuilder bad = new StringBuilder();
		if (!BuiltInRegistries.ITEM.containsKey(StellaEnd.id(EndItems.SHARD_ID))) {
			bad.append(" no void shard");
		}
		if (EndItems.relics().size() != 4) {
			bad.append(" ").append(EndItems.relics().size()).append(" relics");
		}
		for (Item relic : EndItems.relics()) {
			ItemAttributeModifiers modifiers = relic.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
			Identifier id = BuiltInRegistries.ITEM.getKey(relic);
			if (modifiers.modifiers().isEmpty()) {
				bad.append(' ').append(id).append(" has no attribute bonus");
			}
		}
		helper.assertTrue(bad.isEmpty(), "End items:" + bad);
		helper.succeed();
	}
}
