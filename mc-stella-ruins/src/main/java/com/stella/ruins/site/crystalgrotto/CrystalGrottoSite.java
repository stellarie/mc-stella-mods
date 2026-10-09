package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import net.minecraft.world.item.ItemStack;

/**
 * The crystal_grotto site: a geode cave of singing crystals. The player strikes them in their chime order, three
 * rounds, and wins the Resonant affix. The grotto recharges after three days.
 */
public final class CrystalGrottoSite {
	public static final String ID = "crystal_grotto";
	/** Prism Slimes that wander in when the grotto wakes. */
	public static final int SLIMES = 3;
	public static final int ACTIVATION_RADIUS = 12;
	public static final int RESET_DAYS = 3;
	public static final int CLUE_WEIGHT = 3;
	public static final int COVER = 8;

	private CrystalGrottoSite() {
	}

	public static void register() {
		ResonantCrystalBlock.init();
		GrottoMobs.register();
		GrottoItems.register();
		ChimeStrikes.register();
		EchoEffect.register();
		RuinSite.builder(ID)
			.size(SiteSize.SMALL)
			.placement(Placement.cave(-48, 40, COVER))
			.bounds(GrottoPlan.SIZE_X, GrottoPlan.SIZE_Y, GrottoPlan.SIZE_Z)
			.layout(GrottoLayout::draw)
			.claim(Claim.resetAfterDays(RESET_DAYS))
			.objective(ChimeObjective::new)
			.roster(Roster.builder().add(GrottoMobs.PRISM_SLIME, SLIMES).build())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(GrottoMobs.VAULT_LOOT)
			.unique(() -> new ItemStack(GrottoItems.ECHO_PRISM))
			.clueWeight(CLUE_WEIGHT)
			.leads("sealed_vault")
			.register();
	}
}
