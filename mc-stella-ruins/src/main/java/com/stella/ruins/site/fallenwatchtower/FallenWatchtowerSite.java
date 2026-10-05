package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import net.minecraft.world.item.ItemStack;

/**
 * The fallen_watchtower site: a tall, half-collapsed stone tower, 24 to 40 blocks high. Archers and crossbowmen hold
 * its floors and the Watch Captain holds the roof. Clearing the garrison opens the vault once.
 */
public final class FallenWatchtowerSite {
	public static final String ID = "fallen_watchtower";
	/**
	 * The library tests the distance to the core in three dimensions, and the core sits on the roof, up to 37 blocks
	 * over the door. This radius lets a player 20 blocks from the foot of the tallest tower wake it.
	 */
	public static final int ACTIVATION_RADIUS = 43;
	public static final int CLUE_WEIGHT = 3;
	public static final int SLOPE = 4;

	private FallenWatchtowerSite() {
	}

	public static void register() {
		WatchtowerMobs.register();
		WatchtowerItems.register();
		RuinSite.builder(ID)
			.size(SiteSize.SMALL)
			.placement(Placement.surface(SLOPE))
			.bounds(WatchtowerPlan.SIZE_X, WatchtowerPlan.SIZE_Y, WatchtowerPlan.SIZE_Z)
			.layout(WatchtowerLayout::draw)
			.claim(Claim.once())
			.objective(GarrisonObjective::new)
			.roster(Roster.empty())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(WatchtowerMobs.VAULT_LOOT)
			.unique(() -> new ItemStack(WatchtowerItems.LONGBOW))
			.clueWeight(CLUE_WEIGHT)
			.register();
	}
}
