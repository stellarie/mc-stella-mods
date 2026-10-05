package com.stella.ruins.site.wayshrine;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Objectives;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import com.stella.ruins.world.WavePlan;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The wayshrine: a small surface shrine. A player offers gold, an emerald, or a Shrine Shard at its altar and
 * survives three waves of the hollow pilgrims. The vault pays out, and the shrine rearms a day later.
 */
public final class WayshrineSite {
	public static final String ID = "wayshrine";
	/** The slope the ground may have under the footprint. The foundation fills it. */
	public static final int SLOPE = 4;
	public static final int ACTIVATION_RADIUS = 16;
	public static final int CLUE_WEIGHT = 3;
	public static final int RESET_DAYS = 1;
	public static final ResourceKey<LootTable> VAULT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, StellaRuins.id("chests/wayshrine"));

	private static RuinSite site;
	private static WavePlan waves;

	private WayshrineSite() {
	}

	public static RuinSite site() {
		return site;
	}

	public static WavePlan waves() {
		return waves;
	}

	/** True for the three things the altar takes. */
	public static boolean accepts(final ItemStack stack) {
		return stack.is(Items.GOLD_INGOT) || stack.is(Items.EMERALD) || stack.is(WayshrineItems.SHRINE_SHARD);
	}

	public static void register() {
		WayshrineItems.register();
		WayshrineLamp.register();
		WayshrineMobs.register();
		WayshrineRewards.register();
		Roster first = Roster.builder().add(WayshrineMobs.pilgrim(), 4).build();
		Roster second = Roster.builder().add(WayshrineMobs.pilgrim(), 3).add(WayshrineMobs.wisp(), 3).build();
		Roster third = Roster.builder().add(WayshrineMobs.pilgrim(), 2).add(WayshrineMobs.wisp(), 2).add(WayshrineMobs.acolyte(), 1).build();
		waves = WavePlan.of(first, second, third);
		site = RuinSite.builder(ID)
			.size(SiteSize.SMALL)
			.placement(Placement.surface(SLOPE))
			.bounds(WayshrinePlan.SIZE, 16, WayshrinePlan.SIZE)
			.layout(WayshrineLayout::draw)
			.claim(Claim.resetAfterDays(RESET_DAYS))
			.objective(() -> new ShrineRite(() -> Objectives.sequence(Objectives.offer(WayshrineSite::accepts), new ShrineWaves(waves))))
			.activationRadius(ACTIVATION_RADIUS)
			.vaultLoot(VAULT_LOOT)
			.unique(() -> new ItemStack(WayshrineRewards.censer()))
			.clueWeight(CLUE_WEIGHT)
			.register();
	}
}
