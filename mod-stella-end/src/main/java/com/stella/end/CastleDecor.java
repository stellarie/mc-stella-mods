package com.stella.end;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The dressing of the keep: windows of panes and bars, candles, props, wall dressing, partial ceilings, mezzanines,
 * railings, and the furnishing of the great hall. Everything here leaves spawn cells, doorways and stairs alone.
 */
final class CastleDecor {
	private static final int SIZE = EndCastle.SIZE;
	private static final int TOP = EndCastle.FLOOR_HEIGHT - 1;

	private CastleDecor() {
	}

	static void build(final CastleSite site) {
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			CastleLayout.FloorPlan plan = site.layout.floor(floor);
			windows(site, plan);
			candles(site, plan);
			if (floor <= 5) {
				rooms(site, plan, site.random(100 + floor));
			}
		}
		railings(site);
		hall(site, site.layout.floor(EndCastle.FLOORS), site.random(106));
		stairwork(site);
	}

	// -- Windows -------------------------------------------------------------------------------------------------------

	private static void windows(final CastleSite site, final CastleLayout.FloorPlan plan) {
		int floor = plan.number();
		if (floor == EndCastle.FLOORS) {
			CastleLayout.Rect hall = CastleLayout.HALL;
			int y0 = EndCastle.slabY(floor) + 4;
			line(site, plan, true, hall.x0(), hall.z0() + 2, hall.z1() - 2, y0, y0 + 7, 5);
			line(site, plan, true, hall.x1(), hall.z0() + 2, hall.z1() - 2, y0, y0 + 7, 5);
			line(site, plan, false, hall.z0(), hall.x0() + 2, hall.x1() - 2, y0, y0 + 7, 5);
			line(site, plan, false, hall.z1(), hall.x0() + 2, hall.x1() - 2, y0, y0 + 7, 5);
			return;
		}
		int y0 = EndCastle.slabY(floor) + 4;
		int y1 = y0 + 4;
		int from = 3;
		int to = SIZE - 4;
		if (floor == 5) {
			line(site, plan, false, 0, 9, SIZE - 10, y0, y1, 6);
			line(site, plan, false, SIZE - 1, 9, SIZE - 10, y0, y1, 6);
			line(site, plan, true, 6, from, to, y0, y1, 6);
			line(site, plan, true, 25, from, to, y0, y1, 6);
			return;
		}
		line(site, plan, true, 0, from, to, y0, y1, 6);
		line(site, plan, true, SIZE - 1, from, to, y0, y1, 6);
		line(site, plan, false, 0, from, to, y0, y1, 6);
		line(site, plan, false, SIZE - 1, from, to, y0, y1, 6);
		if (floor == 2 || floor == 3) {
			CastleLayout.Rect court = CastleLayout.COURTYARD;
			line(site, plan, true, court.x0() - 1, court.z0(), court.z1(), y0, y1, 4);
			line(site, plan, true, court.x1() + 1, court.z0(), court.z1(), y0, y1, 4);
			line(site, plan, false, court.z0() - 1, court.x0(), court.x1(), y0, y1, 4);
			line(site, plan, false, court.z1() + 1, court.x0(), court.x1(), y0, y1, 4);
		}
	}

	/** Cuts three wide windows along a wall line, one every {@code pitch} cells, wherever the wall stands whole. */
	private static void line(
		final CastleSite site, final CastleLayout.FloorPlan plan, final boolean alongZ, final int fixed, final int lo, final int hi, final int y0, final int y1,
		final int pitch
	) {
		for (int start = lo + 1; start + 2 <= hi; start += pitch) {
			boolean whole = true;
			for (int p = start; p <= start + 2; p++) {
				int x = alongZ ? fixed : p;
				int z = alongZ ? p : fixed;
				whole &= plan.kind(x, z) == CastleLayout.WALL && !(plan.number() >= 5 && CastleKeep.towerCell(x, z));
			}
			if (!whole) {
				continue;
			}
			for (int p = start; p <= start + 2; p++) {
				int x = alongZ ? fixed : p;
				int z = alongZ ? p : fixed;
				for (int y = y0; y <= y1; y++) {
					BlockState pane = y - y0 < 2 ? CastleBlocks.BARS : ((p + y) % 4 == 0 ? CastleBlocks.STAINED_PANE : CastleBlocks.PANE);
					site.putShaped(x, y, z, pane);
				}
			}
		}
	}

	// -- Light -------------------------------------------------------------------------------------------------------------

	/** Candles are the only light of the castle: two to four in every room. */
	private static void candles(final CastleSite site, final CastleLayout.FloorPlan plan) {
		int y = EndCastle.standY(plan.number());
		for (CastleLayout.Candle candle : plan.candles()) {
			site.put(candle.x(), y, candle.z(), CastleBlocks.candle(candle.count()));
		}
	}

	// -- Rooms of floors 1 to 5 ----------------------------------------------------------------------------------------

	private static void rooms(final CastleSite site, final CastleLayout.FloorPlan plan, final Random random) {
		int slab = EndCastle.slabY(plan.number());
		for (int[] prop : plan.props()) {
			prop(site, prop[0], slab + 1, prop[1], random);
		}
		for (int[] edge : plan.edges()) {
			dress(site, plan, edge, slab, random);
		}
		for (int room = 1; room <= plan.roomCount(); room++) {
			int id = room;
			List<int[]> edges = new ArrayList<>(plan.edges().stream().filter(edge -> plan.room(edge[0], edge[1]) == id).toList());
			Collections.shuffle(edges, random);
			for (int[] edge : edges) {
				if (cover(site, plan, edge, slab)) {
					break;
				}
			}
		}
		for (CastleLayout.Ledge ledge : plan.ledges()) {
			ledge(site, ledge, slab);
		}
	}

	private static void prop(final CastleSite site, final int x, final int y, final int z, final Random random) {
		switch (random.nextInt(6)) {
			case 0 -> {
				int height = 1 + random.nextInt(3);
				for (int up = 0; up < height; up++) {
					site.put(x, y + up, z, CastleBlocks.PILLAR);
				}
				if (random.nextBoolean()) {
					site.put(x, y + height, z, CastleBlocks.stair(Blocks.END_STONE_BRICK_STAIRS, Direction.values()[2 + random.nextInt(4)], Half.BOTTOM));
				}
			}
			case 1 -> {
				BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
				site.put(x, y, z, barrel);
				if (random.nextBoolean()) {
					site.put(x, y + 1, z, barrel);
				}
			}
			case 2 -> {
				site.putShaped(x, y, z, CastleBlocks.FENCE);
				site.put(x, y + 1, z, CastleBlocks.slab(Blocks.DARK_OAK_SLAB, SlabType.BOTTOM));
			}
			case 3 -> {
				site.put(x, y, z, CastleBlocks.COBBLE);
				site.put(x, y + 1, z, CastleBlocks.slab(Blocks.END_STONE_BRICK_SLAB, SlabType.BOTTOM));
				if (random.nextBoolean()) {
					site.put(x, y + 2, z, CastleBlocks.COBWEB);
				}
			}
			case 4 -> site.put(x, y, z, CastleBlocks.CAULDRON);
			default -> site.put(x, y, z, CastleBlocks.HAY);
		}
	}

	/** Dresses a free cell against a wall: shelves, banners, chains, cobwebs or rubble. */
	private static void dress(final CastleSite site, final CastleLayout.FloorPlan plan, final int[] edge, final int slab, final Random random) {
		int x = edge[0];
		int z = edge[1];
		Direction toWall = Direction.from2DDataValue(edge[2]);
		int roll = random.nextInt(100);
		if (roll < 16) {
			site.put(x, slab + 1, z, CastleBlocks.BOOKSHELF);
			site.put(x, slab + 2, z, CastleBlocks.BOOKSHELF);
		} else if (roll < 30) {
			BlockState banner = (roll % 2 == 0 ? Blocks.WALL_BANNER.purple() : Blocks.WALL_BANNER.black()).defaultBlockState()
				.setValue(WallBannerBlock.FACING, toWall.getOpposite());
			site.put(x, slab + 4, z, banner);
		} else if (roll < 42) {
			for (int up = 0; up < 3; up++) {
				site.putShaped(x, slab + TOP - up, z, CastleBlocks.CHAIN);
			}
		} else if (roll < 54) {
			site.put(x, slab + TOP, z, CastleBlocks.COBWEB);
			int nx = x - toWall.getStepX();
			int nz = z - toWall.getStepZ();
			if (plan.calm(nx, nz)) {
				site.putIfAir(nx, slab + TOP, nz, CastleBlocks.COBWEB);
			}
		} else if (roll < 62) {
			site.put(x, slab + 1, z, roll % 2 == 0 ? CastleBlocks.COBBLE : CastleBlocks.CRACKED);
		}
	}

	/** What is left of a ceiling: two slabs hung against the wall, high over the floor. Whether the cell took them. */
	private static boolean cover(final CastleSite site, final CastleLayout.FloorPlan plan, final int[] edge, final int slab) {
		Direction inward = Direction.from2DDataValue(edge[2]).getOpposite();
		int nx = edge[0] + inward.getStepX();
		int nz = edge[1] + inward.getStepZ();
		if (!plan.calm(edge[0], edge[1]) || !plan.calm(nx, nz) || plan.room(nx, nz) != plan.room(edge[0], edge[1])) {
			return false;
		}
		BlockState roof = CastleBlocks.slab(Blocks.END_STONE_BRICK_SLAB, SlabType.TOP);
		site.put(edge[0], slab + 11, edge[1], roof);
		site.put(nx, slab + 11, nz, roof);
		return true;
	}

	/** A plank ledge, two cells deep, six blocks over the floor, with bars on its open edge and a ladder at its end. */
	private static void ledge(final CastleSite site, final CastleLayout.Ledge ledge, final int slab) {
		BlockState planks = CastleBlocks.PLANKS;
		int deck = slab + CastleLayout.Ledge.RISE;
		for (int i = 0; i < ledge.length(); i++) {
			site.put(ledge.x(i), deck, ledge.z(i), planks);
			site.put(ledge.openX(i), deck, ledge.openZ(i), planks);
			site.putShaped(ledge.openX(i), deck + 1, ledge.openZ(i), CastleBlocks.BARS);
		}
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, ledge.inward());
		for (int up = 1; up <= CastleLayout.Ledge.RISE + 1; up++) {
			site.put(ledge.x(ledge.length()), slab + up, ledge.z(ledge.length()), ladder);
		}
	}

	// -- Railings ------------------------------------------------------------------------------------------------------

	/** Low walls on the open edges of the floor 5 balconies and the floor 6 terrace. */
	private static void railings(final CastleSite site) {
		for (int floor = 5; floor <= EndCastle.FLOORS; floor++) {
			int y = EndCastle.standY(floor);
			for (int t = 3; t <= SIZE - 4; t++) {
				if (floor == 6 || t < 6 || t > SIZE - 7) {
					site.putShaped(t, y, 0, CastleBlocks.BRICK_WALL);
					site.putShaped(t, y, SIZE - 1, CastleBlocks.BRICK_WALL);
				}
				site.putShaped(0, y, t, CastleBlocks.BRICK_WALL);
				site.putShaped(SIZE - 1, y, t, CastleBlocks.BRICK_WALL);
			}
		}
	}

	// -- Stairwork -------------------------------------------------------------------------------------------------------

	/** Support pillars stand under the rails at these steps of a run. */
	private static final int[] PILLAR_STEPS = {3, 7, 11};
	/** The last step whose rail, two blocks over the step, still stands under the slab above. */
	private static final int LAST_RAIL_STEP = CastleLayout.Flight.HOLE_FROM + 1;

	/** Rails and support pillars on every run, and a rail round the opening where a run comes up through a floor. */
	private static void stairwork(final CastleSite site) {
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			CastleLayout.FloorPlan plan = site.layout.floor(floor);
			int slab = EndCastle.slabY(floor);
			for (CastleLayout.Flight flight : plan.ups()) {
				for (int k = 0; k <= LAST_RAIL_STEP; k++) {
					for (int rail : new int[] {CastleLayout.Flight.RAIL_LOW, CastleLayout.Flight.RAIL_HIGH}) {
						int x = flight.x(k, rail);
						int z = flight.z(k, rail);
						if (plan.kind(x, z) == CastleLayout.OPEN && !plan.walkedByAnother(flight, x, z)) {
							site.putShaped(x, slab + 2 + k, z, CastleBlocks.BRICK_WALL);
						}
					}
				}
				for (int k : PILLAR_STEPS) {
					for (int rail : new int[] {CastleLayout.Flight.RAIL_LOW, CastleLayout.Flight.RAIL_HIGH}) {
						int x = flight.x(k, rail);
						int z = flight.z(k, rail);
						if (plan.kind(x, z) == CastleLayout.OPEN && !plan.walkedByAnother(flight, x, z)) {
							site.fill(x, slab + 1, z, x, slab + 1 + k, z, CastleBlocks.PILLAR);
						}
					}
				}
			}
			for (CastleLayout.Flight flight : plan.arrivals()) {
				opening(site, plan, flight, slab);
			}
		}
	}

	/** Low walls along the sides of the hole a run climbs through, and across its foot end, on the floor the run arrives at. */
	private static void opening(final CastleSite site, final CastleLayout.FloorPlan plan, final CastleLayout.Flight flight, final int slab) {
		int y = slab + 1;
		for (int k = CastleLayout.Flight.HOLE_FROM; k < CastleLayout.Flight.STEPS; k++) {
			for (int rail : new int[] {CastleLayout.Flight.RAIL_LOW, CastleLayout.Flight.RAIL_HIGH}) {
				int x = flight.x(k, rail);
				int z = flight.z(k, rail);
				if (plan.kind(x, z) == CastleLayout.OPEN && !plan.walkedByAnother(flight, x, z)) {
					site.putShaped(x, y, z, CastleBlocks.BRICK_WALL);
				}
			}
		}
		int k = CastleLayout.Flight.HOLE_FROM - 1;
		for (int lane = CastleLayout.Flight.RAIL_LOW; lane <= CastleLayout.Flight.RAIL_HIGH; lane++) {
			int x = flight.x(k, lane);
			int z = flight.z(k, lane);
			if (plan.kind(x, z) == CastleLayout.OPEN && !plan.walkedByAnother(flight, x, z) && site.get(x, y, z).isAir()) {
				site.putShaped(x, y, z, CastleBlocks.BRICK_WALL);
			}
		}
	}

	// -- The great hall --------------------------------------------------------------------------------------------------

	/** A vaulted nave with a pillar row on each side, galleries, pews and banners. The middle of the floor stays clear for the fight. */
	private static void hall(final CastleSite site, final CastleLayout.FloorPlan plan, final Random random) {
		int slab = EndCastle.slabY(EndCastle.FLOORS);
		int floor = slab + 1;
		int ceiling = slab + TOP;
		for (int x = 5; x <= 26; x++) {
			double reach = Math.abs(x - 15.5);
			int height = reach >= 8.5 ? ceiling - 4 : reach >= 6.5 ? ceiling - 2 : reach >= 4.5 ? ceiling - 1 : ceiling;
			for (int z = 9; z <= 26; z++) {
				for (int y = height + 1; y <= ceiling; y++) {
					if (!plan.stairAt(x, z)) {
						site.put(x, y, z, CastleBlocks.BRICKS);
					}
				}
			}
		}
		for (int x : new int[] {7, 24}) {
			for (int z : new int[] {11, 15, 19}) {
				for (int y = floor; y <= ceiling - 4; y++) {
					site.put(x, y, z, CastleBlocks.PILLAR);
				}
			}
		}
		gallery(site, 5, 6, 7, Direction.EAST, slab, plan);
		gallery(site, 26, 25, 24, Direction.WEST, slab, plan);
		BlockState pew = CastleBlocks.stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, Half.BOTTOM);
		BlockState back = Blocks.DARK_OAK_TRAPDOOR.defaultBlockState();
		for (int[] block : new int[][] {{9, 12}, {20, 22}}) {
			for (int z = 19; z <= 21; z += 2) {
				for (int x = block[0]; x <= block[1]; x++) {
					site.put(x, floor, z, pew);
					site.put(x, floor, z + 1, back.setValue(TrapDoorBlock.FACING, Direction.SOUTH).setValue(TrapDoorBlock.HALF, Half.BOTTOM));
				}
			}
		}
		BlockState banner = Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH);
		for (int x : new int[] {10, 15, 16, 21}) {
			site.put(x, slab + 8, 9, banner);
		}
		BlockState west = Blocks.WALL_BANNER.black().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.EAST);
		BlockState east = Blocks.WALL_BANNER.black().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.WEST);
		for (int z : new int[] {12, 16, 20, 24}) {
			site.put(5, slab + 9, z, west);
			site.put(26, slab + 9, z, east);
		}
		for (int i = 0; i < 6; i++) {
			int x = 6 + random.nextInt(20);
			site.putShaped(x, ceiling, 10 + random.nextInt(2), CastleBlocks.CHAIN);
		}
	}

	/** A ledge along the side wall of the nave, reached by a ladder, over the aisle and behind a row of pillars. */
	private static void gallery(final CastleSite site, final int first, final int second, final int rail, final Direction inward, final int slab, final CastleLayout.FloorPlan plan) {
		BlockState ledge = CastleBlocks.DARK_PLANKS;
		for (int z = 10; z <= 18; z++) {
			site.put(first, slab + 6, z, ledge);
			site.put(second, slab + 6, z, ledge);
			if (z != 11 && z != 15) {
				site.putShaped(rail, slab + 7, z, CastleBlocks.BARS);
			}
		}
		for (int x : new int[] {first, second}) {
			site.putShaped(x, slab + 7, 18, CastleBlocks.BARS);
		}
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, inward);
		for (int up = 1; up <= 7; up++) {
			site.put(first, slab + up, 9, ladder);
		}
	}
}
