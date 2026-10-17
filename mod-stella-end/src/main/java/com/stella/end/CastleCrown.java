package com.stella.end;

import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The top of the castle. A solid crown of masonry sits on the hall roof and flares out over corbels to the rooftop
 * disc. The rest room and the stair to the hatch are cut out of it. The deck, the parapet and the towers stand on it.
 */
final class CastleCrown {
	private static final int SIZE = EndCastle.SIZE;
	private static final int CENTRE = SIZE / 2;
	private static final int DECK = EndCastle.ROOF_Y;
	/** Cells of the disc at this squared distance from its middle or more belong to the parapet ring. */
	private static final int RIM_FROM = 420;
	private static final int PARAPET = 4;

	private CastleCrown() {
	}

	static void build(final CastleSite site) {
		mass(site);
		deck(site);
		restRoom(site);
		exit(site);
		towers(site);
		parapet(site);
		cover(site);
	}

	private static int beyond(final int x, final int z) {
		return Math.max(Math.max(-x, x - (SIZE - 1)), Math.max(-z, z - (SIZE - 1)));
	}

	private static int squared(final int x, final int z) {
		return (x - CENTRE) * (x - CENTRE) + (z - CENTRE) * (z - CENTRE);
	}

	/** Masonry from the hall roof to the deck. Beyond the keep it hangs from corbels that step up as they reach out. */
	private static void mass(final CastleSite site) {
		for (int x = CastleGround.ZONE_FROM; x <= CastleGround.ZONE_TO; x++) {
			for (int z = CastleGround.ZONE_FROM; z <= CastleGround.ZONE_TO; z++) {
				if (!CastleGround.inDisc(x, z)) {
					continue;
				}
				int out = Math.max(0, beyond(x, z));
				int rim = squared(x, z);
				for (int y = EndCastle.REST_Y + 1 + out; y < DECK; y++) {
					boolean band = (y - EndCastle.REST_Y) % 6 == 0 && rim > 380;
					site.put(x, y, z, band ? CastleBlocks.PURPUR : CastleBlocks.BRICKS);
				}
			}
		}
	}

	private static void deck(final CastleSite site) {
		Random random = site.random(31);
		for (int x = CastleGround.ZONE_FROM; x <= CastleGround.ZONE_TO; x++) {
			for (int z = CastleGround.ZONE_FROM; z <= CastleGround.ZONE_TO; z++) {
				if (CastleGround.inDisc(x, z)) {
					int roll = random.nextInt(12);
					site.put(x, DECK, z, roll == 0 ? CastleBlocks.END_STONE : roll == 1 ? CastleBlocks.CRACKED : CastleBlocks.BRICKS);
				}
			}
		}
	}

