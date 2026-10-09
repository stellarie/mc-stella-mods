package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.StellaRuins;
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
 * The Ruined Keep: a surface castle keep up to 48 blocks tall. Three floor wardens each drop a key fragment. Three
 * fragments open the throne room, where the Castellan waits. The keep pays out once.
 */
public final class RuinedKeepSite {
	public static final String ID = "ruined_keep";
	public static final ResourceKey<LootTable> VAULT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("chests/ruined_keep"));
	public static final int ACTIVATION_RADIUS = 32;

	private RuinedKeepSite() {
	}

	public static void register() {
		KeepBlocks.register();
		KeepMobs.register();
		KeepRewards.register();
		RuinSite.builder(ID)
			.size(SiteSize.LARGE)
			.placement(Placement.surface(6))
			.bounds(KeepPlan.SIZE, KeepPlan.SIZE, KeepPlan.SIZE)
			.layout(KeepLayout::draw)
			.claim(Claim.once())
			.objective(KeepObjective::new)
			.roster(Roster.empty())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(VAULT_LOOT)
			.unique(() -> new ItemStack(KeepRewards.bannerOfTheKeep))
			.register();
	}
}
