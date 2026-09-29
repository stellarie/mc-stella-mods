package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

/**
 * Clue books: tattered notes left in the chests of ruined portals, dungeons, strongholds and bastions by
 * climbers who came before. Every one tells how to open the way in, a frame of crying obsidian lit by
 * fire, and each adds a word on something else the tower holds.
 */
public final class TowerClues {
	/** Custom data key naming what a clue book adds to the way in. */
	public static final String CLUE_KEY = "stella_tower_clue";
	/** Tattered: the book cannot be copied, as befits a note found at the bottom of a chest. */
	private static final int TATTERED = 3;

	/** The share of each kind of vanilla chest that holds a clue book. */
	private static final Map<String, Double> CHANCES = Map.ofEntries(
		Map.entry("chests/ruined_portal", 0.40),
		Map.entry("chests/simple_dungeon", 0.10),
		Map.entry("chests/stronghold_library", 0.30),
		Map.entry("chests/stronghold_corridor", 0.10),
		Map.entry("chests/stronghold_crossing", 0.10),
		Map.entry("chests/bastion_treasure", 0.30),
		Map.entry("chests/bastion_other", 0.10),
		Map.entry("chests/bastion_bridge", 0.10),
		Map.entry("chests/bastion_hoglin_stable", 0.10)
	);
	private static final Map<Identifier, Boolean> BUILTIN = new ConcurrentHashMap<>();

	/** What a clue book adds to the way in. */
	public enum Kind {
		THE_WAY("The Way Up", "A Climber Who Fell"),
		THE_FLOORS("On Floors and Guardians", "Old Marrow"),
		THE_KEYS("Keys, and the Way Home", "A Climber Who Returned"),
		THE_RUNES("Runes of the Tower", "The Rune-Wright");

		private final String title;
		private final String author;

		Kind(final String title, final String author) {
			this.title = title;
			this.author = author;
		}
	}

	private TowerClues() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Identifier table = key.identifier();
			if (chanceOf(table) > 0.0) {
				// A data pack's own version of a vanilla table carries no clue.
				BUILTIN.put(table, source.isBuiltin());
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey()
			.map(key -> key.identifier())
			.ifPresent(id -> modifyChestDrops(id, BUILTIN.getOrDefault(id, false), context.getRandom(), drops)));
	}

	/** The chance a chest of this table holds a clue book; nought for any other table. */
	public static double chanceOf(final Identifier table) {
		if (!"minecraft".equals(table.getNamespace())) {
			return 0.0;
		}
		return CHANCES.getOrDefault(table.getPath(), 0.0);
	}

	/** Adds a clue book to a chest's loot, at its table's chance, on its own draw. */
	public static void modifyChestDrops(final Identifier table, final boolean builtin, final RandomSource random, final List<ItemStack> drops) {
		double chance = builtin ? chanceOf(table) : 0.0;
		if (chance > 0.0 && random.nextDouble() < chance) {
			ItemStack clue = roll(random);
			drops.add(clue);
			StellaTower.LOGGER.debug("[TOWER] a clue book of {} was left in a chest of {}", teaches(clue), table);
		}
	}

	/** A clue book of a random kind. */
	public static ItemStack roll(final RandomSource random) {
		return of(Kind.values()[random.nextInt(Kind.values().length)]);
	}

	public static ItemStack of(final Kind kind) {
		List<Component> pages = new ArrayList<>();
		pages.add(Component.literal("""
			There is a tower that climbs without end, and it can be entered from anywhere.

			Raise a frame of crying obsidian, as you would for a Nether portal: at least two blocks wide inside and three high, closed on every side. Then light it with fire."""));
		pages.add(Component.literal("""
			The frame need not be small: the game allows it up to twenty-one blocks square inside.

			Ruined portals carry crying obsidian, if you would rather not mine it. Step through, and the first floor is waiting."""));
		switch (kind) {
			case THE_WAY -> pages.add(Component.literal("""
				Every floor is a maze of wide halls, lit from above, and the way up lies across it.

				Do not linger. What lives in the tower grows with every floor, and does not stop."""));
			case THE_FLOORS -> pages.add(Component.literal("""
				Each fifth floor keeps an elite by its portal, and each tenth a boss with a hall of its own, its door a safe room.

				The boss holds a key to its floor. Carry it in, and it will take you back there, and never spends itself."""));
			case THE_KEYS -> pages.add(Component.literal("""
				You are given a Void Key when you enter. Use it, and it returns you to the place you came in by.

				Dying does the same, less kindly. A Scroll of Return, if you have learned to make one, takes you home."""));
			case THE_RUNES -> pages.add(Component.literal("""
				Things of the tower may leave a rune when they fall, of one of eight elements and as strong as the floor.

				Set one into a weapon or armor at the anvil, for sixty-four emeralds, and it will change every blow."""));
		}
		pages.add(Component.literal("- " + kind.author));
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> filtered = pages.stream().map(Filterable::passThrough).toList();
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(kind.title), kind.author, TATTERED, filtered, true));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(CLUE_KEY, kind.name()));
		return stack;
	}

	/** What a clue book adds to the way in, or null for any other item. */
	public static @Nullable Kind teaches(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return data.copyTag().getString(CLUE_KEY).map(name -> {
			try {
				return Kind.valueOf(name);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
	}
}
