package com.stella.ruins.site.sealedvault;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import net.minecraft.world.item.ItemStack;

/**
 * The sealed_vault site: a two-level maze at the bottom of the world. Three seals rest in three of six rooms while
 * the Vault Warden hunts the player. Three seals set in the core open the inner vault, which pays out once.
 */
public final class SealedVaultSite {
	public static final String ID = "sealed_vault";
	public static final int ACTIVATION_RADIUS = 28;
	/** The lowest world Y of the vault floor, and the highest world Y of the vault top. */
	public static final int MIN_Y = -58;
	public static final int MAX_Y = -16;
	public static final int COVER = 16;

	private SealedVaultSite() {
	}

	public static void register() {
		VaultBlocks.init();
		VaultItems.register();
		VaultMobs.register();
		VaultTraps.register();
		AbyssEffect.register();
		RuinSite.builder(ID)
			.size(SiteSize.LARGE)
			.placement(Placement.cave(MIN_Y, MAX_Y, COVER))
			.bounds(VaultPlan.SIZE_X, VaultPlan.SIZE_Y, VaultPlan.SIZE_Z)
			.layout(VaultLayout::draw)
			.claim(Claim.once())
			.objective(VaultObjective::new)
			.roster(Roster.empty())
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(VaultMobs.VAULT_LOOT)
			.unique(() -> new ItemStack(VaultItems.ABYSSAL_EDGE))
			.register();
	}
}
