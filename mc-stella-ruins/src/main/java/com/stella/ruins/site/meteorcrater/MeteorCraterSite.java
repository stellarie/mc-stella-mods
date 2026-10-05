package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import net.minecraft.world.item.ItemStack;

/**
 * The meteor_crater site: a scorched crater with a fallen star at its heart. The star's ore is hot. Mining it
 * pays Starmetal and wakes Ember Slimes, while Star Golems strike from the wall. The star cools after 2400 ticks,
 * and then the vault opens, once for good.
 */
public final class MeteorCraterSite {
	public static final String ID = "meteor_crater";
	public static final int ACTIVATION_RADIUS = 20;
	public static final int CLUE_WEIGHT = 3;

	private MeteorCraterSite() {
	}

	public static void register() {
		MeteorBlocks.init();
		CraterMobs.register();
		CraterItems.register();
		CraterEvents.register();
		CraterSmoke.register();
		StarweaveMagic.register();
		RuinSite.builder(ID)
			.size(SiteSize.SMALL)
			.placement(Placement.surface(CraterPlan.FOUNDATION))
			.bounds(CraterPlan.SIZE_X, CraterPlan.SIZE_Y, CraterPlan.SIZE_Z)
			.layout(CraterLayout::draw)
			.claim(Claim.once())
			.objective(CoolingObjective::new)
			.roster(Roster.empty())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(CraterMobs.VAULT_LOOT)
			.unique(() -> new ItemStack(CraterItems.FALLEN_STAR))
			.clueWeight(CLUE_WEIGHT)
			.register();
	}
}
