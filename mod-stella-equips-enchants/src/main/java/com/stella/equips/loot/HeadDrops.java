package com.stella.equips.loot;

import com.stella.equips.StellaEquips;
import java.util.Map;
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
 * Attaches the mob head pools to the vanilla entity tables. The pools are loot table JSON.
 */
public final class HeadDrops {
	/** The entity tables that drop a head, and the pool each one receives. */
	private static final Map<String, Identifier> HEADS = Map.of(
		"entities/warden", id("head/warden")
	);

	private HeadDrops() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register(HeadDrops::modifyLootTable);
	}

	private static void modifyLootTable(
		final ResourceKey<LootTable> key,
		final LootTable.Builder builder,
		final LootTableSource source,
		final HolderLookup.Provider registries
	) {
		Identifier location = key.identifier();
		if (!location.getNamespace().equals("minecraft")) {
			return;
		}

		Identifier pool = HEADS.get(location.getPath());
		if (pool == null) {
			return;
		}

		HolderGetter<LootTable> tables = registries.lookupOrThrow(Registries.LOOT_TABLE);
		Holder<LootTable> table = tables.getOrThrow(ResourceKey.create(Registries.LOOT_TABLE, pool));
		builder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(table)));
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, path);
	}
}
