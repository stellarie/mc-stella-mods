package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a floor the first time anyone reaches it, and never again: a floor's corner wall block is its own
 * record that it exists, so a floor keeps every change made to it for good. The layout is drawn from the
 * world seed and the floor number, so each floor is its own and the same every time it is drawn.
 */
public final class TowerFloors {
	/** Blocks that shape the rooms: pillars and low walls, all mineable. */
	public record Piece(BlockPos offset, BlockState state) {
	}

	/** Around the arrival spot and in front of the portal, nothing is built. */
	public static final int CLEAR_RADIUS = 6;
	public static final int LAMP_SPACING = 6;
	private static final long FLOOR_SALT = 0x5DEECE66DL;

	private TowerFloors() {
	}

	public static void register() {
		// The portal frame is part of the tower: the inside of a room may be dug, its way up may not.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
			!(level.dimension().equals(TowerTravel.TOWER) && TowerLayout.isPortalFrame(pos)));
	}

	/** The block that proves a floor was built. */
	public static BlockPos marker(final int floor) {
		return TowerLayout.origin(floor).offset(-1, -1, -1);
	}

	public static boolean isBuilt(final ServerLevel tower, final int floor) {
		return tower.getBlockState(marker(floor)).is(TowerBlocks.TOWER_WALL);
	}

	/** Builds the floor unless it already stands; true when it was built now. */
	public static boolean ensure(final ServerLevel tower, final int floor) {
		if (floor < 1 || isBuilt(tower, floor)) {
			return false;
		}
		build(tower, floor);
		StellaTower.LOGGER.debug("[TOWER] floor={} BUILT origin={}", floor, TowerLayout.origin(floor));
		return true;
	}

	private static void build(final ServerLevel tower, final int floor) {
		BlockPos origin = TowerLayout.origin(floor);
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int base = TowerLayout.BASE_Y;
		int size = TowerLayout.SIZE;
		int top = base + TowerLayout.HEIGHT;
		BlockState wall = TowerBlocks.TOWER_WALL.defaultBlockState();
		BlockState lamp = TowerBlocks.TOWER_LAMP.defaultBlockState();
		BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
		BlockState[] palette = palette(floor);
		Random random = new Random(tower.getSeed() ^ FLOOR_SALT ^ (floor * 0x9E3779B97F4A7C15L));
		for (int x = x0 - 1; x <= x0 + size; x++) {
			for (int z = z0 - 1; z <= z0 + size; z++) {
				boolean edge = x == x0 - 1 || x == x0 + size || z == z0 - 1 || z == z0 + size;
				set(tower, x, base - 2, z, bedrock);
				if (edge) {
					for (int y = base - 1; y <= top; y++) {
						// The corner that marks the floor as built goes down last, below.
						if (!(x == x0 - 1 && z == z0 - 1 && y == base - 1)) {
							set(tower, x, y, z, wall);
						}
					}
				} else {
					set(tower, x, base - 1, z, palette[random.nextInt(palette.length)]);
					boolean lit = (x - x0) % LAMP_SPACING == LAMP_SPACING / 2 && (z - z0) % LAMP_SPACING == LAMP_SPACING / 2;
					set(tower, x, top, z, lit ? lamp : wall);
				}
			}
		}
		for (Piece piece : layout(tower.getSeed(), floor)) {
			BlockPos at = origin.offset(piece.offset());
			tower.setBlock(at, piece.state(), Block.UPDATE_CLIENTS);
		}
		BlockState frame = Blocks.CRYING_OBSIDIAN.defaultBlockState();
		for (BlockPos pos : TowerLayout.portalFrame(floor)) {
			tower.setBlock(pos, frame, Block.UPDATE_CLIENTS);
		}
		tower.setBlock(marker(floor), wall, Block.UPDATE_CLIENTS);
	}

	private static void set(final ServerLevel tower, final int x, final int y, final int z, final BlockState state) {
		tower.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
	}

	/**
	 * The floor's pillars and low walls, as offsets from its inner corner. A pure function of the seed and
	 * the floor, so a floor drawn twice is drawn the same.
	 */
	public static List<Piece> layout(final long seed, final int floor) {
		Random random = new Random(seed ^ (floor * 0x2545F4914F6CDD1DL));
		BlockState[] palette = palette(floor);
		List<Piece> pieces = new ArrayList<>();
		int pillars = 6 + random.nextInt(7);
		for (int i = 0; i < pillars; i++) {
			int x = 2 + random.nextInt(TowerLayout.SIZE - 5);
			int z = 2 + random.nextInt(TowerLayout.SIZE - 5);
			BlockState state = palette[random.nextInt(palette.length)];
			if (clear(x, z) || clear(x + 1, z + 1)) {
				continue;
			}
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					for (int y = 0; y < TowerLayout.HEIGHT; y++) {
						pieces.add(new Piece(new BlockPos(x + dx, y, z + dz), state));
					}
				}
			}
		}
		int walls = 4 + random.nextInt(5);
		for (int i = 0; i < walls; i++) {
			int length = 3 + random.nextInt(5);
			int height = 1 + random.nextInt(3);
			boolean alongX = random.nextBoolean();
			int x = 1 + random.nextInt(TowerLayout.SIZE - 2 - (alongX ? length : 0));
			int z = 1 + random.nextInt(TowerLayout.SIZE - 2 - (alongX ? 0 : length));
			BlockState state = palette[random.nextInt(palette.length)];
			for (int step = 0; step < length; step++) {
				int px = alongX ? x + step : x;
				int pz = alongX ? z : z + step;
				if (clear(px, pz)) {
					continue;
				}
				for (int y = 0; y < height; y++) {
					pieces.add(new Piece(new BlockPos(px, y, pz), state));
				}
			}
		}
		return pieces;
	}

	/** Whether an inner spot must stay open: around the arrival spot, and in front of the portal. */
	public static boolean clear(final int x, final int z) {
		int dzMiddle = Math.abs(z - TowerLayout.MIDDLE_Z);
		boolean nearArrival = Math.abs(x - TowerLayout.ARRIVAL_X) <= CLEAR_RADIUS && dzMiddle <= CLEAR_RADIUS;
		boolean nearPortal = x >= TowerLayout.PORTAL_X - CLEAR_RADIUS && dzMiddle <= CLEAR_RADIUS;
		return nearArrival || nearPortal;
	}

	/**
	 * The stone a floor is built from. The first twenty floors are overworld stone; above them the tower
	 * turns from overworld to nether to end stone every ten floors.
	 */
	public static BlockState[] palette(final int floor) {
		int band = floor <= 20 ? 0 : ((floor - 21) / 10) % 3;
		return switch (band) {
			case 1 -> new BlockState[] {
				Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
				Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()
			};
			case 2 -> new BlockState[] {
				Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
				Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.END_STONE.defaultBlockState()
			};
			default -> new BlockState[] {
				Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
				Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState()
			};
		};
	}
}
