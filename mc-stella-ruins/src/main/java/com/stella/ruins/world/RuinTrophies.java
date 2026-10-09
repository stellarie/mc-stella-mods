package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * What a clear adds to its vault beside the table. The site unique comes through pity: each clear of a kind that
 * pays no trophy raises the next chance, and a trophy resets it. A lead is a clue to a rarer site.
 */
public final class RuinTrophies {
	private RuinTrophies() {
	}

	/** The chance of a trophy after this many dry clears of one kind. */
	public static double chance(final RuinConfig.Values config, final int dry) {
		return Math.min(1.0, config.trophyChance() + config.pityStep() * dry);
	}

	/** Adds the extras of one clear to the vault. */
	public static void award(final ServerLevel level, final RuinSite site, final RuinVaultBlockEntity vault) {
		RuinConfig.Values config = RuinConfig.get();
		RandomSource random = level.getRandom();
		if (site.hasUnique()) {
			RuinPity pity = RuinPity.get(level);
			int dry = pity.dry(site.id());
			ItemStack unique = site.newUnique();
			if (random.nextDouble() < chance(config, dry) && vault.addReward(unique)) {
				pity.reset(site.id());
				StellaRuins.LOGGER.debug("[RUINS] trophy {} paid by {} after {} dry clears", unique.getItem(), site.id(), dry);
			} else {
				pity.miss(site.id());
			}
		}
		List<String> leads = site.leads();
		if (!leads.isEmpty() && random.nextDouble() < config.leadChance()) {
			String lead = leads.get(random.nextInt(leads.size()));
			if (vault.addReward(RuinClueItem.of(lead))) {
				StellaRuins.LOGGER.debug("[RUINS] lead to {} paid by {}", lead, site.id());
			}
		}
	}
}
