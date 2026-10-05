package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.Objectives;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import com.stella.ruins.world.WavePlan;
import net.fabricmc.api.ModInitializer;

/**
 * Registers a test-only copy of the wayshrine whose only wave holds six pilgrims, to show how a wave that needs more
 * mobs than markers reuses the markers in order. It shares the real layout. Nothing here ships in the jar.
 */
public final class WayshrineTestInit implements ModInitializer {
	static final String OVERFLOW_ID = "test_wayshrine_overflow";
	static final int OVERFLOW_PILGRIMS = 6;
	static RuinSite overflow;

	/** Built when a core asks, because the kinds exist only once the site has registered, and entrypoint order is not fixed. */
	private static Roster crowd() {
		return Roster.builder().add(WayshrineMobs.pilgrim(), OVERFLOW_PILGRIMS).build();
	}

	@Override
	public void onInitialize() {
		overflow = RuinSite.builder(OVERFLOW_ID)
			.size(SiteSize.SMALL)
			.placement(Placement.surface(WayshrineSite.SLOPE))
			.bounds(WayshrinePlan.SIZE, 16, WayshrinePlan.SIZE)
			.layout(WayshrineLayout::draw)
			.objective(() -> new ShrineRite(() -> Objectives.sequence(Objectives.offer(WayshrineSite::accepts), new ShrineWaves(WavePlan.of(crowd())))))
			.activationRadius(WayshrineSite.ACTIVATION_RADIUS)
			.vaultLoot(WayshrineSite.VAULT_LOOT)
			.register();
	}
}
