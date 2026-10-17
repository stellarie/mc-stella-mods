package com.stella.end;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The castle's chests: two random double chests per floor and one fixed vault, each half under rubble with its lid in
 * view. The left half holds the loot table that fills the pair the first time it opens, so a vault never rolls twice.
 */
public final class CastleTreasure {
	private CastleTreasure() {
	}

	public static ResourceKey<LootTable> floorLoot() {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaEnd.id("chests/castle_floor"));
	}

	public static ResourceKey<LootTable> vaultLoot(final int floor) {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaEnd.id("chests/vault_floor_" + floor));
	}

	/** Places the plan's double chests, gives each pair its loot table, and heaps rubble on one half. */
	static void place(final CastleSite site) {
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			int y = EndCastle.standY(floor);
			int index = 0;
			for (CastleLayout.Chest chest : site.layout.floor(floor).chests()) {
				Direction facing = chest.facing();
				site.put(chest.x(), y, chest.z(), CastleBlocks.chest(facing, ChestType.LEFT));
				site.put(chest.partnerX(), y, chest.partnerZ(), CastleBlocks.chest(facing, ChestType.RIGHT));
				if (site.level.getBlockEntity(site.pos(chest.x(), y, chest.z())) instanceof ChestBlockEntity entity) {
					long seed = site.layout.seed() * 31L + floor * 7L + index;
					entity.setLootTable(chest.vault() ? vaultLoot(floor) : floorLoot(), seed);
				}
				rubble(site, chest, y);
				index++;
			}
		}
	}

	/** Slabs and stairs over the right half and beside it, none of them whole blocks, so the pair still opens. */
	private static void rubble(final CastleSite site, final CastleLayout.Chest chest, final int y) {
		site.put(chest.partnerX(), y + 1, chest.partnerZ(), CastleBlocks.slab(Blocks.END_STONE_BRICK_SLAB, SlabType.BOTTOM));
		Direction along = chest.facing().getClockWise();
		int beyondX = chest.partnerX() + along.getStepX();
		int beyondZ = chest.partnerZ() + along.getStepZ();
		if (site.get(beyondX, y, beyondZ).isAir()) {
			site.put(beyondX, y, beyondZ, CastleBlocks.stair(Blocks.END_STONE_BRICK_STAIRS, along.getOpposite(), Half.BOTTOM));
		}
	}
}
