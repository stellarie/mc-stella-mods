package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import net.minecraft.world.item.ItemStack;

/**
 * The ambush_camp site: a bandit camp of tents behind a broken palisade. The Bandit Chief hides in one tent, a
 * decoy waits in each of the others, and killing the Chief opens the vault. The camp rearms after two days with
 * the Chief in a new tent.
 * <p>
 * The site roster is empty on purpose. The camp places its own mobs at its own markers, because the framework
 * roster spawns every kind at the same {@code spawn} markers.
 */
public final class AmbushCampSite {
	public static final String ID = "ambush_camp";
	public static final int ACTIVATION_RADIUS = 20;
	public static final int RESET_DAYS = 2;
	public static final int CLUE_WEIGHT = 3;
	public static final int MAX_SLOPE = 4;

	private AmbushCampSite() {
	}

	public static void register() {
		CampMobs.register();
		CampItems.register();
		CutthroatEffect.register();
		RuinSite.builder(ID)
			.size(SiteSize.SMALL)
			.placement(Placement.surface(MAX_SLOPE))
			.bounds(CampPlan.SIZE_X, CampPlan.SIZE_Y, CampPlan.SIZE_Z)
			.layout(CampLayout::draw)
			.claim(Claim.resetAfterDays(RESET_DAYS))
			.objective(ChiefObjective::new)
			.roster(Roster.empty())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(CampMobs.VAULT_LOOT)
			.unique(() -> new ItemStack(CampItems.CUTTHROATS_CLOAK))
			.clueWeight(CLUE_WEIGHT)
			.leads("ruined_keep")
			.register();
	}
}
