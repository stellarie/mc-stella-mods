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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Appends a Requires line and a Desc line per enchantment the stack carries, in the
 * order vanilla renders the names. A missing key adds no lines. The Requires line names the
 * items the enchantment applies to, which it reads from the enchantment's own supported items.
 */
public final class EnchantmentDescriptions {
	public static final String KEY_SUFFIX = ".desc";
	/** The lang key prefix of the item class an enchantment applies to. The tag id follows, with dots for the colon and slashes. */
	public static final String REQUIRES_PREFIX = "description.mod-stella-equips-enchants.requires.";
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

		DescriptionLines.addBlock(lines, requires(enchantment), Component.translatable(key).withStyle(STYLE));
	}

	/** The key of the item class an enchantment applies to: its supported tag, or {@code any} for a plain list of items. */
	public static String requiresKey(final Holder<Enchantment> enchantment) {
		if (enchantment.value().definition().supportedItems() instanceof HolderSet.Named<Item> named) {
			Identifier tag = named.key().location();
			return REQUIRES_PREFIX + (tag.getNamespace() + "." + tag.getPath()).replace('/', '.');
		}
		return REQUIRES_PREFIX + "any";
	}

	private static Component requires(final Holder<Enchantment> enchantment) {
		return Component.translatable(requiresKey(enchantment)).withStyle(ChatFormatting.WHITE);
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
