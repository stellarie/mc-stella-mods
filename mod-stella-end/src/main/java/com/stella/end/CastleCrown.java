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
	private static final int RIM_FROM = CastleLayout.PARAPET_FROM;
	private static final int PARAPET = 4;
	/** The walls of the hatch house stand this high over the deck; its roof lies on top of them. */
	private static final int HOUSE_HEIGHT = 4;

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
		ruins(site);
		hatchHouse(site);
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
	}

	/**
	 * A small house over the hatch: full walls on both sides and behind the hole, and a roof four blocks over the deck.
	 * The front, where the stair comes out, stays open.
	 */
	private static void hatchHouse(final CastleSite site) {
		CastleLayout.Rect house = site.layout.roof().hatchHouse();
		CastleLayout.Flight flight = site.layout.rest().exit();
		int roof = DECK + HOUSE_HEIGHT + 1;
		for (int x = house.x0(); x <= house.x1(); x++) {
			for (int z = house.z0(); z <= house.z1(); z++) {
				boolean side = x == house.x0() || x == house.x1();
				boolean back = z == house.z0();
				if (side || back) {
					for (int up = 1; up <= HOUSE_HEIGHT; up++) {
						site.put(x, DECK + up, z, up == HOUSE_HEIGHT && (x + z) % 2 != 0 ? CastleBlocks.CRACKED : CastleBlocks.BRICKS);
					}
				}
				site.put(x, roof, z, side || back ? CastleBlocks.BRICKS : CastleBlocks.slab(Blocks.END_STONE_BRICK_SLAB, SlabType.TOP));
			}
		}
		int lastHole = flight.z(CastleLayout.Flight.STEPS - 2, 0);
		for (int lane = 0; lane < CastleLayout.Flight.WIDTH; lane++) {
			int x = flight.x(0, lane);
			site.fill(x, DECK + 1, house.z0() + 1, x, DECK + HOUSE_HEIGHT, lastHole + 1, CastleBlocks.AIR);
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

	/** Whether a cell lies in a ruined room or within a cell of one. */
	private static boolean inRuin(final CastleSite site, final int x, final int z) {
		for (CastleLayout.Ruin ruin : site.layout.roof().ruins()) {
			CastleLayout.Rect b = ruin.bounds();
			if (x >= b.x0() - 1 && x <= b.x1() + 1 && z >= b.z0() - 1 && z <= b.z1() + 1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The ruined rooms the plan sets in the corners of the deck, off the dive lanes. Corners stand full height in
	 * pillars, the walls break off at ragged heights, two doorways stand open, and a roof of slabs holds in places.
	 * Nothing rises over the height the plan gives.
	 */
	private static void ruins(final CastleSite site) {
		Random random = site.random(33);
		for (CastleLayout.Ruin ruin : site.layout.roof().ruins()) {
			CastleLayout.Rect b = ruin.bounds();
			int height = ruin.height();
			boolean alongX = random.nextBoolean();
			int gapOne = alongX ? b.x0() + 1 + random.nextInt(b.x1() - b.x0() - 1) : b.z0() + 1 + random.nextInt(b.z1() - b.z0() - 1);
			int gapTwo = alongX ? b.x0() + 1 + random.nextInt(b.x1() - b.x0() - 1) : b.z0() + 1 + random.nextInt(b.z1() - b.z0() - 1);
			for (int x = b.x0(); x <= b.x1(); x++) {
				for (int z = b.z0(); z <= b.z1(); z++) {
					boolean edgeX = x == b.x0() || x == b.x1();
					boolean edgeZ = z == b.z0() || z == b.z1();
					if (edgeX || edgeZ) {
						boolean corner = edgeX && edgeZ;
						boolean doorway = !corner && (alongX ? ((z == b.z0() && x == gapOne) || (z == b.z1() && x == gapTwo)) : ((x == b.x0() && z == gapOne) || (x == b.x1() && z == gapTwo)));
						int top = corner ? height : Math.max(1, height - random.nextInt(4));
						for (int up = 1; up <= top && !doorway; up++) {
							site.put(x, DECK + up, z, corner ? CastleBlocks.PILLAR : random.nextInt(5) == 0 ? CastleBlocks.CRACKED : CastleBlocks.BRICKS);
						}
					} else {
						if (random.nextInt(100) < 45) {
							site.put(x, DECK + height, z, CastleBlocks.slab(Blocks.END_STONE_BRICK_SLAB, SlabType.TOP));
						}
						if (random.nextInt(9) == 0) {
							site.put(x, DECK + 1, z, random.nextBoolean() ? CastleBlocks.COBBLE : CastleBlocks.CRACKED);
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
			if (lane || hatch || inRuin(site, CENTRE + dx, CENTRE + dz)) {
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
