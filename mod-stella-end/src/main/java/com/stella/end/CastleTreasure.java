package com.stella.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The castle's chests: two random ones per floor and one fixed vault, each handed a loot table that fills it the first
 * time it opens. A table fills a chest once, so a vault never refills.
 */
public final class CastleTreasure {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private CastleTreasure() {
	}

	/** The loot table of the two random chests of every floor. */
	public static ResourceKey<LootTable> floorLoot() {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaEnd.id("chests/castle_floor"));
	}

	/** The loot table of the vault on a floor. */
	public static ResourceKey<LootTable> vaultLoot(final int floor) {
		return ResourceKey.create(Registries.LOOT_TABLE, StellaEnd.id("chests/vault_floor_" + floor));
	}

	/** Places the plan's chests and gives each its loot table. */
	static void place(final ServerLevel level, final CastleLayout layout, final BlockPos min) {
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			int index = 0;
			for (CastleLayout.Chest chest : layout.floor(floor).chests()) {
				BlockPos at = new BlockPos(min.getX() + chest.x(), EndCastle.standY(floor), min.getZ() + chest.z());
				level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, chest.facing()), FLAGS);
				if (level.getBlockEntity(at) instanceof ChestBlockEntity entity) {
					long seed = layout.seed() * 31L + floor * 7L + index;
					entity.setLootTable(chest.vault() ? vaultLoot(floor) : floorLoot(), seed);
				}
				index++;
			}
		}
	}
}
