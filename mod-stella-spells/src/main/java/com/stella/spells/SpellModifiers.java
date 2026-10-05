package com.stella.spells;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class SpellModifiers {
	private static final Identifier QUICKENING = enchantmentId("quickening");
	private static final Identifier POTENCY = enchantmentId("potency");
	private static final Identifier PRECISION = enchantmentId("precision");
	private static final Identifier ARCANE_SIGHT = enchantmentId("arcane_sight");
	private static final Identifier CLEAR_MIND = enchantmentId("clear_mind");
	private static final Identifier ARCANE_WEAVE = enchantmentId("arcane_weave");
	private static final Identifier SPELLGUARD = enchantmentId("spellguard");
	private static final Identifier DOUBLECAST = enchantmentId("doublecast");
	static final int BASE_TARGET_RANGE_BLOCKS = 16;

	private SpellModifiers() {
	}

	public static CastModifiers resolve(final ItemStack book, final ItemStack head, final ItemStack chest) {
		int baseCooldown = book.getItem() instanceof SpellbookItem spellbook
			? spellbook.definition().baseCooldownTicks()
			: 20;
		return resolve(book, head, chest, baseCooldown);
	}

	/** The same reading for any focus, a weapon or an arrow included, with the spell's own base cooldown. */
	public static CastModifiers resolve(final ItemStack book, final ItemStack head, final ItemStack chest, final int baseCooldown) {
		int quickening = level(book, QUICKENING);
		int potency = level(book, POTENCY);
		int precision = level(book, PRECISION);
		int arcaneSight = level(head, ARCANE_SIGHT);
		int clearMind = level(head, CLEAR_MIND);
		int arcaneWeave = level(chest, ARCANE_WEAVE);
		int spellguard = level(chest, SPELLGUARD);

		return new CastModifiers(
			1.0 + 0.10 * potency,
			1.0 + 0.10 * precision,
			multiplier(arcaneWeave),
			multiplier(spellguard),
			BASE_TARGET_RANGE_BLOCKS + 2 * arcaneSight,
			cooldownTicks(baseCooldown, quickening, clearMind),
			Awakening.isAwakened(book)
		);
	}

	/** True when the helmet or the cast book carries Doublecast; the higher level counts, never the sum. */
	public static boolean doublecast(final ItemStack head, final ItemStack book) {
		return Math.max(level(head, DOUBLECAST), level(book, DOUBLECAST)) > 0;
	}

	/** True when the helmet carries Doublecast. */
	public static boolean doublecast(final ItemStack head) {
		return level(head, DOUBLECAST) > 0;
	}

	static int cooldownTicks(final int baseTicks, final int quickeningLevel, final int clearMindLevel) {
		double reduction = Math.max(0.50, 1.0 - 0.10 * quickeningLevel - 0.05 * clearMindLevel);
		return Math.max(20, (int) Math.round(baseTicks * reduction));
	}

	static int durationTicks(final int baseTicks, final int level) {
		return durationTicks(baseTicks, multiplier(level));
	}

	private static int durationTicks(final int baseTicks, final double multiplier) {
		return (int) Math.round(baseTicks * multiplier);
	}

	private static int level(final ItemStack stack, final Identifier id) {
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			if (enchantment.unwrapKey().map(ResourceKey::identifier).filter(id::equals).isPresent()) {
				return enchantments.getLevel(enchantment);
			}
		}

		return 0;
	}

	private static double multiplier(final int level) {
		return 1.0 + 0.10 * Math.max(0, level);
	}

	private static Identifier enchantmentId(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}

	public record CastModifiers(
		double powerMultiplier,
		double radiusMultiplier,
		double durationMultiplier,
		double defensiveDurationMultiplier,
		int targetRangeBlocks,
		int cooldownTicks,
		boolean awakened
	) {
		/** The same cast with the awakened flag set; nothing else changes. */
		CastModifiers withAwakened(final boolean awakened) {
			return new CastModifiers(
				this.powerMultiplier, this.radiusMultiplier, this.durationMultiplier, this.defensiveDurationMultiplier,
				this.targetRangeBlocks, this.cooldownTicks, awakened
			);
		}

		int durationTicks(final int baseTicks, final boolean defensive) {
			double durationMultiplier = defensive
				? this.defensiveDurationMultiplier
				: this.durationMultiplier;
			return SpellModifiers.durationTicks(baseTicks, durationMultiplier);
		}
	}
}
