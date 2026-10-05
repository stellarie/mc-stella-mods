package com.stella.equips.unique;

import com.stella.equips.refine.Refine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The registry of uniques. A slice registers its uniques from its own class while the mod initializes, before the
 * item registry freezes. The registry also finds the uniques a player holds and wears, for the hook dispatch.
 */
public final class Uniques {
	/** The refine levels at which a unique gains a bonus. */
	public static final int PLUS_7 = 7;
	public static final int PLUS_10 = 10;
	public static final int PLUS_12 = 12;
	public static final int PLUS_15 = 15;

	private static final Map<String, UniqueDef> BY_ID = new LinkedHashMap<>();
	private static final Map<Item, UniqueDef> BY_ITEM = new HashMap<>();
	private static final Map<String, Item> ITEMS = new HashMap<>();
	private static final Map<Identifier, List<UniqueDef>> BY_SOURCE = new HashMap<>();
	private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/** A unique that a player holds or wears, with the context its hooks run for. */
	public record Active(UniqueDef def, UniqueContext context) {
	}

	private Uniques() {
	}

	/** Registers every unique, then the systems that run their hooks. Called once from the mod initializer. */
	public static void bootstrap() {
		BonewhisperBow.register();
		UniquesArcherFighter.register();
		UniquesMagePriest.register();
		UniqueDispatch.register();
		UniqueDrops.register();
		UniqueFields.register();
		UniqueScheduler.register();
		UniqueCooldowns.register();
	}

	public static Item register(final UniqueDef def) {
		if (!def.id().matches("[a-z0-9_]+")) {
			throw new IllegalArgumentException("bad unique id " + def.id());
		}
		if (BY_ID.containsKey(def.id())) {
			throw new IllegalArgumentException("duplicate unique " + def.id());
		}
		if (def.sources().isEmpty()) {
			throw new IllegalArgumentException(def.id() + " has no source");
		}
		if (def.look() != null && !def.kind().isArmor()) {
			throw new IllegalArgumentException(def.id() + " is not armor and has a look");
		}
		Item item = Registry.register(BuiltInRegistries.ITEM, UniqueItems.id(def), UniqueItems.create(def));
		BY_ID.put(def.id(), def);
		BY_ITEM.put(item, def);
		ITEMS.put(def.id(), item);
		for (Identifier source : def.sources()) {
			BY_SOURCE.computeIfAbsent(source, key -> new ArrayList<>()).add(def);
		}
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item, builder -> builder.set(DataComponents.ITEM_NAME, UniqueItems.name(def))));
		UniqueDrops.add(new UniqueDrops.Entry(def.id(), def.sources(), UniqueDrops.BASE_CHANCE, () -> new ItemStack(item)));
		return item;
	}

	public static @Nullable UniqueDef def(final ItemStack stack) {
		return stack.isEmpty() ? null : BY_ITEM.get(stack.getItem());
	}

	public static @Nullable UniqueDef byId(final String id) {
		return BY_ID.get(id);
	}

	/** The registered item of a unique. */
	public static Item item(final UniqueDef def) {
		return ITEMS.get(def.id());
	}

	public static List<UniqueDef> all() {
		return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
	}

	public static List<UniqueDef> bySource(final Identifier entityType) {
		return List.copyOf(BY_SOURCE.getOrDefault(entityType, List.of()));
	}

	/** The unique in the main hand, then each unique worn in its own slot. */
	public static List<Active> active(final ServerPlayer player) {
		List<Active> found = new ArrayList<>(5);
		ItemStack held = player.getMainHandItem();
		UniqueDef heldDef = def(held);
		if (heldDef != null && !heldDef.kind().isArmor()) {
			found.add(new Active(heldDef, new UniqueContext(player, held, Refine.level(held))));
		}
		for (EquipmentSlot slot : ARMOR) {
			ItemStack worn = player.getItemBySlot(slot);
			UniqueDef wornDef = def(worn);
			if (wornDef != null && wornDef.kind().slot() == slot) {
				found.add(new Active(wornDef, new UniqueContext(player, worn, Refine.level(worn))));
			}
		}
		return found;
	}

	/** The refine level of a stack that holds a unique, or 0. */
	public static int refine(final ItemStack stack) {
		return def(stack) == null ? 0 : Refine.level(stack);
	}
}