	/** The rest room, cut from the crown at the level of the hall roof, with its table, anvil, ender chest and a row of empty chests. */
	private static void restRoom(final CastleSite site) {
		CastleLayout.RestPlan rest = site.layout.rest();
		CastleLayout.Rect room = rest.room();
		int floor = EndCastle.REST_Y + 1;
		site.fill(room.x0(), floor, room.z0(), room.x1(), floor + 5, room.z1(), CastleBlocks.AIR);
		site.put(rest.tableX(), floor, rest.tableZ(), Blocks.CRAFTING_TABLE.defaultBlockState());
		site.put(rest.anvilX(), floor, rest.anvilZ(), Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST));
		site.put(rest.enderX(), floor, rest.enderZ(), Blocks.ENDER_CHEST.defaultBlockState().setValue(EnderChestBlock.FACING, Direction.SOUTH));
		for (int i = 0; i < rest.chestCount(); i++) {
			site.put(rest.chestX() + i, floor, rest.chestZ(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		}
		BlockState rug = Blocks.CARPET.purple().defaultBlockState();
		for (int x = room.x0() + 2; x <= room.x1() - 2; x++) {
			site.put(x, floor, room.z0() + 4, rug);
		}
		for (int z = room.z0() + 1; z <= room.z1() - 1; z++) {
			site.put(room.x0(), floor, z, CastleBlocks.BOOKSHELF);
			site.put(room.x0(), floor + 1, z, CastleBlocks.BOOKSHELF);
		}
		for (CastleLayout.Candle candle : rest.candles()) {
			site.put(candle.x(), floor, candle.z(), CastleBlocks.candle(candle.count()));
		}
	}

	/** The stair from the rest room south through the crown, up to a hatch in the deck beside the parapet. */
	private static void exit(final CastleSite site) {
		CastleLayout.Flight flight = site.layout.rest().exit();
		int floor = EndCastle.REST_Y + 1;
		int doorZ = flight.z(-1, 0) - 1;
		BlockState step = CastleBlocks.stair(Blocks.PURPUR_STAIRS, flight.direction(), Half.BOTTOM);
		for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
			int x = flight.x(0, lane);
			site.fill(x, floor, doorZ, x, floor + 3, doorZ + 1, CastleBlocks.AIR);
		}
		for (int k = 0; k < CastleLayout.Flight.STEPS; k++) {
			for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
				int x = flight.x(k, lane);
				int z = flight.z(k, lane);
				int y = EndCastle.REST_Y + 1 + k;
				site.put(x, y, z, step);
				if (k < CastleLayout.Flight.STEPS - 1) {
					int headroom = flight.hole(k) ? DECK + 2 : y + 3;
					site.fill(x, y + 1, z, x, headroom, z, CastleBlocks.AIR);
				}
			}
		}
		int top = flight.z(CastleLayout.Flight.HOLE_FROM, 0);
		int lastHole = flight.z(CastleLayout.Flight.STEPS - 2, 0);
		int left = flight.x(0, 0) - 1;
		int right = flight.x(0, CastleLayout.Flight.WIDTH - 1) + 1;
		for (int z = top; z <= lastHole; z++) {
			site.putShaped(left, DECK + 1, z, CastleBlocks.BRICK_WALL);
			site.putShaped(right, DECK + 1, z, CastleBlocks.BRICK_WALL);
		}
		for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
			site.putShaped(flight.x(0, lane), DECK + 1, top - 1, CastleBlocks.BRICK_WALL);
		}
	}

	/** Four corner pillars run from the floor 5 slab to the top of the towers they become. */
	private static void towers(final CastleSite site) {
		int base = EndCastle.slabY(5) + 1;
		int top = DECK + EndCastle.TOWER_HEIGHT - 1;
		for (int ox : new int[] {0, SIZE - 3}) {
			for (int oz : new int[] {0, SIZE - 3}) {
				site.fill(ox, base, oz, ox + 2, top - 1, oz + 2, CastleBlocks.PILLAR);
				site.fill(ox, top, oz, ox + 2, top, oz + 2, CastleBlocks.slab(Blocks.PURPUR_SLAB, SlabType.BOTTOM));
				for (int cx : new int[] {ox, ox + 2}) {
					for (int cz : new int[] {oz, oz + 2}) {
						site.put(cx, top + 1, cz, CastleBlocks.PURPUR);
					}
				}
			}
		}
	}

	/** A four block parapet round the rim, crenellated on top, with covered arches cut into it at eight places. */
	private static void parapet(final CastleSite site) {
		for (int x = CastleGround.ZONE_FROM; x <= CastleGround.ZONE_TO; x++) {
			for (int z = CastleGround.ZONE_FROM; z <= CastleGround.ZONE_TO; z++) {
				if (!CastleGround.inDisc(x, z) || squared(x, z) < RIM_FROM || CastleKeep.towerCell(x, z)) {
					continue;
				}
				for (int up = 1; up <= PARAPET; up++) {
					boolean crenel = up == PARAPET && (x + z) % 2 != 0;
					if (!crenel) {
						site.put(x, DECK + up, z, CastleBlocks.BRICKS);
					}
				}
			}
		}
		for (int i = 0; i < 8; i++) {
			double angle = Math.toRadians(22.5 + 45.0 * i);
			double cx = CENTRE + 21.0 * Math.cos(angle);
			double cz = CENTRE + 21.0 * Math.sin(angle);
			for (int x = (int) Math.floor(cx - 2); x <= (int) Math.ceil(cx + 2); x++) {
				for (int z = (int) Math.floor(cz - 2); z <= (int) Math.ceil(cz + 2); z++) {
					if (Math.hypot(x - cx, z - cz) <= 1.7 && CastleGround.inDisc(x, z)) {
						for (int up = 1; up <= 3; up++) {
							site.put(x, DECK + up, z, CastleBlocks.AIR);
						}
					}
				}
			}
		}
	}

	/** Cover in a ring about the middle: half walls, pillar stumps and rubble mounds, with the portal and four lanes left clear. */
	private static void cover(final CastleSite site) {
		Random random = site.random(32);
		for (int i = 0; i < 24; i++) {
			double angle = Math.toRadians(7.5 + 15.0 * i);
			double radius = 10.5 + 2.5 * (i % 3);
			int dx = (int) Math.round(radius * Math.cos(angle));
			int dz = (int) Math.round(radius * Math.sin(angle));
			boolean lane = Math.abs(dx) <= 3 || Math.abs(dz) <= 3;
			boolean hatch = dx >= 4 && dx <= 13 && dz >= 9 && dz <= 20;
			if (lane || hatch) {
				continue;
			}
			int x = CENTRE + dx;
			int z = CENTRE + dz;
			int y = DECK + 1;
			switch ((i / 3) % 3) {
				case 0 -> {
					boolean alongZ = Math.abs(dx) > Math.abs(dz);
					for (int t = -1; t <= 1; t++) {
						int wx = alongZ ? x : x + t;
						int wz = alongZ ? z + t : z;
						site.put(wx, y, wz, CastleBlocks.BRICKS);
						if (random.nextInt(4) > 0) {
							site.put(wx, y + 1, wz, CastleBlocks.BRICKS);
						}
					}
				}
				case 1 -> {
					int height = 2 + random.nextInt(2);
					for (int up = 0; up < height; up++) {
						site.put(x, y + up, z, CastleBlocks.PILLAR);
					}
					site.put(x + 1, y, z, CastleBlocks.stair(Blocks.END_STONE_BRICK_STAIRS, Direction.WEST, Half.BOTTOM));
				}
				default -> {
					site.put(x, y, z, CastleBlocks.BRICKS);
					site.put(x, y + 1, z, CastleBlocks.slab(Blocks.END_STONE_BRICK_SLAB, SlabType.BOTTOM));
					for (Direction side : CastleSite.horizontals()) {
						site.put(x + side.getStepX(), y, z + side.getStepZ(), CastleBlocks.stair(Blocks.END_STONE_BRICK_STAIRS, side.getOpposite(), Half.BOTTOM));
					}
				}
			}
		}
	}
}
