package com.stella.ruins.reward;

import com.mojang.serialization.Codec;
import com.stella.ruins.StellaRuins;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.Nullable;

/**
 * Unique items. A unique item carries the id of its effect in one component, has the EPIC rarity, and shows one
 * lore line. {@link UniqueEffects} holds what the effect does.
 */
public final class RuinUniques {
	public static final DataComponentType<String> UNIQUE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		StellaRuins.id("unique"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build()
	);
	public static final String LORE_KEY_PREFIX = "effect.mod-stella-ruins.";

	private static final Map<Identifier, String> UNIQUES = new LinkedHashMap<>();

	private RuinUniques() {
	}

	static void register() {
	}

	public static <T extends Item> T register(
		final String id, final Function<Item.Properties, T> factory, final Item.Properties props, final String effectId
	) {
		if (!Identifier.isValidPath(effectId)) {
			throw new IllegalArgumentException("Bad effect id: " + effectId);
		}
		props.rarity(Rarity.EPIC)
			.component(UNIQUE, effectId)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatableWithFallback(LORE_KEY_PREFIX + effectId, effectId).withStyle(ChatFormatting.LIGHT_PURPLE)
			)));
		T item = RuinItems.register(id, factory, props);
		UNIQUES.put(StellaRuins.id(id), effectId);
		return item;
	}

	/** The effect id on a stack, or null when the stack is not a unique item. */
	public static @Nullable String effectOf(final ItemStack stack) {
		return stack.get(UNIQUE);
	}

	public static Map<Identifier, String> all() {
		return Map.copyOf(UNIQUES);
	}
}
