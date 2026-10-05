package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import net.minecraft.world.item.ItemStack;

/**
 * The lost_miners_camp site: a haunted mine tunnel that ends
 * in a camp. Reading the journal opens the cache, once.
 */
public final class LostMinersCampSite {
	public static final String ID = "lost_miners_camp";
	public static final int ACTIVATION_RADIUS = 16;
	public static final int CLUE_WEIGHT = 3;
	public static final int COVER = 8;

	private LostMinersCampSite() {
	}

	public static void register() {
		CampMobs.register();
		CampItems.register();
		JournalObjective.register();
		HeadlampEffect.register();
		RuinSite.builder(ID)
			.size(SiteSize.SMALL)
			.placement(Placement.cave(-40, 40, COVER))
			.bounds(CampPlan.SIZE_X, CampPlan.SIZE_Y, CampPlan.SIZE_Z)
			.layout(CampLayout::draw)
			.claim(Claim.once())
			.objective(JournalObjective::new)
			.roster(Roster.builder().add(CampMobs.RESTLESS_MINER, CampPlan.MINERS).add(CampMobs.TUNNEL_RAT, CampPlan.RATS).build())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(CampMobs.VAULT_LOOT)
			.unique(() -> new ItemStack(CampItems.LUCKY_HEADLAMP))
			.clueWeight(CLUE_WEIGHT)
			.register();
	}
}
