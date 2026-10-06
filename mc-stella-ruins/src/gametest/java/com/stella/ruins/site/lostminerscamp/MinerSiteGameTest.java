package com.stella.ruins.site.lostminerscamp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinStructure;
import com.stella.ruins.world.SiteSize;
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
public class MinerSiteGameTest {
	@GameTest
	public void theSiteRegistersWithItsBoundsClaimRosterAndUnique(final GameTestHelper helper) {
		RuinSite site = MinerTestSupport.site();
		helper.assertTrue(site != null, "the lost_miners_camp site is not registered");
		helper.assertTrue(site.size() == SiteSize.SMALL, "the camp is " + site.size());
		helper.assertTrue(site.sizeX() == CampPlan.SIZE_X && site.sizeY() == CampPlan.SIZE_Y && site.sizeZ() == CampPlan.SIZE_Z,
			"bounds " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(site.placement() instanceof Placement.Cave cave && cave.minY() == -40 && cave.maxY() == 40 && cave.minCover() == LostMinersCampSite.COVER,
			"placement " + site.placement());
		helper.assertTrue(site.claim().isOnce() && site.claim().resetTicks(site.id()) == -1L, "the camp pays out more than once");
		helper.assertTrue(site.activationRadius() == LostMinersCampSite.ACTIVATION_RADIUS, "radius " + site.activationRadius());
		helper.assertTrue(site.clueWeight() == LostMinersCampSite.CLUE_WEIGHT, "clue weight " + site.clueWeight());
		helper.assertTrue(site.vaultLoot().equals(CampMobs.VAULT_LOOT), "the vault table is " + site.vaultLoot());
		helper.assertTrue(site.hasUnique() && site.newUnique().is(CampItems.LUCKY_HEADLAMP), "the unique is not the headlamp");
		helper.assertTrue(site.newObjective() instanceof JournalObjective, "the objective is not the journal");
		helper.assertTrue(site.roster().size() == CampPlan.MINERS + CampPlan.RATS, "the roster holds " + site.roster().size());
		helper.assertTrue(site.roster().entries().get(0).kind() == CampMobs.RESTLESS_MINER && site.roster().entries().get(0).count() == CampPlan.MINERS,
			"the miners are " + site.roster().entries().get(0));
		helper.assertTrue(site.roster().entries().get(1).kind() == CampMobs.TUNNEL_RAT && site.roster().entries().get(1).count() == CampPlan.RATS,
			"the rats are " + site.roster().entries().get(1));
		MinerTestSupport.result("campSite", "bounds=" + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ() + " radius=" + site.activationRadius()
			+ " clue=" + site.clueWeight() + " roster=" + site.roster().size() + " once=" + site.claim().isOnce());
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, StellaRuins.id(LostMinersCampSite.ID));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals(LostMinersCampSite.ID), "the structure does not name the site");
		StructureSet set = com.stella.ruins.world.RuinSitePlanGameTest.sites(level);
		helper.assertTrue(set != null, "no sites structure set");
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow().getAsJsonObject();
		MinerTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 12 && placement.get("separation").getAsInt() == 9, "spread " + placement);
		helper.assertTrue(placement.get("salt").getAsInt() == 702913845, "salt " + placement.get("salt"));
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.excludesVillages(placement), "exclusion " + placement);
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.theSitesSetHolds(level, LostMinersCampSite.ID), "the one ruin set does not hold " + LostMinersCampSite.ID);
		helper.succeed();
	}
}
