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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
	public void everyFloorHoldsTwoRandomChestsAndOneVaultAtItsFixedPlace(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		BlockPos min = EndCastle.min();
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			int chests = 0;
			for (int x = 0; x < EndCastle.SIZE; x++) {
				for (int z = 0; z < EndCastle.SIZE; z++) {
					for (int y = EndCastle.standY(floor); y < EndCastle.slabY(floor) + EndCastle.FLOOR_HEIGHT; y++) {
						chests += EndTestSupport.state(end, min, x, y, z).is(Blocks.CHEST) ? 1 : 0;
					}
				}
			}
			if (chests != 3) {
				bad.append(" floor ").append(floor).append(" holds ").append(chests).append(" chests");
			}
			for (CastleLayout.Chest spot : EndCastles.layout(end).floor(floor).chests()) {
				BlockPos at = EndTestSupport.world(min, spot.x(), EndCastle.standY(floor), spot.z());
				ResourceKey<LootTable> expected = spot.vault() ? CastleTreasure.vaultLoot(floor) : CastleTreasure.floorLoot();
				if (!(end.getBlockEntity(at) instanceof ChestBlockEntity chest) || !expected.equals(chest.getLootTable())) {
					bad.append(" floor ").append(floor).append(" chest at ").append(spot.x()).append(',').append(spot.z()).append(" does not hold ").append(expected.identifier());
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
