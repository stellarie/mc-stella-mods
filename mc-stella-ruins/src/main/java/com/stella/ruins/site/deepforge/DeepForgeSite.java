package com.stella.ruins.site.deepforge;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.reward.UniqueEffects;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The deep_forge site: a dwarven forge sunk into the deepslate. Three Forge Cores, three levers and a guardian light
 * it for good. A lit forge is the only place that smelts Deepsteel.
 */
public final class DeepForgeSite {
	public static final String ID = "deep_forge";
	public static final ResourceKey<LootTable> VAULT_TABLE = ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("chests/deep_forge"));
	public static final ResourceKey<LootTable> CACHE_TABLE = ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("chests/deep_forge_cache"));
	public static final int ACTIVATION_RADIUS = 40;
	/** The hammer's effect, kept so a test can clear its hit counts. */
	static final ForgeheartEffect FORGEHEART = new ForgeheartEffect();

	private DeepForgeSite() {
	}

	public static void register() {
		DeepForgeBlocks.register();
		DeepForgeItems.register();
		DeepForgeMobs.register();
		UniqueEffects.define(DeepForgeItems.HAMMER_EFFECT, FORGEHEART);
		RuinSite.builder(ID)
			.size(SiteSize.LARGE)
			.placement(Placement.cave(-40, 0, 12))
			.bounds(Geo.SIZE_X, Geo.SIZE_Y, Geo.SIZE_Z)
			.layout(DeepForgeLayout::draw)
			.claim(Claim.once())
			.objective(ForgeObjective::new)
			.roster(Roster.empty())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(VAULT_TABLE)
			.unique(() -> new ItemStack(DeepForgeItems.HAMMER))
			.register();
	}
}
