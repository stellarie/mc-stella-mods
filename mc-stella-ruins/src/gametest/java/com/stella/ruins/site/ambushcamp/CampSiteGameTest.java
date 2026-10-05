package com.stella.ruins.site.ambushcamp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinStructure;
import com.stella.ruins.world.SiteSize;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** The site as the game sees it: its registration, its worldgen files, and the markers its layout names. */
public class CampSiteGameTest {
	@GameTest
	public void theSiteRegistersWithItsBoundsClaimRadiusAndClue(final GameTestHelper helper) {
		RuinSite site = CampTestSupport.site();
		helper.assertTrue(site.size() == SiteSize.SMALL, "the site is not small");
		helper.assertTrue(site.sizeX() == 31 && site.sizeY() == 10 && site.sizeZ() == 31, "bounds " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(site.activationRadius() == 20 && site.clueWeight() == 3, "radius " + site.activationRadius() + " clue " + site.clueWeight());
		helper.assertTrue(!site.claim().isOnce() && site.claim().days() == 2 && site.claim().resetTicks(site.id()) == 2L * Claim.DAY_TICKS, "the claim is not a two day reset");
		helper.assertTrue(site.placement().foundation() == 4, "foundation " + site.placement().foundation());
		helper.assertTrue(site.roster().isEmpty() && site.hasUnique() && site.vaultLoot().equals(CampMobs.VAULT_LOOT), "roster " + site.roster().size());
		helper.assertTrue(site.newObjective() instanceof ChiefObjective, "the objective is not the Chief objective");
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, StellaRuins.id("ambush_camp"));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals("ambush_camp"), "the structure does not name the site");
		ResourceKey<StructureSet> setKey = ResourceKey.create(Registries.STRUCTURE_SET, StellaRuins.id("ambush_camp"));
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(setKey).value();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow().getAsJsonObject();
		CampTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 34 && placement.get("separation").getAsInt() == 12, "spread " + placement);
		helper.assertTrue(placement.get("salt").getAsInt() == 702913845, "salt " + placement.get("salt"));
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		helper.assertTrue("minecraft:villages".equals(zone.get("other_set").getAsString()) && zone.get("chunk_count").getAsInt() >= 8, "exclusion " + zone);
		helper.assertTrue(set.structures().size() == 1 && set.structures().getFirst().structure().value() == structure.value(), "the set holds another structure");
		helper.succeed();
	}

	@GameTest
	public void theLayoutNamesTheMarkersTheObjectiveReads(final GameTestHelper helper) {
		for (int s = 0; s < 40; s++) {
			long seed = 31_000L + s;
			for (Rotation rotation : Rotation.values()) {
				RuinFrame frame = RuinFrame.of(CampTestSupport.site(), new BlockPos(5000, 64, -3000), rotation, seed);
				LayoutContext dry = LayoutContext.forDryRun(frame);
				CampTestSupport.site().layout().draw(dry);
				Map<String, List<BlockPos>> marks = dry.markers();
				CampPlan plan = CampPlan.derive(net.minecraft.util.RandomSource.create(seed));
				String where = "seed " + seed + " " + rotation;
				helper.assertTrue(marks.get(RuinMarkers.CORE).size() == 1 && marks.get(RuinMarkers.VAULT).size() == 1, where + ": core or vault marker");
				for (int tent = 1; tent <= plan.tentCount(); tent++) {
					helper.assertTrue(marks.get(CampMarkers.tent(tent)).size() == 1 && marks.get(CampMarkers.door(tent)).size() == 1, where + ": tent " + tent + " markers");
				}
				helper.assertTrue(!marks.containsKey(CampMarkers.tent(plan.tentCount() + 1)), where + ": a marker for a tent that does not exist");
				helper.assertTrue(marks.get(CampMarkers.PATROL).size() == CampPlan.patrolFor(plan.tentCount()), where + ": patrol markers " + marks.get(CampMarkers.PATROL).size());
				helper.assertTrue(marks.get(CampMarkers.LOOKOUT).size() == CampPlan.LOOKOUTS && marks.get(CampMarkers.TRAIL).size() == 1, where + ": lookout or trail markers");
				helper.assertTrue(!marks.containsKey(RuinMarkers.SPAWN), where + ": a generic spawn marker would feed the empty roster");
			}
		}
		helper.succeed();
	}
}
