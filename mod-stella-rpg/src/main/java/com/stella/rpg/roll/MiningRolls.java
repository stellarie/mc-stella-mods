package com.stella.rpg.roll;

import com.stella.rpg.RpgConfig;
import com.stella.rpg.RpgConfigStore;
import com.stella.rpg.Stat;
import com.stella.rpg.StatBonuses;
import com.stella.rpg.StellaRpg;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Gives a mined ore drop one profile of its tier, and marks chest gear as rolled so it carries nothing.
 * Only a player's block break rolls. A chest, a piston, or an explosion never does.
 */
public final class MiningRolls {
	public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, StellaRpg.id("bonus_ores"));
	/** Sea level. A deeper break counts as deeper. */
	private static final double SURFACE_Y = 64.0;
	/** Each effective LUK point adds this much to the chance that a mined drop rolls. */
	public static final double LUK_ROLL_BONUS = 0.005;

	private MiningRolls() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> rollOre(table, context, drops));
		// The equips mod adds its chest gear in the default phase, so the mark runs after it.
		Identifier late = StellaRpg.id("chest_mark");
		LootTableEvents.MODIFY_DROPS.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		LootTableEvents.MODIFY_DROPS.register(late, (table, context, drops) -> markChest(table, drops));
	}

	/** A chest, a container, or a chest minecart table. */
	public static boolean isChestTable(final Holder<LootTable> table) {
		return table.value().getParamSet() == LootContextParamSets.CHEST
			|| table.unwrapKey().map(key -> key.identifier().getPath().startsWith("chests/")).orElse(false);
	}

	/** The share of mined drops that roll, for a base chance and a player's effective LUK. */
	public static double rollChance(final double base, final int luk) {
		return Math.min(1.0, base + LUK_ROLL_BONUS * luk);
	}

	private static void markChest(final Holder<LootTable> table, final List<ItemStack> drops) {
		if (!isChestTable(table)) {
			return;
		}
		for (ItemStack stack : drops) {
			WeaponRolls.markRolled(stack);
		}
	}

	private static void rollOre(final Holder<LootTable> table, final LootContext context, final List<ItemStack> drops) {
		if (table.value().getParamSet() != LootContextParamSets.BLOCK || drops.isEmpty()) {
			return;
		}
		BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
		if (state == null || !state.is(ORES) || !(context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer miner)) {
			return;
		}
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		ServerLevel level = context.getLevel();
		double depth = origin == null ? 0.0 : depth(origin.y, level.getMinY());
		RpgConfig.MaterialRolls config = RpgConfigStore.current().materialRolls();
		RandomSource random = context.getRandom();
		double rollChance = rollChance(config.rollChance(), StatBonuses.effective(miner, Stat.LUK));
		for (ItemStack stack : drops) {
			String tier = MaterialProfiles.tierOf(stack);
			if (tier == null || stack.has(WeaponRolls.STAT_ROLLS) || stack.has(WeaponRolls.AFFIX_ROLLS)) {
				continue;
			}
			// Most drops stay plain so they stack. LUK lifts the chance of a roll. Depth lifts the quality of a rolled drop.
			if (random.nextDouble() >= rollChance) {
				continue;
			}
			MaterialProfiles.Profile profile = MaterialProfiles.roll(tier, config, MaterialProfiles.gearAffixes(tier), depth, random);
			if (profile == null) {
				continue;
			}
			apply(stack, profile);
			double chance = Math.min(1.0, config.tiers().get(tier).enchantChance() * (1.0 + config.depthEnchantBonus() * depth));
			if (random.nextDouble() < chance) {
				enchant(stack, level, config.secondEnchantChance(), random);
			}
		}
	}

	/** 0 at sea level or above, 1 at the bottom of the world. */
	static double depth(final double y, final int minY) {
		return Math.max(0.0, Math.min(1.0, (SURFACE_Y - y) / (SURFACE_Y - minY)));
	}

	private static void apply(final ItemStack stack, final MaterialProfiles.Profile profile) {
		if (!profile.stats().isEmpty()) {
			stack.set(WeaponRolls.STAT_ROLLS, Map.copyOf(profile.stats()));
		}
		if (!profile.affixes().isEmpty()) {
			stack.set(WeaponRolls.AFFIX_ROLLS, Map.copyOf(profile.affixes()));
		}
	}

	/** One random enchantment, and a second compatible one with the config chance. Curses never roll. */
	private static void enchant(final ItemStack stack, final ServerLevel level, final double secondChance, final RandomSource random) {
		List<Holder<Enchantment>> pool = new ArrayList<>();
		HolderSet<Enchantment> loot = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.ON_RANDOM_LOOT).orElse(null);
		if (loot == null) {
			return;
		}
		for (Holder<Enchantment> enchantment : loot) {
			if (!enchantment.is(EnchantmentTags.CURSE)) {
				pool.add(enchantment);
			}
		}
		if (pool.isEmpty()) {
			return;
		}
		ItemEnchantments.Mutable picked = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		Holder<Enchantment> first = pool.remove(random.nextInt(pool.size()));
		picked.set(first, 1 + random.nextInt(first.value().getMaxLevel()));
		if (random.nextDouble() < secondChance) {
			pool.removeIf(other -> !Enchantment.areCompatible(first, other));
			if (!pool.isEmpty()) {
				Holder<Enchantment> second = pool.get(random.nextInt(pool.size()));
				picked.set(second, 1 + random.nextInt(second.value().getMaxLevel()));
			}
		}
		stack.set(DataComponents.ENCHANTMENTS, picked.toImmutable());
	}
}
