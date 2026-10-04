package com.stella.ruins.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Items 10 to 12: nothing outside the mod leaks an exclusive item, clues turn up at the configured rate, and a clue leads to a site. */
public class RuinLootGameTest {
	static final int TABLE_ROLLS = 50;
	static final int CLUE_ROLLS = 2000;

	@GameTest
	public void noLootTableOutsideTheModNamespaceYieldsAnExclusiveItem(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Identifier> ids = level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(ResourceKey::identifier).toList();
		int rolled = 0;
		int skippedContext = 0;
		int own = 0;
		List<String> leaks = new ArrayList<>();
		for (Identifier id : ids) {
			if (Exclusives.ownsTable(id)) {
				own++;
				continue;
			}
			LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
			Exclusives.Probe probe = Exclusives.probe(level, table, TABLE_ROLLS);
			rolled += probe.rolled() ? 1 : 0;
			skippedContext += probe.rolled() ? 0 : 1;
			if (!probe.exclusives().isEmpty()) {
				leaks.add(id + " -> " + probe.exclusives());
			}
		}
		WorldTestSupport.result("exclusivesStayHome", "tables=" + ids.size() + " rolled=" + rolled + " skipped=" + skippedContext + " ownNamespace=" + own + " leaks=" + leaks.size());
		helper.assertTrue(rolled >= 100, "only " + rolled + " tables were rolled, so the check proves little");
		helper.assertTrue(rolled > skippedContext, "more tables were skipped (" + skippedContext + ") than rolled (" + rolled + ")");
		helper.assertTrue(leaks.isEmpty(), "tables outside the mod yield exclusive items: " + leaks);
		helper.succeed();
	}

