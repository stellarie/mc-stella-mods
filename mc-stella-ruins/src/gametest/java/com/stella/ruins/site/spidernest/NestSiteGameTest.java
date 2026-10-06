package com.stella.ruins.site.spidernest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinStructure;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** The site as the game sees it: its registration, its worldgen files, and its egg sac block. */
public class NestSiteGameTest {
	@GameTest
	public void theSiteRegistersWithItsClaimRadiusAndClue(final GameTestHelper helper) {
		RuinSite site = NestTestSupport.site();
		helper.assertTrue(site.activationRadius() == 14 && site.clueWeight() == 3, "radius " + site.activationRadius() + " clue " + site.clueWeight());
		helper.assertTrue(!site.claim().isOnce() && site.claim().days() == 3 && site.claim().resetTicks(site.id()) == 3L * Claim.DAY_TICKS, "the claim is not a three day reset");
		helper.assertTrue(site.roster().size() == 3 && site.hasUnique() && site.vaultLoot().equals(NestMobs.VAULT_LOOT), "roster " + site.roster().size());
		helper.assertTrue(site.roster().entries().get(0).kind() == NestMobs.NEST_WEAVER, "the roster is not Weavers");
		helper.assertTrue(site.newObjective() instanceof SacObjective, "the objective is not the sac objective");
		helper.assertTrue(site.sizeX() == 30 && site.sizeY() == 14 && site.sizeZ() == 46, "bounds " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, StellaRuins.id("spider_nest"));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals("spider_nest"), "the structure does not name the site");
		StructureSet set = com.stella.ruins.world.RuinSitePlanGameTest.sites(level);
		helper.assertTrue(set != null, "no sites structure set");
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow().getAsJsonObject();
		NestTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 12 && placement.get("separation").getAsInt() == 9, "spread " + placement);
		helper.assertTrue(placement.get("salt").getAsInt() == 702913845, "salt " + placement.get("salt"));
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.excludesVillages(placement), "exclusion " + placement);
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.theSitesSetHolds(level, "spider_nest"), "the one ruin set does not hold spider_nest");
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.theSitesSetHolds(level, "spider_nest"), "the one ruin set does not hold spider_nest");
		helper.succeed();
	}

	@GameTest
	public void theEggSacGlowsAtLevelFourBreaksInstantlyAndDropsNothing(final GameTestHelper helper) {
		BlockState state = EggSacBlock.BLOCK.defaultBlockState();
		helper.assertTrue(state.getLightEmission() == 4 && EggSacBlock.LIGHT == 4, "light " + state.getLightEmission());
		helper.assertTrue(state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == 0.0F, "the sac takes time to break");
		helper.assertTrue(EggSacBlock.BLOCK.getLootTable().isEmpty(), "the sac has a loot table: " + EggSacBlock.BLOCK.getLootTable());
		helper.succeed();
	}
}
