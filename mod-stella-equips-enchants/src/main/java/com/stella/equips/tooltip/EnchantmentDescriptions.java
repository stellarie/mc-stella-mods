package com.stella.equips.tooltip;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Appends one grey description line per enchantment the stack carries, in the
 * order vanilla renders the names. A missing key adds no line.
 */
public final class EnchantmentDescriptions {
	public static final String KEY_SUFFIX = ".desc";
	private static final Style STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false);

	private EnchantmentDescriptions() {
	}

	public static void append(
		final ItemStack stack, final HolderLookup.Provider registries, final Consumer<Component> lines
	) {
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		if (enchantments.isEmpty()) {
			return;
		}

		HolderSet<Enchantment> order = tooltipOrder(registries);
		for (Holder<Enchantment> enchantment : order) {
			if (enchantments.getLevel(enchantment) > 0) {
				add(lines, enchantment);
			}
		}

		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			if (!order.contains(enchantment)) {
				add(lines, enchantment);
			}
		}
	}

	public static String keyFor(final Identifier id) {
		return "enchantment." + id.getNamespace() + "." + id.getPath().replace('/', '.') + KEY_SUFFIX;
	}

	private static void add(final Consumer<Component> lines, final Holder<Enchantment> enchantment) {
		Identifier id = enchantment.unwrapKey().map(key -> key.identifier()).orElse(null);
		if (id == null) {
			return;
		}

		String key = keyFor(id);
		if (!Language.getInstance().has(key)) {
			return;
		}

		lines.accept(Component.translatable(key).withStyle(STYLE));
	}

	private static HolderSet<Enchantment> tooltipOrder(final HolderLookup.Provider registries) {
		if (registries == null) {
			return HolderSet.empty();
		}

		Optional<HolderSet.Named<Enchantment>> named =
			registries.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.TOOLTIP_ORDER);
		if (named.isPresent()) {
			return named.get();
		}

		return HolderSet.empty();
	}
}
