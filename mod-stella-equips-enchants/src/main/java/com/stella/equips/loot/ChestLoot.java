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
 * Attaches the chest pools to the vanilla chest tables. The pools themselves are loot table JSON.
 */
public final class ChestLoot {
	private static final String CHEST_FOLDER = "chests/";
	private static final List<Identifier> POOLS = List.of(
		id("chest/gear"),
		id("chest/book"),
		id("chest/jackpot")
	);
	/** A tutorial chest, an arrow trap, and a barrel are not chests a player searches. */
	private static final Set<String> EXCLUDED = Set.of(
		"chests/spawn_bonus_chest",
		"chests/jungle_temple_dispenser",
		"chests/trial_chambers/intersection_barrel"
	);

	private ChestLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register(ChestLoot::modifyLootTable);
	}

	/** True for a vanilla chest table that a player can find and open. */
	public static boolean targetsChest(final ResourceKey<LootTable> key) {
		Identifier location = key.identifier();
		return location.getNamespace().equals("minecraft")
			&& location.getPath().startsWith(CHEST_FOLDER)
			&& !EXCLUDED.contains(location.getPath());
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

		for (Identifier pool : POOLS) {
			Holder<LootTable> table = tables.getOrThrow(ResourceKey.create(Registries.LOOT_TABLE, pool));
			builder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(table)));
		}
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, path);
	}
}