	/** A chest table that always drops one item, built from the same JSON a data pack would hold. */
	static LootTable tableOf(final ServerLevel level, final String item) {
		JsonElement json = JsonParser.parseString("{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + item + "\"}]}]}");
		return LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), json).getOrThrow();
	}

	@GameTest
	public void theExclusivityProbeCatchesATableThatLeaksAndIgnoresTheClue(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable leaking = tableOf(level, "mod-stella-ruins:test_relic");
		LootTable clues = tableOf(level, "mod-stella-ruins:ruin_clue");
		Exclusives.Probe leak = Exclusives.probe(level, leaking, TABLE_ROLLS);
		Exclusives.Probe clue = Exclusives.probe(level, clues, TABLE_ROLLS);
		helper.assertTrue(leak.rolled() && leak.exclusives().size() == 1, "the probe missed an exclusive item in a leaking table: " + leak);
		helper.assertTrue(clue.rolled() && clue.exclusives().isEmpty(), "the probe called the clue exclusive: " + clue);
		helper.assertTrue(Exclusives.isExclusive(WorldTestInit.relic) && !Exclusives.isExclusive(RuinClueItem.ITEM) && !Exclusives.isExclusive(Items.DIAMOND), "the exclusivity rule is wrong");
		helper.assertTrue(Exclusives.ownsTable(WorldTestInit.VAULT_LOOT.identifier()) && !Exclusives.ownsTable(Identifier.withDefaultNamespace("chests/simple_dungeon")), "the namespace rule is wrong");
		helper.succeed();
	}

	/** Rolls a vanilla chest table, as a container fills. */
	static List<ItemStack> roll(final ServerLevel level, final LootTable table, final int seed) {
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
		return table.getRandomItems(params, seed);
	}

	@GameTest
	public void clueChestsGiveACluePerTheConfiguredChance(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable dungeon = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/simple_dungeon")));
		double chance = RuinConfig.get().clueChance();
		int clues = 0;
		Map<String, Integer> bySite = new HashMap<>();
		for (int i = 0; i < CLUE_ROLLS; i++) {
			for (ItemStack stack : roll(level, dungeon, 90_000 + i)) {
				if (stack.is(RuinClueItem.ITEM)) {
					clues++;
					bySite.merge(stack.get(RuinClueItem.CLUE_SITE), 1, Integer::sum);
				}
			}
		}
		double rate = (double) clues / CLUE_ROLLS;
		WorldTestSupport.result("clueRate", "rolls=" + CLUE_ROLLS + " clues=" + clues + " rate=" + rate + " configured=" + chance + " bySite=" + bySite);
		helper.assertTrue(chance == RuinConfig.DEFAULT_CLUE_CHANCE, "the configured chance is " + chance + ", not the plan's default");
		helper.assertTrue(Math.abs(rate - chance) <= 0.25 * chance, "the clue rate " + rate + " is outside 25% of " + chance);
		helper.assertTrue(bySite.containsKey("test_surface") && bySite.containsKey("test_cave") && bySite.size() == 2, "the clues did not follow the sites' weights: " + bySite);
		helper.assertTrue(bySite.get("test_surface") > bySite.get("test_cave"), "the weight 3 site did not beat the weight 1 site: " + bySite);
		helper.succeed();
	}

	@GameTest
	public void aClueChanceOfZeroGivesNoClue(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable dungeon = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/simple_dungeon")));
		RuinConfig.Values old = RuinConfig.replace(RuinConfig.get().withClueChance(0.0));
		int clues = 0;
		try {
			for (int i = 0; i < 400; i++) {
				clues += (int) roll(level, dungeon, 5_000 + i).stream().filter(stack -> stack.is(RuinClueItem.ITEM)).count();
			}
		} finally {
			RuinConfig.replace(old);
		}
		helper.assertTrue(clues == 0, "a chance of 0 still gave " + clues + " clues");
		helper.succeed();
	}

	@GameTest
	public void everyVanillaChestTableCarriesTheCluePoolButNothingElseDoes(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinConfig.Values old = RuinConfig.replace(RuinConfig.get().withClueChance(1.0));
		int chests = 0;
		int withClue = 0;
		int others = 0;
		List<String> missing = new ArrayList<>();
		try {
			for (Identifier id : level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(ResourceKey::identifier).toList()) {
				LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
				if (!"minecraft".equals(id.getNamespace()) || !table.getParamSet().equals(LootContextParamSets.CHEST)) {
					continue;
				}
				boolean chest = RuinClues.isChestTable(id);
				boolean got = false;
				for (int i = 0; i < 20 && !got; i++) {
					got = roll(level, table, i).stream().anyMatch(stack -> stack.is(RuinClueItem.ITEM));
				}
				if (chest) {
					chests++;
					withClue += got ? 1 : 0;
					if (!got) {
						missing.add(id.toString());
					}
				} else {
					others += got ? 1 : 0;
				}
			}
		} finally {
			RuinConfig.replace(old);
		}
		WorldTestSupport.result("clueTables", "chestTables=" + chests + " withClue=" + withClue + " otherChestParamTablesWithClue=" + others);
		helper.assertTrue(chests >= 20, "only " + chests + " chest tables were found");
		helper.assertTrue(missing.isEmpty(), "chest tables without a clue at chance 1.0: " + missing);
		helper.assertTrue(others == 0, others + " tables outside chests/ gave a clue");
		helper.succeed();
	}

	/**
	 * Plants a real test_findable start in the region of a chunk: the structure's own generation makes the start, the
	 * start goes into its chunk, and every chunk of the site is placed. The test world does not generate structures by
	 * itself, so the clue search needs a start put there by hand.
	 */
	static net.minecraft.world.level.levelgen.structure.StructureStart plant(final ServerLevel level, final int chunkX, final int chunkZ, final List<net.minecraft.world.level.ChunkPos> forced) {
		var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(StructureGameTest.key("test_findable"));
		var placement = (net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement) level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).get(0);
		net.minecraft.world.level.ChunkPos candidate = placement.getPotentialStructureChunk(level.getSeed(), chunkX, chunkZ);
		for (int cx = candidate.x() - 3; cx <= candidate.x() + 3; cx++) {
			for (int cz = candidate.z() - 3; cz <= candidate.z() + 3; cz++) {
				level.setChunkForced(cx, cz, true);
				forced.add(new net.minecraft.world.level.ChunkPos(cx, cz));
			}
		}
		var start = StructureGameTest.generate(level, "test_findable", candidate);
		level.getChunk(candidate.x(), candidate.z(), net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS).setStartForStructure(holder.value(), start);
		// The structure check cached this chunk as empty when it loaded; a reference is what tells it a start stands here.
		level.structureManager().addReference(start);
		var box = start.getBoundingBox();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				var chunkBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
				start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(1L), chunkBox, new net.minecraft.world.level.ChunkPos(cx, cz));
			}
		}
		return start;
	}

	@GameTest(maxTicks = 400)
	public void usingAClueNearAPlacedSiteTurnsItIntoAFilledMapWithATargetAtThatSite(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.findable, arena -> {
			ServerLevel level = arena.level();
			List<net.minecraft.world.level.ChunkPos> forced = new ArrayList<>();
			RuinClues.Locator old = null;
			try {
				ServerPlayer player = arena.players().get(0);
				var planted = plant(level, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4, forced);
				helper.assertTrue(planted.isValid() && planted.getPieces().get(0) instanceof RuinPiece, "the planted start is not a ruin start");
				RuinFrame frame = ((RuinPiece) planted.getPieces().get(0)).frame();
				LayoutContext marks = LayoutContext.forDryRun(frame);
				WorldTestInit.findable.layout().draw(marks);
				helper.assertTrue(level.getBlockState(marks.markers().get(RuinMarkers.CORE).get(0)).is(RuinBlocks.CORE), "the planted site has no core, so it was not placed");
				var structures = RuinClues.structuresOf(level, "test_findable");
				var placement = (net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement) level.getChunkSource().getGeneratorState().getPlacementsForStructure(structures.get(0)).get(0);
				BlockPos locatePos = placement.getLocatePos(planted.getChunkPos());
				old = RuinClues.replaceLocator((where, siteId, from) -> siteId.equals("test_findable") ? java.util.Optional.of(locatePos) : java.util.Optional.empty());
				ItemStack clue = RuinClueItem.of("test_findable");
				player.setItemInHand(InteractionHand.MAIN_HAND, clue);
				InteractionResult result = RuinClueItem.ITEM.use(level, player, InteractionHand.MAIN_HAND);
				ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
				helper.assertTrue(result.consumesAction(), "using the clue did nothing: " + result);
				helper.assertTrue(held.is(Items.FILLED_MAP), "the clue became " + held.getItem() + ", not a filled map");
				helper.assertTrue(held.get(DataComponents.MAP_ID) != null, "the map has no map id");
				var decorations = held.get(DataComponents.MAP_DECORATIONS);
				helper.assertTrue(decorations != null && decorations.decorations().size() == 1, "the map has no single target decoration");
				var target = decorations.decorations().values().iterator().next();
				helper.assertTrue(target.type().equals(MapDecorationTypes.TARGET_X), "the decoration is " + target.type());
				helper.assertTrue(Math.abs(target.x() - locatePos.getX()) < 1.0 && Math.abs(target.z() - locatePos.getZ()) < 1.0, "the target is at " + target.x() + "," + target.z() + ", not at " + locatePos.toShortString());
				var box = planted.getBoundingBox();
				helper.assertTrue(locatePos.getX() >= box.minX() - 16 && locatePos.getX() <= box.maxX() + 16 && locatePos.getZ() >= box.minZ() - 16 && locatePos.getZ() <= box.maxZ() + 16, "the target " + locatePos.toShortString() + " is not at the planted site " + box);
				helper.assertTrue(held.get(DataComponents.ITEM_NAME) != null, "the map has no name");
				WorldTestSupport.result("clueMap", "target=" + locatePos.toShortString() + " siteBox=" + box + " searchChunks=" + RuinClues.SEARCH_CHUNKS);
			} finally {
				if (old != null) {
					RuinClues.replaceLocator(old);
				}
				forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false));
			}
		});
	}

	/**
	 * Gap: the test world holds {@code generate_structures} false, and the chunk generator then answers every structure
	 * search with nothing. So this check of the real search fails in every run, and the user checks it by hand in a
	 * normal world. It stays in the suite as evidence of the gap. Manual item: with a ruins mod loaded and a clue in the
	 * inventory, use the clue in a survival world and walk to the marked spot.
	 */
	@GameTest(maxTicks = 400, required = false)
	public void gapTestWorldGeneratesNoStructuresSoTheRealClueSearchIsNotObservable(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.findable, arena -> {
			ServerLevel level = arena.level();
			List<net.minecraft.world.level.ChunkPos> forced = new ArrayList<>();
			try {
				ServerPlayer player = arena.players().get(0);
				var planted = plant(level, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4, forced);
				java.util.Optional<BlockPos> found = RuinClues.locate(level, "test_findable", player.blockPosition());
				WorldTestSupport.result("realClueSearch", "generateStructures=" + level.getServer().getWorldGenSettings().options().generateStructures() + " found=" + found + " plantedChunk=" + planted.getChunkPos());
				helper.assertTrue(found.isPresent(), "the real search found no site; the test world generates no structures");
			} finally {
				forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false));
			}
		});
	}

	@GameTest(maxTicks = 400)
	public void aClueToASiteNoStructureNamesKeepsTheClue(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerPlayer player = arena.join(arena.standAt(3, 3));
			ItemStack clue = RuinClueItem.of("no_such_site");
			player.setItemInHand(InteractionHand.MAIN_HAND, clue);
			RuinClueItem.ITEM.use(arena.level(), player, InteractionHand.MAIN_HAND);
			ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
			helper.assertTrue(held.is(RuinClueItem.ITEM) && held.getCount() == 1 && "no_such_site".equals(held.get(RuinClueItem.CLUE_SITE)), "the clue changed: " + held);
		});
	}

	@GameTest
	public void aClueShowsTheSitesRiddleInItsTooltip(final GameTestHelper helper) {
		ItemStack clue = RuinClueItem.of("test_surface");
		List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
		clue.getItem().appendHoverText(clue, net.minecraft.world.item.Item.TooltipContext.EMPTY, net.minecraft.world.item.component.TooltipDisplay.DEFAULT, lines::add, net.minecraft.world.item.TooltipFlag.NORMAL);
		helper.assertTrue(lines.size() == 1 && lines.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents riddle && riddle.getKey().equals("clue.mod-stella-ruins.test_surface"), "the tooltip is " + lines);
		helper.succeed();
	}
}
