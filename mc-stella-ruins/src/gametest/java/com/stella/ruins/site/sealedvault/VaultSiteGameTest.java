package com.stella.ruins.site.sealedvault;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinStructure;
import com.stella.ruins.world.SiteSize;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** Items 7 and 9 and the site as the game sees it: its registration, its worldgen files, and its objective. */
public class VaultSiteGameTest {
	static final long SEED = 0x51DE5L;

	@GameTest
	public void theSiteRegistersLargeWithItsRadiusClaimAndClue(final GameTestHelper helper) {
		RuinSite site = VaultTestSupport.site();
		helper.assertTrue(site.size() == SiteSize.LARGE && site.activationRadius() == 28 && site.clueWeight() == 1, "size " + site.size() + " radius " + site.activationRadius() + " clue " + site.clueWeight());
		helper.assertTrue(site.sizeX() == 56 && site.sizeY() == 24 && site.sizeZ() == 64, "bounds " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(site.claim().isOnce() && site.hasUnique() && site.roster().isEmpty() && site.vaultLoot().equals(VaultMobs.VAULT_LOOT), "the claim, unique, roster or table is wrong");
		helper.assertTrue(site.newObjective() instanceof VaultObjective, "the objective is not the vault objective");
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, StellaRuins.id("sealed_vault"));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals("sealed_vault"), "the structure does not name the site");
		ResourceKey<StructureSet> setKey = ResourceKey.create(Registries.STRUCTURE_SET, StellaRuins.id("sealed_vault"));
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(setKey).value();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow().getAsJsonObject();
		VaultTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 56 && placement.get("separation").getAsInt() == 20, "spread " + placement);
		helper.assertTrue(placement.get("salt").getAsInt() == 512830417, "salt " + placement.get("salt"));
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		helper.assertTrue("minecraft:villages".equals(zone.get("other_set").getAsString()) && zone.get("chunk_count").getAsInt() >= 8, "exclusion " + zone);
		helper.assertTrue(set.structures().size() == 1 && set.structures().getFirst().structure().value() == structure.value(), "the set holds another structure");
		helper.succeed();
	}

	@GameTest(maxTicks = 6000)
	public void threeSealsUsedOnTheCoreCompleteTheVaultAndOpenTheInnerVault(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().size() == 15, "the vault did not wake with 15 mobs: " + arena.entry().roster().size());
			arena.freeze();
			helper.assertTrue(arena.vault().sealed(), "the vault was open before the seals");
			arena.useCore(player, new ItemStack(Items.DIAMOND));
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "a diamond counted as a seal");
			for (int used = 1; used <= VaultPlan.SEALS; used++) {
				ItemStack seal = new ItemStack(VaultItems.VAULT_SEAL);
				arena.useCore(player, seal);
				helper.assertTrue(seal.isEmpty(), "use " + used + " did not spend the seal");
				helper.assertTrue(arena.phase() == (used < VaultPlan.SEALS ? RuinPhase.ACTIVE : RuinPhase.COMPLETE), "after " + used + " seals the phase is " + arena.phase());
			}
			helper.assertTrue(!arena.vault().sealed(), "the inner vault is still locked after three seals");
			boolean affix = false;
			boolean essence = false;
			for (int slot = 0; slot < arena.vault().getContainerSize(); slot++) {
				ItemStack stack = arena.vault().getItem(slot);
				affix |= stack.is(VaultItems.VOID_TOUCHED.template());
				essence |= stack.is(VaultItems.VOID_ESSENCE);
			}
			VaultTestSupport.result("innerVault", "affix=" + affix + " essence=" + essence + " phase=" + arena.phase());
			helper.assertTrue(affix && essence, "the inner vault holds no void_touched_affix or no Void Essence");
		});
	}

	@GameTest(maxTicks = 6000)
	public void theCoreStaysCompleteAfterThirtyDaysAndTheWardenDoesNotComeBack(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			AtomicLong clock = new AtomicLong(9_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				for (int used = 0; used < VaultPlan.SEALS; used++) {
					arena.useCore(player, new ItemStack(VaultItems.VAULT_SEAL));
				}
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the vault did not complete");
				int rosterBefore = arena.entry().roster().size();
				arena.leave(player);
				clock.addAndGet(30L * com.stella.ruins.world.Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
				ServerPlayer again = arena.join(arena.standNearCore());
				arena.core().pulse(arena.level());
				VaultTestSupport.result("onceFor30Days", "phase=" + arena.phase() + " rosterBefore=" + rosterBefore + " rosterAfter=" + arena.entry().roster().size() + " sealed=" + arena.vault().sealed());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the vault left COMPLETE after 30 days: " + arena.phase());
				helper.assertTrue(arena.entry().roster().size() == rosterBefore, "the roster changed after completion: " + rosterBefore + " to " + arena.entry().roster().size());
				helper.assertTrue(again.isAlive(), "the second visitor is dead");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = 6000)
	public void aWokenVaultIsListedAndTheListSurvivesASaveAndLoad(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, SEED, arena -> {
			arena.wake();
			arena.freeze();
			VaultRegistry registry = VaultRegistry.get(arena.level());
			helper.assertTrue(registry.size() >= 1, "the woken vault is not listed");
			var encoded = VaultRegistry.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, registry).getOrThrow();
			VaultRegistry loaded = VaultRegistry.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
			BoundingBox box = arena.frame().box();
			BlockPos inside = new BlockPos(box.minX() + 2, box.minY() + 3, box.minZ() + 20);
			helper.assertTrue(loaded.size() == registry.size(), "the list lost entries: " + registry.size() + " to " + loaded.size());
			helper.assertTrue(!loaded.opened(arena.level(), inside), "an active vault reads as open after a reload");
			VaultTestSupport.result("registry", "entries=" + registry.size() + " reloaded=" + loaded.size());
		});
	}
}
