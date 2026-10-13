package com.stella.tower;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * The spawner set in a floor's wall: its numbers, and the mobs it raises by the floor's kind and the
 * castle's palette. Each entity is only its id, so vanilla finishes it with gear on its own. The light
 * rule spans every level because the castle's rooms are lamp lit, and a vanilla monster rule rejects light.
 * No creeper is ever listed: its blast would open the wall.
 */
public final class LostCastleSpawner {
	/** A player in the same room, or just next to it, wakes the spawner. */
	public static final int REQUIRED_PLAYER_RANGE = 10;
	/** The box a mob appears in: three blocks about the spawner, which sits flush in a wall. */
	public static final int SPAWN_RANGE = 3;
	public static final int SPAWN_COUNT = 2;
	/** A local cap; the floor cap in {@link LostCastles} is the real limit. */
	public static final int MAX_NEARBY_ENTITIES = 3;
	public static final int MIN_SPAWN_DELAY = 400;
	public static final int MAX_SPAWN_DELAY = 1000;

	private LostCastleSpawner() {
	}

	/** The weight of each mob id the kind's spawner lists; equal ids are merged by adding their weights. */
	public static Map<String, Integer> table(final LostFloorKind kind, final LostCastlePalette palette) {
		Map<String, Integer> table = new LinkedHashMap<>();
		String flavour = palette.flavour();
		switch (kind) {
			case MUSTER -> {
				add(table, "minecraft:vindicator", 3);
				add(table, "minecraft:skeleton", 2);
				add(table, flavour, 1);
			}
			case STOREROOM -> {
				add(table, "minecraft:spider", 3);
				add(table, "minecraft:cave_spider", 2);
				add(table, "minecraft:zombie", 2);
			}
			case LIBRARY -> {
				add(table, "minecraft:skeleton", 4);
				add(table, "minecraft:zombie", 1);
				add(table, flavour, 1);
			}
			case CRYPT -> {
				add(table, "minecraft:skeleton", 3);
				add(table, "minecraft:zombie", 2);
				add(table, flavour, 1);
			}
			case CHAPEL -> {
				add(table, "minecraft:zombie", 3);
				add(table, "minecraft:vindicator", 1);
				add(table, "minecraft:skeleton", 1);
				add(table, flavour, 1);
			}
			case SANCTUM -> {
				add(table, flavour, 3);
				add(table, "minecraft:skeleton", 2);
				add(table, "minecraft:vindicator", 1);
			}
		}
		return table;
	}

	private static void add(final Map<String, Integer> table, final String id, final int weight) {
		table.merge(id, weight, Integer::sum);
	}

	/** The block entity tag of a spawner for a floor kind in a castle of a palette. */
	public static CompoundTag tag(final LostFloorKind kind, final LostCastlePalette palette) {
		ListTag potentials = new ListTag();
		for (Map.Entry<String, Integer> mob : table(kind, palette).entrySet()) {
			CompoundTag entity = new CompoundTag();
			entity.putString("id", mob.getKey());
			CompoundTag light = new CompoundTag();
			light.putInt("min_inclusive", 0);
			light.putInt("max_inclusive", 15);
			CompoundTag rules = new CompoundTag();
			rules.put("block_light_limit", light);
			rules.put("sky_light_limit", light.copy());
			CompoundTag data = new CompoundTag();
			data.put("entity", entity);
			data.put("custom_spawn_rules", rules);
			CompoundTag entry = new CompoundTag();
			entry.putInt("weight", mob.getValue());
			entry.put("data", data);
			potentials.add(entry);
		}
		CompoundTag tag = new CompoundTag();
		tag.put("SpawnPotentials", potentials);
		tag.putInt("MinSpawnDelay", MIN_SPAWN_DELAY);
		tag.putInt("MaxSpawnDelay", MAX_SPAWN_DELAY);
		tag.putInt("SpawnCount", SPAWN_COUNT);
		tag.putInt("MaxNearbyEntities", MAX_NEARBY_ENTITIES);
		tag.putInt("RequiredPlayerRange", REQUIRED_PLAYER_RANGE);
		tag.putInt("SpawnRange", SPAWN_RANGE);
		return tag;
	}
}
