package com.stella.ruins.reward;

import com.mojang.serialization.Codec;
import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jspecify.annotations.Nullable;

/**
 * Affixes. An affix is a smithing template. The smithing table writes it onto an item as one component, its
 * attribute modifiers, and one lore line. An item holds one ruins affix. A new affix replaces the old one.
 */
public final class RuinAffixes {
	public static final DataComponentType<String> AFFIX = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		StellaRuins.id("affix"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build()
	);
	public static final Identifier RECIPE_ID = StellaRuins.id("affix");
	public static final String LORE_KEY_PREFIX = "affix.mod-stella-ruins.";
	static final String MODIFIER_PREFIX = "affix/";

	private static final Map<String, Affix> AFFIXES = new LinkedHashMap<>();

	private RuinAffixes() {
	}

	/** A registered affix and its template item. */
	public record Affix(String id, AffixSpec spec, Item template) {
	}

	static void register() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, RECIPE_ID, AffixRecipe.SERIALIZER);
	}

	public static Affix define(final String affixId, final AffixSpec spec) {
		if (!Identifier.isValidPath(affixId) || affixId.contains("/")) {
			throw new IllegalArgumentException("Bad affix id: " + affixId);
		}
		if (AFFIXES.containsKey(affixId)) {
			throw new IllegalStateException("Affix already defined: " + affixId);
		}
		Item template = RuinItems.register(affixId + "_affix", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON)
			.component(DataComponents.LORE, new ItemLore(List.of(loreLine(affixId)))));
		Affix affix = new Affix(affixId, spec, template);
		AFFIXES.put(affixId, affix);
		return affix;
	}

	public static Optional<Affix> get(final String affixId) {
		return Optional.ofNullable(AFFIXES.get(affixId));
	}

	public static Collection<Affix> all() {
		return List.copyOf(AFFIXES.values());
	}

	/** The affix id on a stack, or null. */
	public static @Nullable String affixOf(final ItemStack stack) {
		return stack.get(AFFIX);
	}

	/**
	 * Returns a copy of the base with the affix on it. Returns an empty stack when the base already has this
	 * affix, so the smithing table does not eat materials for no change.
	 */
	public static ItemStack apply(final ItemStack base, final Affix affix) {
		if (affix.id().equals(affixOf(base))) {
			return ItemStack.EMPTY;
		}
		ItemStack result = base.copyWithCount(1);
		EquipmentSlotGroup group = slotGroup(result);
		ItemAttributeModifiers modifiers = withoutAffixModifiers(result.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY));
		for (AttributeBonus bonus : affix.spec().attributes()) {
			String attribute = bonus.attribute().unwrapKey().orElseThrow().identifier().getPath();
			Identifier id = StellaRuins.id(MODIFIER_PREFIX + affix.id() + "/" + group.getSerializedName() + "/" + attribute);
			modifiers = modifiers.withModifierAdded(bonus.attribute(), new AttributeModifier(id, bonus.amount(), bonus.operation()), group);
		}
		result.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
		List<Component> lore = new ArrayList<>();
		ItemLore old = result.get(DataComponents.LORE);
		if (old != null) {
			old.lines().stream().filter(line -> !isAffixLore(line)).forEach(lore::add);
		}
		lore.add(loreLine(affix.id()));
		result.set(DataComponents.LORE, new ItemLore(lore));
		result.set(AFFIX, affix.id());
		return result;
	}

	static Component loreLine(final String affixId) {
		return Component.translatableWithFallback(LORE_KEY_PREFIX + affixId, affixId).withStyle(ChatFormatting.AQUA);
	}

	static boolean isAffixLore(final Component line) {
		return line.getContents() instanceof TranslatableContents contents && contents.getKey().startsWith(LORE_KEY_PREFIX);
	}

	static boolean isAffixModifier(final AttributeModifier modifier) {
		return modifier.id().getNamespace().equals(StellaRuins.MOD_ID) && modifier.id().getPath().startsWith(MODIFIER_PREFIX);
	}

	static EquipmentSlotGroup slotGroup(final ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			return EquipmentSlotGroup.bySlot(equippable.slot());
		}
		return EquipmentSlotGroup.MAINHAND;
	}

	static ItemAttributeModifiers withoutAffixModifiers(final ItemAttributeModifiers modifiers) {
		List<ItemAttributeModifiers.Entry> kept = new ArrayList<>();
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (!isAffixModifier(entry.modifier())) {
				kept.add(entry);
			}
		}
		return new ItemAttributeModifiers(kept);
	}
}
