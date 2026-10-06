package com.stella.ruins.site.deepforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Exclusives;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.loot.LootTable;

/** What the site registers and ships as data: its place, its tables, its worldgen files and its exclusivity. */
public class DeepForgeDataGameTest {
	static final int SALT = 702_913_845;

	@GameTest
	public void theSiteIsRegisteredWithThePlanNumbers(final GameTestHelper helper) {
		RuinSite site = ForgeTestSupport.site();
		helper.assertTrue(site.size() == SiteSize.LARGE, "the site is not large");
		helper.assertTrue(site.sizeX() == 64 && site.sizeY() == 40 && site.sizeZ() == 64, "the bounds are " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(site.activationRadius() == 40, "the activation radius is " + site.activationRadius());
		helper.assertTrue(site.clueWeight() == 2, "the clue weight is " + site.clueWeight());
		helper.assertTrue(site.hasUnique() && site.newUnique().is(DeepForgeItems.HAMMER), "the unique is not the hammer");
		helper.assertTrue(site.claim().resetTicks(site.id()) < 0, "the claim is not once");
		helper.assertTrue(site.placement() instanceof Placement.Cave cave && cave.minY() == -40 && cave.maxY() == 0 && cave.minCover() == 12, "the placement is " + site.placement());
		helper.assertTrue(site.roster().isEmpty(), "the site spawns a fixed roster, but its objective spawns by marker");
		helper.assertTrue(Claim.once().resetTicks("x") < 0, "the once claim control is wrong");
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetAreInTheRegistriesWithTheRarityTheDesignNames(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Structure> structure = ResourceKey.create(Registries.STRUCTURE, StellaRuins.id(DeepForgeSite.ID));
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structure).isPresent(), "the structure file did not load");
		StructureSet loaded = com.stella.ruins.world.RuinSitePlanGameTest.sites(level);
		helper.assertTrue(loaded != null, "no " + com.stella.ruins.world.RuinSitePlanGameTest.SITES + " structure set");
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) loaded.placement();
		helper.assertTrue(placement.spacing() == 12 && placement.separation() == 9, "spacing " + placement.spacing() + " separation " + placement.separation());
		JsonObject spread = readSetFile().getAsJsonObject("placement");
		helper.assertTrue(spread.get("salt").getAsInt() == SALT, "the salt is " + spread.get("salt"));
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.excludesVillages(spread), "the village exclusion is missing or short: " + spread);
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.theSitesSetHolds(level, DeepForgeSite.ID), "the one ruin set does not hold the deep forge");
		helper.succeed();
	}

	/** The merged set file, which is the only structure set file this mod ships. */
	private static JsonObject readSetFile() {
		try (java.io.InputStream in = DeepForgeDataGameTest.class.getResourceAsStream("/data/mod-stella-ruins/worldgen/structure_set/sites.json")) {
			return JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest
	public void everySiteTableLoadsAndOnlyHoldsItemsOfThisMod(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<ResourceKey<LootTable>> keys = List.of(DeepForgeSite.VAULT_TABLE, DeepForgeSite.CACHE_TABLE, DeepForgeMobs.KEEPER_DROPS, DeepForgeMobs.IMP_DROPS, DeepForgeMobs.GOLEM_DROPS, DeepForgeMobs.GUARDIAN_DROPS);
		for (ResourceKey<LootTable> key : keys) {
			LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
			helper.assertTrue(table != LootTable.EMPTY, key.identifier() + " did not load");
			helper.assertTrue(Exclusives.ownsTable(key.identifier()), key.identifier() + " is not a table of this mod");
		}
		helper.succeed();
	}

	@GameTest
	public void everyExclusiveItemOfTheSiteIsInTheModNamespace(final GameTestHelper helper) {
		for (net.minecraft.world.item.Item item : List.of(DeepForgeItems.FORGE_CORE, DeepForgeItems.ORE_CHUNK, DeepForgeItems.INGOT, DeepForgeItems.CINDER_DUST,
			DeepForgeItems.FORGEHEART_SHARD, DeepForgeItems.SWORD, DeepForgeItems.AXE, DeepForgeItems.PICKAXE, DeepForgeItems.HELMET, DeepForgeItems.CHESTPLATE,
			DeepForgeItems.LEGGINGS, DeepForgeItems.BOOTS, DeepForgeItems.HAMMER, DeepForgeBlocks.FORGE_ITEM)) {
			helper.assertTrue(Exclusives.isExclusive(new ItemStack(item)), item + " is not exclusive");
		}
		helper.succeed();
	}
}
