package com.stella.ruins.site.spidernest;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import net.minecraft.world.item.ItemStack;

/**
 * The spider_nest site: a web-choked cave of egg sacs. The first sac that breaks starts a clock. Break every sac
 * before it ends, or the Nest Matriarch hatches with a swarm. Silkweave builds light armor and bowstrings. The nest
 * grows back after three days.
 */
public final class SpiderNestSite {
	public static final String ID = "spider_nest";
	/** Nest Weavers that wake with the nest. */
	public static final int WEAVERS = NestPlan.WEAVERS;
	public static final int ACTIVATION_RADIUS = 14;
	public static final int RESET_DAYS = 3;
	public static final int CLUE_WEIGHT = 3;
	public static final int COVER = 8;

	private SpiderNestSite() {
	}

	public static void register() {
		EggSacBlock.init();
		NestMobs.register();
		NestItems.register();
		NestHeartbeat.register();
		NestVaults.register();
		RuinSite.builder(ID)
			.size(SiteSize.SMALL)
			.placement(Placement.cave(-32, 48, COVER))
			.bounds(NestPlan.SIZE_X, NestPlan.SIZE_Y, NestPlan.SIZE_Z)
			.layout(NestLayout::draw)
			.claim(Claim.resetAfterDays(RESET_DAYS))
			.objective(SacObjective::new)
			.roster(Roster.builder().add(NestMobs.NEST_WEAVER, WEAVERS).build())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(NestMobs.VAULT_LOOT)
			.unique(() -> new ItemStack(NestItems.WIDOWS_FANG))
			.clueWeight(CLUE_WEIGHT)
			.leads("sealed_vault")
			.onRearm((level, core, markers) -> NestRegrowth.regrow(level, core))
			.register();
	}
}
