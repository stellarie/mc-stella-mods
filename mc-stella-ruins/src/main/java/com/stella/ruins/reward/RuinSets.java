package com.stella.ruins.reward;

import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Armor sets. {@link #define} registers four pieces. A tracker counts the pieces that a wearer has on and keeps
 * transient attribute modifiers in step with the bonuses that the count earns.
 */
public final class RuinSets {
	private static final ArmorType[] PIECES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
	private static final int PERIOD = 20;

	private static final Map<String, SetDefinition> SETS = new LinkedHashMap<>();
	private static final Map<Item, Piece> MEMBERS = new HashMap<>();
	private static final Map<String, HybridBonus> HYBRIDS = new LinkedHashMap<>();

	private RuinSets() {
	}

	/** A registered set and its four items, by the slot they are worn in. */
	public record SetDefinition(String id, SetSpec spec, Map<EquipmentSlot, Item> pieces) {
	}

	/** The set that an item belongs to, and the slot that it is worn in. */
	public record Piece(String setId, EquipmentSlot slot) {
	}

	static void register() {
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, from, to) -> {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				refresh(entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % PERIOD != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				refresh(player);
				applyPeriodicEffects(player);
			}
		});
	}

	public static SetDefinition define(final String setId, final SetSpec spec) {
		if (!Identifier.isValidPath(setId) || setId.contains("/")) {
			throw new IllegalArgumentException("Bad set id: " + setId);
		}
		if (SETS.containsKey(setId)) {
			throw new IllegalStateException("Set already defined: " + setId);
		}
		ArmorMaterial material = RuinGear.armorMaterial(spec);
		ItemLore lore = lore(setId, spec);
		Map<EquipmentSlot, Item> pieces = new EnumMap<>(EquipmentSlot.class);
		for (ArmorType type : PIECES) {
			Item item = RuinItems.register(setId + "_" + type.getName(), Item::new,
				new Item.Properties().humanoidArmor(material, type).component(DataComponents.LORE, lore));
			pieces.put(type.getSlot(), item);
			MEMBERS.put(item, new Piece(setId, type.getSlot()));
		}
		SetDefinition definition = new SetDefinition(setId, spec, Collections.unmodifiableMap(pieces));
		SETS.put(setId, definition);
		return definition;
	}

	/**
	 * Registers four pieces that already exist as a set with no bonus of its own, so a hybrid can count them. The
	 * items keep their ids, art and recipes.
	 */
	public static SetDefinition adopt(final String setId, final GearSpec gear, final Map<EquipmentSlot, Item> pieces) {
		if (SETS.containsKey(setId)) {
			throw new IllegalStateException("Set already defined: " + setId);
		}
		SetSpec spec = SetSpec.builder()
			.durabilityFactor(gear.armorDurabilityFactor())
			.defense(gear.helmet(), gear.chestplate(), gear.leggings(), gear.boots())
			.toughness(gear.toughness())
			.knockbackResistance(gear.knockbackResistance())
			.enchantValue(gear.enchantValue())
			.repair(gear.repair())
			.equipmentAsset(gear.equipmentAsset())
			.build();
		Map<EquipmentSlot, Item> copy = new EnumMap<>(pieces);
		copy.forEach((slot, item) -> MEMBERS.put(item, new Piece(setId, slot)));
		SetDefinition definition = new SetDefinition(setId, spec, Collections.unmodifiableMap(copy));
		SETS.put(setId, definition);
		return definition;
	}

	/** Registers a bonus across sets. Every set it names must be defined already. */
	public static HybridBonus defineHybrid(final HybridBonus hybrid) {
		if (!Identifier.isValidPath(hybrid.id()) || hybrid.id().contains("/")) {
			throw new IllegalArgumentException("Bad hybrid id: " + hybrid.id());
		}
		if (HYBRIDS.containsKey(hybrid.id())) {
			throw new IllegalStateException("Hybrid already defined: " + hybrid.id());
		}
		for (String setId : hybrid.requires().keySet()) {
			if (!SETS.containsKey(setId)) {
				throw new IllegalStateException("Hybrid " + hybrid.id() + " names the undefined set " + setId);
			}
		}
		HYBRIDS.put(hybrid.id(), hybrid);
		return hybrid;
	}

	public static Collection<HybridBonus> hybrids() {
		return List.copyOf(HYBRIDS.values());
	}

	public static Identifier hybridModifierId(final String hybridId) {
		return StellaRuins.id("hybrid/" + hybridId);
	}

	/** True when the entity wears enough pieces of every set the hybrid names. */
	public static boolean hybridHolds(final LivingEntity entity, final HybridBonus hybrid) {
		for (Map.Entry<String, Integer> need : hybrid.requires().entrySet()) {
			if (wornPieces(entity, need.getKey()) < need.getValue()) {
				return false;
			}
		}
		return true;
	}

	public static Optional<SetDefinition> get(final String setId) {
		return Optional.ofNullable(SETS.get(setId));
	}

	public static Collection<SetDefinition> all() {
		return List.copyOf(SETS.values());
	}

	public static Optional<Piece> pieceOf(final ItemStack stack) {
		return Optional.ofNullable(MEMBERS.get(stack.getItem()));
	}

	/** Counts the worn pieces of a set. A piece worn in a slot that is not its own does not count. */
	public static int wornPieces(final LivingEntity entity, final String setId) {
		int count = 0;
		for (ArmorType type : PIECES) {
			Piece piece = MEMBERS.get(entity.getItemBySlot(type.getSlot()).getItem());
			if (piece != null && piece.setId().equals(setId) && piece.slot() == type.getSlot()) {
				count++;
			}
		}
		return count;
	}

	public static Identifier modifierId(final String setId, final int pieces) {
		return StellaRuins.id("set/" + setId + "/" + pieces);
	}

	/** Brings the set modifiers of the entity in step with the pieces it wears. */
	static void refresh(final LivingEntity entity) {
		refreshHybrids(entity);
		for (SetDefinition set : SETS.values()) {
			int worn = wornPieces(entity, set.id());
			for (SetBonus bonus : set.spec().bonuses()) {
				Identifier id = modifierId(set.id(), bonus.pieces());
				for (AttributeBonus attribute : bonus.attributes()) {
					AttributeInstance instance = entity.getAttribute(attribute.attribute());
					if (instance == null) {
						continue;
					}
					if (worn >= bonus.pieces()) {
						instance.addOrUpdateTransientModifier(new AttributeModifier(id, attribute.amount(), attribute.operation()));
					} else {
						instance.removeModifier(id);
					}
				}
			}
		}
	}

	private static void refreshHybrids(final LivingEntity entity) {
		for (HybridBonus hybrid : HYBRIDS.values()) {
			boolean holds = hybridHolds(entity, hybrid);
			Identifier id = hybridModifierId(hybrid.id());
			for (AttributeBonus attribute : hybrid.attributes()) {
				AttributeInstance instance = entity.getAttribute(attribute.attribute());
				if (instance == null) {
					continue;
				}
				if (holds) {
					instance.addOrUpdateTransientModifier(new AttributeModifier(id, attribute.amount(), attribute.operation()));
				} else {
					instance.removeModifier(id);
				}
			}
		}
	}

	private static void applyPeriodicEffects(final ServerPlayer player) {
		for (HybridBonus hybrid : HYBRIDS.values()) {
			if (hybrid.every20Ticks() != null && hybridHolds(player, hybrid)) {
				player.addEffect(hybrid.every20Ticks().instance());
			}
		}
		for (SetDefinition set : SETS.values()) {
			int worn = wornPieces(player, set.id());
			for (SetBonus bonus : set.spec().bonuses()) {
				if (bonus.every20Ticks() != null && worn >= bonus.pieces()) {
					player.addEffect(bonus.every20Ticks().instance());
				}
			}
		}
	}

	private static ItemLore lore(final String setId, final SetSpec spec) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatableWithFallback("set.mod-stella-ruins." + setId, setId).withStyle(ChatFormatting.GOLD));
		for (SetBonus bonus : spec.bonuses()) {
			List<Component> parts = new ArrayList<>();
			bonus.attributes().forEach(attribute -> parts.add(attribute.describe()));
			PeriodicEffect periodic = bonus.every20Ticks();
			if (periodic != null) {
				parts.add(Component.translatable("ruins.reward.set.effect", periodic.effect().value().getDisplayName()));
			}
			Component joined = Component.empty();
			for (int i = 0; i < parts.size(); i++) {
				joined = i == 0 ? parts.get(i) : Component.empty().append(joined).append(Component.literal(", ")).append(parts.get(i));
			}
			lines.add(Component.translatable("ruins.reward.set.bonus", bonus.pieces(), joined).withStyle(ChatFormatting.GRAY));
		}
		return new ItemLore(lines);
	}
}
