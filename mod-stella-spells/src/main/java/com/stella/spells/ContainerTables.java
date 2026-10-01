package com.stella.spells;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * The loot tables of containers a player searches. The rules copy the equips mod's container matcher, so the
 * scroll and spellbook rolls reach the same chests, barrels and pots as its gear rolls. The mods share no library.
 */
public final class ContainerTables {
	private static final List<String> CONTAINER_WORDS = List.of(
		"chest", "barrel", "crate", "shulker", "cache", "stash", "supply", "treasure", "reward", "pot"
	);
	private static final Set<String> EXCLUDED_FOLDERS = Set.of("dispensers", "spawners", "blocks", "entities", "gameplay");
	private static final Set<String> EXCLUDED_TABLES = Set.of(
		"chests/jungle_temple_dispenser", "chests/trial_chambers/intersection_barrel"
	);
	private static final Set<String> EXCLUDED_NAMESPACES = Set.of(
		Spellbooks.MOD_ID, Spellbooks.MOD_ID + "-test-" + Spellbooks.MOD_ID
	);

	private ContainerTables() {
	}

	public static boolean targets(final Identifier table) {
		if (EXCLUDED_NAMESPACES.contains(table.getNamespace())) {
			return false;
		}
		String path = table.getPath();
		String folder = path.contains("/") ? path.substring(0, path.indexOf('/')) : path;
		if (EXCLUDED_FOLDERS.contains(folder) || EXCLUDED_TABLES.contains(path)) {
			return false;
		}
		if (table.getNamespace().equals("minecraft") && folder.equals("chests")) {
			return true;
		}
		for (String word : CONTAINER_WORDS) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}
}
