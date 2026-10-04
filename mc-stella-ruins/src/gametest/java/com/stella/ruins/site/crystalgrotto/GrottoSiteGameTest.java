package com.stella.ruins.site.crystalgrotto;

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

/** The site as the game sees it: its registration, its worldgen files, and its crystal block. */
public class GrottoSiteGameTest {
	@GameTest
	public void theSiteRegistersWithItsClaimRadiusAndClue(final GameTestHelper helper) {
		RuinSite site = GrottoTestSupport.site();
		helper.assertTrue(site.activationRadius() == 12 && site.clueWeight() == 3, "radius " + site.activationRadius() + " clue " + site.clueWeight());
		helper.assertTrue(!site.claim().isOnce() && site.claim().days() == 3 && site.claim().resetTicks(site.id()) == 3L * Claim.DAY_TICKS, "the claim is not a three day reset");
		helper.assertTrue(site.roster().size() == 3 && site.hasUnique() && site.vaultLoot().equals(GrottoMobs.VAULT_LOOT), "roster " + site.roster().size());
		helper.assertTrue(site.newObjective() instanceof ChimeObjective, "the objective is not the chime objective");
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, StellaRuins.id("crystal_grotto"));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals("crystal_grotto"), "the structure does not name the site");
		ResourceKey<StructureSet> setKey = ResourceKey.create(Registries.STRUCTURE_SET, StellaRuins.id("crystal_grotto"));
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(setKey).value();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow().getAsJsonObject();
		GrottoTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 34 && placement.get("separation").getAsInt() == 12, "spread " + placement);
		helper.assertTrue(placement.get("salt").getAsInt() == 618244907, "salt " + placement.get("salt"));
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		helper.assertTrue("minecraft:villages".equals(zone.get("other_set").getAsString()) && zone.get("chunk_count").getAsInt() >= 8, "exclusion " + zone);
		helper.assertTrue(set.structures().size() == 1 && set.structures().getFirst().structure().value() == structure.value(), "the set holds another structure");
		helper.succeed();
	}

	@GameTest
	public void theCrystalHasSixTintsLightTenAndResistsBreaking(final GameTestHelper helper) {
		ResonantCrystalBlock block = ResonantCrystalBlock.BLOCK;
		helper.assertTrue(block.getStateDefinition().getPossibleStates().size() == 6, "states " + block.getStateDefinition().getPossibleStates().size());
		for (Tint tint : Tint.values()) {
			BlockState state = block.stateOf(tint);
			helper.assertTrue(state.getValue(ResonantCrystalBlock.TINT) == tint && state.getLightEmission() == ResonantCrystalBlock.LIGHT, tint + " state " + state);
			helper.assertTrue(state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) < 0.0F, tint + " can be broken");
		}
		helper.succeed();
	}

	@GameTest
	public void tintsKeepOneFixedPitchEach(final GameTestHelper helper) {
		float last = 0.0F;
		for (Tint tint : Tint.values()) {
			float pitch = tint.pitch();
			helper.assertTrue(pitch >= 0.5F && pitch <= 2.0F, tint + " pitch " + pitch + " is outside the sound range");
			helper.assertTrue(pitch > last, tint + " pitch " + pitch + " does not rise");
			last = pitch;
		}
		helper.assertTrue(Math.abs(Tint.BLUE.pitch() - 0.75F) < 0.01F && Math.abs(Tint.ORANGE.pitch() - 1.50F) < 0.01F && Math.abs(Tint.CYAN.pitch() - 1.0F) < 1.0E-6F, "the pitch ends moved");
		helper.succeed();
	}
}
