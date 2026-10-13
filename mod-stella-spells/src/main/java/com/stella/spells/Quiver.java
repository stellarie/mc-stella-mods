package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The quiver item and the rules for the quiver in the off hand. Its ammunition is the first thing a bow or a
 * crossbow draws, and an archer skill spends arrows from it. The stack the game draws from is a copy of one
 * slot, so a shot writes the remainder back after the draw. Creative mode and Infinity keep their own rules
 * because their draw leaves the copy whole.
 */
public final class Quiver {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "quiver");
	public static final QuiverItem ITEM = Registry.register(BuiltInRegistries.ITEM, ID, new QuiverItem(new Item.Properties()
		.setId(ResourceKey.create(Registries.ITEM, ID)).stacksTo(1)
		.component(DataComponents.LORE, Spellbooks.lore(Spellbooks.block("quiver", ChatFormatting.GRAY)))));

	/** The copy a draw is working on, by shooter: its source slot is written back when the draw ends. */
	private static final Map<UUID, Draw> DRAWS = new ConcurrentHashMap<>();

	private record Draw(ItemStack copy, ItemStack quiver, int slot) {
	}

	private Quiver() {
	}

	/** Loads the class, which registers the item. */
	static void register() {
	}

	/** The quiver in the player off hand, or an empty stack. Only the off hand counts. */
	public static ItemStack held(final Player player) {
		ItemStack off = player.getOffhandItem();
		return off.getItem() instanceof QuiverItem ? off : ItemStack.EMPTY;
	}

	/** The nine slots of a quiver, as copies. */
	public static NonNullList<ItemStack> contents(final ItemStack quiver) {
		NonNullList<ItemStack> items = NonNullList.withSize(QuiverMenu.SIZE, ItemStack.EMPTY);
		quiver.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		return items;
	}

	/** Replaces the nine slots of a quiver. */
	public static void store(final ItemStack quiver, final List<ItemStack> items) {
		quiver.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}

	/** How many of this item a quiver holds. */
	public static int count(final ItemStack quiver, final Item item) {
		int total = 0;
		for (ItemStack stack : contents(quiver)) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * The ammunition a bow or crossbow draws first: a copy of the first slot the weapon supports, or an empty
	 * stack. Called on both sides, so the client draw check agrees with the server.
	 */
	public static ItemStack projectileFor(final Player player, final ItemStack weapon) {
		ItemStack quiver = held(player);
		if (quiver.isEmpty() || !(weapon.getItem() instanceof ProjectileWeaponItem bow)) {
			return ItemStack.EMPTY;
		}
		Predicate<ItemStack> supported = bow.getSupportedHeldProjectiles();
		NonNullList<ItemStack> items = contents(quiver);
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (!stack.isEmpty() && supported.test(stack)) {
				if (player instanceof ServerPlayer shooter) {
					DRAWS.put(shooter.getUUID(), new Draw(stack, quiver, slot));
				}
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Writes back what a finished draw left of the copy it took from the quiver. */
	public static void commit(final ServerPlayer shooter, final ItemStack ammo) {
		Draw draw = DRAWS.remove(shooter.getUUID());
		if (draw == null || draw.copy() != ammo || held(shooter) != draw.quiver()) {
			return;
		}
		NonNullList<ItemStack> items = contents(draw.quiver());
		items.set(draw.slot(), ammo.isEmpty() ? ItemStack.EMPTY : ammo.copy());
		store(draw.quiver(), items);
	}

	/**
	 * Takes one arrow for an archer skill from the off-hand quiver and returns it as a single stack, or an
	 * empty stack when there is no quiver or it holds no arrow. A firework is never taken.
	 */
	public static ItemStack takeArrow(final ServerPlayer caster) {
		ItemStack quiver = held(caster);
		if (quiver.isEmpty()) {
			return ItemStack.EMPTY;
		}
		NonNullList<ItemStack> items = contents(quiver);
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (!stack.isEmpty() && stack.is(ItemTags.ARROWS)) {
				ItemStack taken = stack.split(1);
				store(quiver, items);
				return taken;
			}
		}
		return ItemStack.EMPTY;
	}
}
