package com.stella.equips.loot;

import com.stella.equips.StellaEquips;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;

/**
 * Decides which loot tables are containers a player searches, vanilla or modded. The bonus chest gets the head
 * start pool. Every other container gets the rolls of {@link ContainerLoot}.
 */
public final class ChestLoot {
	private static final String BONUS_CHEST = "spawn_bonus_chest";
	private static final List<Identifier> POOLS = List.of();
	/** The head start a new player gets, which replaces the ordinary rolls in the bonus chest. */
	private static final List<Identifier> STARTER_POOLS = List.of(id("chest/starter"));
	/** Vanilla files every chest a player searches under this one folder. */
	private static final String VANILLA_CHEST_FOLDER = "chests";
	/**
	 * The words a container is named with, matched anywhere in the path and in any
	 * namespace. This is what reaches the structure mods, which file their containers
	 * under folders of their own or at the root: Repurposed Structures uses `barrels`,
	 * `trapped_chests` and `shulker_boxes`, Towns and Towers uses `village` and `outpost`,
	 * and the Moogs mods name tables like `desert_temple_crypt_chest` with no folder.
	 */
	private static final List<String> CONTAINER_WORDS = List.of(
		"chest", "barrel", "crate", "shulker", "cache", "stash", "supply", "treasure", "reward", "pot"
	);
	/**
	 * An arrow trap is not a container a player searches, and a trial spawner is not one either. A block's,
	 * a mob's, or a fishing table's own drops are no container either, though names like `blocks/chest` or
	 * `blocks/potatoes` contain a container word.
	 */
	private static final Set<String> EXCLUDED_FOLDERS = Set.of("dispensers", "spawners", "blocks", "entities", "gameplay");
	/** Two vanilla tables sit in the chest folder but are not containers worth filling. */
	private static final Set<String> EXCLUDED_TABLES = Set.of(
		"chests/jungle_temple_dispenser", "chests/trial_chambers/intersection_barrel"
	);
	/** Nothing of ours is a container to fill, and a test fixture must never reach play. */
	private static final Set<String> EXCLUDED_NAMESPACES = Set.of(
		StellaEquips.MOD_ID, StellaEquips.MOD_ID + "-test-" + StellaEquips.MOD_ID
	);

	private ChestLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register(ChestLoot::modifyLootTable);
	}

	/** The folder a path is filed under, or the path itself when it has no folder. */
	private static String folder(final Identifier location) {
		String path = location.getPath();
		return path.contains("/") ? path.substring(0, path.indexOf('/')) : path;
	}

	private static boolean namesAContainer(final String path) {
		for (String word : CONTAINER_WORDS) {
			if (path.contains(word)) {
				return true;
			}
		}

		return false;
	}

	/** True for a container table a player can find and open, in any namespace. */
	public static boolean targetsChest(final ResourceKey<LootTable> key) {
		Identifier location = key.identifier();
		if (EXCLUDED_NAMESPACES.contains(location.getNamespace())) {
			return false;
		}

		String folder = folder(location);
		if (EXCLUDED_FOLDERS.contains(folder) || EXCLUDED_TABLES.contains(location.getPath())) {
			return false;
		}

		// Vanilla keeps every searched chest in one folder, whatever the table is called.
		if (location.getNamespace().equals("minecraft") && folder.equals(VANILLA_CHEST_FOLDER)) {
			return true;
		}

		return namesAContainer(location.getPath());
	}

	public static boolean isBonusChest(final ResourceKey<LootTable> key) {
		return key.identifier().getPath().endsWith(BONUS_CHEST);
	}

	/** The pools a table receives: the bonus chest gets the head start, and the rest none. */
	public static List<Identifier> poolsFor(final ResourceKey<LootTable> key) {
		return key.identifier().getPath().endsWith(BONUS_CHEST) ? STARTER_POOLS : POOLS;
	}

	private static void modifyLootTable(
		final ResourceKey<LootTable> key,
		final LootTable.Builder builder,
		final LootTableSource source,
		final HolderLookup.Provider registries
	) {
		if (!targetsChest(key)) {
			return;
		}

		HolderGetter<LootTable> tables = registries.lookupOrThrow(Registries.LOOT_TABLE);

		for (Identifier pool : poolsFor(key)) {
			Holder<LootTable> table = tables.getOrThrow(ResourceKey.create(Registries.LOOT_TABLE, pool));
			builder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(table)));
		}
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, path);
	}
}
