package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import com.stella.ruins.world.WavePlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The proving grounds: a large ruined arena on the surface. A player rings the gong, picks a tier at a lectern, and
 * survives 5, 7 or 10 waves. Each tier pays Champion Tokens once a day. The tokens craft the fighter set.
 */
public final class ProvingGroundsSite {
	public static final String ID = "proving_grounds";
	/** The slope the ground may have under the footprint. The foundation fills it. */
	public static final int SLOPE = 5;
	public static final int ACTIVATION_RADIUS = 24;
	public static final int CLUE_WEIGHT = 2;
	public static final int RESET_DAYS = 1;
	public static final int HEIGHT = 20;
	/** Mobs of each wave as gladiator, retiarius, hound and beast, for tiers I to III. */
	static final int[][][] WAVES = {
		{{3, 0, 0, 0}, {4, 0, 0, 0}, {4, 0, 2, 0}, {4, 1, 2, 0}, {5, 2, 2, 0}},
		{{4, 0, 0, 0}, {5, 0, 1, 0}, {5, 0, 2, 0}, {5, 2, 2, 0}, {6, 2, 3, 0}, {6, 3, 3, 0}, {8, 4, 3, 0}},
		{{5, 0, 0, 0}, {6, 0, 2, 0}, {6, 1, 3, 0}, {7, 2, 3, 0}, {8, 2, 4, 0}, {8, 4, 4, 0}, {9, 4, 5, 0}, {10, 5, 5, 0}, {10, 6, 6, 0}, {10, 6, 6, 1}}
	};
	/** How hard each kind counts, for the strength of a wave: gladiator, retiarius, hound, beast. */
	public static final double[] WEIGHTS = {1.0, 1.5, 1.2, 6.0};

	private static RuinSite site;
	private static final List<WavePlan> PLANS = new ArrayList<>();

	private ProvingGroundsSite() {
	}

	public static RuinSite site() {
		return site;
	}

	/** The waves of a tier. A tier is 1 to 3. */
	public static WavePlan waves(final int tier) {
		return PLANS.get(tier - 1);
	}

	/** The strength of one wave: its mobs, weighted by kind. */
	public static double strength(final int tier, final int wave) {
		int[] mobs = WAVES[tier - 1][wave];
		double sum = 0;
		for (int i = 0; i < mobs.length; i++) {
			sum += mobs[i] * WEIGHTS[i];
		}
		return sum;
	}

	/** The vault table of a tier. A tier is 1 to 3. */
	public static ResourceKey<LootTable> vaultTable(final int tier) {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("chests/proving_grounds_" + tier));
	}

	public static void register() {
		GroundsItems.register();
		GroundsMobs.register();
		GroundsRewards.register();
		GroundsUse.register();
		PLANS.clear();
		String[] kinds = {GroundsMobs.GLADIATOR, GroundsMobs.RETIARIUS, GroundsMobs.HOUND, GroundsMobs.PIT_BEAST};
		for (int[][] tier : WAVES) {
			List<Roster> rosters = new ArrayList<>();
			for (int[] mobs : tier) {
				Roster.Builder builder = Roster.builder();
				for (int i = 0; i < kinds.length; i++) {
					if (mobs[i] > 0) {
						builder.add(GroundsMobs.kind(kinds[i]), mobs[i]);
					}
				}
				rosters.add(builder.build());
			}
			PLANS.add(new WavePlan(rosters));
		}
		site = RuinSite.builder(ID)
			.size(SiteSize.LARGE)
			.placement(Placement.surface(SLOPE))
			.bounds(ArenaPlan.SIZE, HEIGHT, ArenaPlan.SIZE)
			.layout(ArenaLayout::draw)
			.claim(Claim.resetAfterDays(RESET_DAYS))
			.objective(TierObjective::new)
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(vaultTable(1))
			.unique(() -> new ItemStack(GroundsRewards.gladius()))
			.clueWeight(CLUE_WEIGHT)
			.register();
	}
}
