package com.stella.ruins.site.fallenwatchtower;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinStructure;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** The site as the game sees it: its registration and its worldgen files. */
public class WatchtowerSiteGameTest {
	@GameTest
	public void theSiteRegistersWithItsBoundsClaimObjectiveAndNoFixedRoster(final GameTestHelper helper) {
		RuinSite site = TowerTestSupport.site();
		helper.assertTrue(site.sizeX() == 21 && site.sizeY() == 44 && site.sizeZ() == 21, "bounds " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(site.size() == com.stella.ruins.world.SiteSize.SMALL && site.placement() instanceof com.stella.ruins.world.Placement.Surface surface && surface.maxSlope() == 4, "placement " + site.placement());
		helper.assertTrue(site.claim().isOnce(), "the claim is not once");
		helper.assertTrue(site.activationRadius() == FallenWatchtowerSite.ACTIVATION_RADIUS && site.clueWeight() == 3, "radius " + site.activationRadius() + " clue " + site.clueWeight());
		helper.assertTrue(site.roster().isEmpty() && site.newObjective() instanceof GarrisonObjective, "the roster is fixed or the objective is not the garrison");
		helper.assertTrue(site.hasUnique() && site.vaultLoot().equals(WatchtowerMobs.VAULT_LOOT), "unique or vault table");
		helper.assertTrue(WatchtowerPlan.SIZE_Y <= 44 && WatchtowerPlan.FLOOR_HEIGHT * (WatchtowerPlan.MIN_FLOORS + 2) + WatchtowerPlan.MAX_CROWN < WatchtowerPlan.SIZE_Y, "the tallest tower does not fit the box");
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, StellaRuins.id("fallen_watchtower"));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals("fallen_watchtower"), "the structure does not name the site");
		ResourceKey<StructureSet> setKey = ResourceKey.create(Registries.STRUCTURE_SET, StellaRuins.id("fallen_watchtower"));
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(setKey).value();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow().getAsJsonObject();
		TowerTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 34 && placement.get("separation").getAsInt() == 12, "spread " + placement);
		helper.assertTrue(placement.get("salt").getAsInt() == 159662037, "salt " + placement.get("salt"));
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		helper.assertTrue("minecraft:villages".equals(zone.get("other_set").getAsString()) && zone.get("chunk_count").getAsInt() >= 8, "exclusion " + zone);
		helper.assertTrue(set.structures().size() == 1 && set.structures().getFirst().structure().value() == structure.value(), "the set holds another structure");
		helper.succeed();
	}
}
