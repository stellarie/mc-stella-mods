package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Clue books in chests: how to open the tower, found where crying obsidian and old climbers lie. */
public class TowerClueGameTest {
	private static final List<String> TABLES = List.of(
		"chests/ruined_portal", "chests/simple_dungeon", "chests/stronghold_library", "chests/stronghold_corridor", "chests/stronghold_crossing",
		"chests/bastion_treasure", "chests/bastion_other", "chests/bastion_bridge", "chests/bastion_hoglin_stable"
	);

	private static Identifier table(final String path) {
		return Identifier.withDefaultNamespace(path);
	}

	/** Every clue book tells how to open the way in, and says what else it adds. */
	@GameTest
	public void everyClueBookTellsHowToOpenTheTower(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		java.util.Set<String> titles = new java.util.HashSet<>();
		for (TowerClues.Kind kind : TowerClues.Kind.values()) {
			ItemStack book = TowerClues.of(kind);
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			// The way in is the first page's whole business.
			String text = content.pages().get(0).raw().getString();
			titles.add(content.title().raw());
			if (!book.is(Items.WRITTEN_BOOK) || TowerClues.teaches(book) != kind) {
				faults.add(kind + " is not marked as itself");
			}
			if (!text.contains("crying obsidian") || !text.contains("fire") || !text.contains("two blocks wide")) {
				faults.add(kind + " does not tell the way in");
			}
			if (content.generation() != 3) {
				faults.add(kind + " can be copied");
			}
		}
		TowerTestSupport.result("clueBooks", "kinds=" + TowerClues.Kind.values().length + " titles=" + titles.size() + " faults=" + faults);
		helper.assertTrue(faults.isEmpty(), String.join("; ", faults));
		helper.assertTrue(titles.size() == TowerClues.Kind.values().length, "the books share titles: " + titles);
		helper.assertTrue(TowerClues.teaches(new ItemStack(Items.WRITTEN_BOOK)) == null, "a plain book was taken for a clue");
		helper.succeed();
	}

	/** Each table holds a clue at its own chance, measured over many draws; any other table holds none. */
	@GameTest
	public void chestsHoldCluesAtTheirTablesChance(final GameTestHelper helper) {
		int draws = 20_000;
		List<String> faults = new ArrayList<>();
		StringBuilder rates = new StringBuilder();
		RandomSource random = RandomSource.create(41L);
		for (String path : TABLES) {
			Identifier table = table(path);
			double chance = TowerClues.chanceOf(table);
			int held = 0;
			for (int i = 0; i < draws; i++) {
				List<ItemStack> drops = new ArrayList<>();
				TowerClues.modifyChestDrops(table, true, random, drops);
				held += drops.stream().anyMatch(drop -> TowerClues.teaches(drop) != null) ? 1 : 0;
			}
			double bound = 6.0 * Math.sqrt(chance * (1.0 - chance) / draws);
			rates.append(path).append('=').append(held / (double) draws).append(' ');
			if (chance <= 0.0 || Math.abs(held / (double) draws - chance) > bound) {
				faults.add(path + " held " + held / (double) draws + ", not " + chance);
			}
		}
		int stray = 0;
		for (String other : List.of("chests/village/village_plains_house", "chests/buried_treasure", "chests/end_city_treasure", "entities/zombie")) {
			for (int i = 0; i < 2000; i++) {
				List<ItemStack> drops = new ArrayList<>();
				TowerClues.modifyChestDrops(table(other), true, random, drops);
				stray += drops.size();
			}
		}
		// A data pack's own version of a listed table holds none.
		List<ItemStack> packed = new ArrayList<>();
		for (int i = 0; i < 2000; i++) {
			TowerClues.modifyChestDrops(table("chests/ruined_portal"), false, random, packed);
		}
		TowerTestSupport.result("clueRates", rates + "stray=" + stray + " packed=" + packed.size());
		helper.assertTrue(faults.isEmpty(), String.join("; ", faults));
		helper.assertTrue(stray == 0, stray + " clues turned up in tables that hold none");
		helper.assertTrue(packed.isEmpty(), packed.size() + " clues turned up in a data pack's table");
		helper.succeed();
	}

	/** Through the game's own loot rolls: a ruined portal's chest holds a clue at its chance, a village's never. */
	@GameTest
	public void aRuinedPortalsRealChestHoldsClues(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int rolls = 3000;
		int ruined = countClues(level, "chests/ruined_portal", rolls);
		int village = countClues(level, "chests/village/village_plains_house", rolls);
		double chance = TowerClues.chanceOf(table("chests/ruined_portal"));
		double bound = 6.0 * Math.sqrt(chance * (1.0 - chance) / rolls);
		TowerTestSupport.result("realChest", "ruined=" + ruined / (double) rolls + " want=" + chance + " village=" + village);
		helper.assertTrue(Math.abs(ruined / (double) rolls - chance) < bound, "a ruined portal's chest held a clue " + ruined / (double) rolls + " of rolls, not " + chance);
		helper.assertTrue(village == 0, "a village chest held " + village + " clues");
		helper.succeed();
	}

	private static int countClues(final ServerLevel level, final String path, final int rolls) {
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, table(path));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
		int clues = 0;
		for (int i = 0; i < rolls; i++) {
			clues += table.getRandomItems(params).stream().anyMatch(drop -> TowerClues.teaches(drop) != null) ? 1 : 0;
		}
		return clues;
	}
}
