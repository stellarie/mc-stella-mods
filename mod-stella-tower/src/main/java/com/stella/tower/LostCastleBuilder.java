package com.stella.tower;

import com.stella.tower.LostCastleGrid.Side;
import com.stella.tower.LostCastlePlan.Alcove;
import com.stella.tower.LostCastlePlan.Door;
import com.stella.tower.LostCastlePlan.Fragment;
import com.stella.tower.LostCastlePlan.Level;
import com.stella.tower.LostCastlePlan.Pillar;
import com.stella.tower.LostCastlePlan.Scatter;
import com.stella.tower.LostCastlePlan.Spawner;
import com.stella.tower.LostCastlePlan.Stair;
import com.stella.tower.LostCastlePlan.Vault;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Lays a lost castle to its plan: the slab and walls of every floor, its rooms, doorways, pillars, broken
 * walls, alcoves, chests and spawners, the stairs between floors, the portal and the vault on the bottom
 * floor, the ring of ruins on the surface, and the stair down from the ring. Every choice comes from the
 * seed and the plan, and none from a block already laid, so a castle built one chunk at a time is the
 * castle built whole.
 */
public final class LostCastleBuilder {
	/** Where the portal stands on the bottom floor: the lower of its two columns, and its row. */
	public static final int PORTAL_X = LostCastleGrid.MID;
	public static final int PORTAL_Z = LostCastleGrid.MID - 1;
	/** One wall block in this many has decayed. */
	public static final int DECAY = 14;
	private static final long DECAY_SALT = 0x4445434159L;
	private static final long RING_SALT = 0x52494E47L;
	private static final long FRAGMENT_SALT = 0x46524147L;

	private LostCastleBuilder() {
	}

	/** A number from a seed and a spot alone, so no chunk of a castle can draw it differently from another. */
	static int hash(final long seed, final int x, final int y, final int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (int) (h >>> 32);
	}

	static void build(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final long seed, final LostCastlePalette palette,
		final LostCastlePlan plan) {
		int floors = plan.floors();
		BlockState wall = palette.wall().defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		piece.fill(level, bb, 0, 0, 0, LostCastleGrid.SIZE - 1, LostCastleGrid.topY(floors), LostCastleGrid.SIZE - 1, wall);
		for (Level floor : plan.levels()) {
			rooms(piece, level, bb, floors, floor, air);
			decay(piece, level, bb, seed, palette, floors, floor);
			lamps(piece, level, bb, floors, floor);
			doors(piece, level, bb, floors, floor, air);
			hiding(piece, level, bb, seed, palette, floors, floor);
			alcoves(piece, level, bb, floors, floor, air);
			spawners(piece, level, bb, palette, floors, floor);
			scatter(piece, level, bb, palette, floors, floor);
		}
		for (Level floor : plan.levels()) {
			if (floor.exit() != null) {
				stair(piece, level, bb, palette, floors, floor.index(), floor.exit(), air);
			}
		}
		portal(piece, level, bb, palette, floors);
		vault(piece, level, bb, palette, floors, plan.vault());
		ring(piece, level, bb, seed, palette, floors, plan.drop());
		entrance(piece, level, bb, palette, floors, plan.drop(), air);
	}

	// ---------------------------------------------------------------- floors

	private static void rooms(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final int floors, final Level floor,
		final BlockState air) {
		int feet = LostCastleGrid.feet(floors, floor.index());
		int head = LostCastleGrid.head(floors, floor.index());
		for (int cz = 0; cz < LostCastleGrid.COLS; cz++) {
			for (int cx = 0; cx < LostCastleGrid.COLS; cx++) {
				piece.fill(level, bb, LostCastleGrid.cellLow(cx), feet, LostCastleGrid.cellLow(cz), LostCastleGrid.cellHigh(cx), head,
					LostCastleGrid.cellHigh(cz), air);
			}
		}
	}

	/** The stone of a floor gone worn: each block of a room's walls, floor and ceiling by its own spot and the seed. */
	private static void decay(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final long seed, final LostCastlePalette palette,
		final int floors, final Level floor) {
		BlockState worn = palette.worn().defaultBlockState();
		int feet = LostCastleGrid.feet(floors, floor.index());
		int head = LostCastleGrid.head(floors, floor.index());
		for (int cz = 0; cz < LostCastleGrid.COLS; cz++) {
			for (int cx = 0; cx < LostCastleGrid.COLS; cx++) {
				int x0 = LostCastleGrid.cellLow(cx) - 1;
				int x1 = LostCastleGrid.cellHigh(cx) + 1;
				int z0 = LostCastleGrid.cellLow(cz) - 1;
				int z1 = LostCastleGrid.cellHigh(cz) + 1;
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
						if (edge) {
							for (int y = feet; y <= head; y++) {
								wear(piece, level, bb, seed, worn, x, y, z);
							}
						} else {
							wear(piece, level, bb, seed, worn, x, feet - 1, z);
							wear(piece, level, bb, seed, worn, x, head + 1, z);
						}
					}
				}
			}
		}
	}

	private static void wear(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final long seed, final BlockState worn,
		final int x, final int y, final int z) {
		if (Math.floorMod(hash(seed ^ DECAY_SALT, x, y, z), DECAY) == 0) {
			piece.set(level, bb, worn, x, y, z);
		}
	}

	/** Tower lamps in the ceiling above each room's lanes. */
	private static void lamps(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final int floors, final Level floor) {
		int head = LostCastleGrid.head(floors, floor.index());
		int[][] spots = {{6, 6}, {6, 2}, {7, 11}, {2, 7}, {11, 6}};
		BlockState lamp = TowerBlocks.TOWER_LAMP.defaultBlockState();
		for (int cz = 0; cz < LostCastleGrid.COLS; cz++) {
			for (int cx = 0; cx < LostCastleGrid.COLS; cx++) {
				for (int[] spot : spots) {
					piece.set(level, bb, lamp, LostCastleGrid.cellLow(cx) + spot[0], head, LostCastleGrid.cellLow(cz) + spot[1]);
				}
			}
		}
	}

	private static void doors(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final int floors, final Level floor,
		final BlockState air) {
		int feet = LostCastleGrid.feet(floors, floor.index());
		int top = feet + LostCastleGrid.DOOR_HEIGHT - 1;
		for (Door door : floor.doors()) {
			int from = LostCastleGrid.DOOR_FROM;
			int to = from + LostCastleGrid.DOOR_WIDTH - 1;
			if (door.alongX()) {
				int x = LostCastleGrid.cellHigh(door.cx()) + 1;
				piece.fill(level, bb, x, feet, LostCastleGrid.cellLow(door.cz()) + from, x + LostCastleGrid.WALL - 1, top,
					LostCastleGrid.cellLow(door.cz()) + to, air);
			} else {
				int z = LostCastleGrid.cellHigh(door.cz()) + 1;
				piece.fill(level, bb, LostCastleGrid.cellLow(door.cx()) + from, feet, z, LostCastleGrid.cellLow(door.cx()) + to, top,
					z + LostCastleGrid.WALL - 1, air);
			}
		}
	}

	/** Pillars, whole or broken off, and wall segments with a ragged top. */
	private static void hiding(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final long seed, final LostCastlePalette palette,
		final int floors, final Level floor) {
		int feet = LostCastleGrid.feet(floors, floor.index());
		BlockState wall = palette.wall().defaultBlockState();
		BlockState worn = palette.worn().defaultBlockState();
		for (Pillar pillar : floor.pillars()) {
			piece.fill(level, bb, pillar.x(), feet, pillar.z(), pillar.x() + 1, feet + pillar.height() - 1, pillar.z() + 1, wall);
			if (pillar.height() < LostCastleGrid.CLEAR) {
				piece.fill(level, bb, pillar.x(), feet + pillar.height() - 1, pillar.z(), pillar.x() + 1, feet + pillar.height() - 1, pillar.z() + 1, worn);
			}
		}
		for (Fragment fragment : floor.fragments()) {
			for (int i = 0; i < fragment.length(); i++) {
				int x = fragment.alongX() ? fragment.x() + i : fragment.x();
				int z = fragment.alongX() ? fragment.z() : fragment.z() + i;
				int height = 1 + Math.floorMod(hash(seed ^ FRAGMENT_SALT, x, floor.index(), z), fragment.height());
				piece.fill(level, bb, x, feet, z, x, feet + height - 1, z, wall);
				piece.set(level, bb, worn, x, feet + height - 1, z);
			}
		}
	}

	/** Recesses in the outer wall; a chest alcove holds a double chest, a niche an old skull. */
	private static void alcoves(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final int floors, final Level floor,
		final BlockState air) {
		int feet = LostCastleGrid.feet(floors, floor.index());
		for (Alcove alcove : floor.alcoves()) {
			Side side = alcove.side();
			int[] first = null;
			int[] second = null;
			for (int w = 0; w < LostCastlePlan.ALCOVE_WIDTH; w++) {
				int x = LostCastlePlan.faceX(alcove.cx(), side, alcove.first() + w);
				int z = LostCastlePlan.faceZ(alcove.cz(), side, alcove.first() + w);
				piece.fill(level, bb, x, feet, z, x, feet + LostCastlePlan.ALCOVE_HEIGHT - 1, z, air);
				if (w == 0) {
					first = new int[] {x, z};
				} else if (w == 1) {
					second = new int[] {x, z};
				}
			}
			if (alcove.chest()) {
				piece.doubleChest(level, bb, feet, first[0], first[1], second[0], second[1], LostCastleGrid.label(side.inX(), side.inZ()));
			} else {
				piece.set(level, bb, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, Math.floorMod(first[0] + first[1], 16)),
					second[0], feet, second[1]);
			}
		}
	}

	private static void spawners(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final LostCastlePalette palette, final int floors,
		final Level floor) {
		int y = LostCastleGrid.feet(floors, floor.index()) + 2;
		for (Spawner spawner : floor.spawners()) {
			piece.spawner(level, bb, spawner.x(), y, spawner.z(), LostCastleSpawner.tag(floor.kind(), palette));
		}
	}

	private static void scatter(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final LostCastlePalette palette, final int floors,
		final Level floor) {
		int feet = LostCastleGrid.feet(floors, floor.index());
		int head = LostCastleGrid.head(floors, floor.index());
		for (Scatter item : floor.scatter()) {
			int x = item.x();
			int z = item.z();
			switch (item.kind()) {
				case CANDLE -> piece.set(level, bb, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, item.arg()),
					x, feet, z);
				case SKULL -> piece.set(level, bb, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, item.arg()), x, feet, z);
				case RUBBLE -> piece.set(level, bb, palette.worn().defaultBlockState(), x, feet, z);
				case MOSS -> piece.set(level, bb, switch (palette) {
					case GREEN -> Blocks.MOSS_CARPET.defaultBlockState();
					case SNOW -> Blocks.SNOW.defaultBlockState();
					case SANDSTONE -> Blocks.DEAD_BUSH.defaultBlockState();
				}, x, feet, z);
				case COBWEB -> piece.set(level, bb, Blocks.COBWEB.defaultBlockState(), x, head, z);
				case CHAIN -> {
					for (int drop = 0; drop < item.arg(); drop++) {
						piece.set(level, bb, Blocks.IRON_CHAIN.defaultBlockState(), x, head - drop, z);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- stairs

	/**
	 * The stair from floor {@code index} to the floor below: twelve steps along an outer wall, each one lower than
	 * the last. The first step is level with the upper floor, so the way down starts half a block at a time. The
	 * slab over each step but the first is open, the lower floor holds the stone under each step, and a low
	 * wall guards the opening on the upper floor.
	 */
	private static void stair(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final LostCastlePalette palette, final int floors,
		final int index, final Stair stair, final BlockState air) {
		int upper = LostCastleGrid.feet(floors, index);
		int lower = LostCastleGrid.feet(floors, index + 1);
		BlockState wall = palette.wall().defaultBlockState();
		BlockState step = palette.stairs().defaultBlockState().setValue(StairBlock.FACING, LostCastleGrid.label(-stair.step(), 0));
		for (int j = 1; j <= LostCastleGrid.FLOOR_HEIGHT; j++) {
			int x = stair.xAt(j);
			int tread = upper - j;
			for (int r = 0; r < LostCastleGrid.STAIR_WIDTH; r++) {
				int z = stair.z0() + r;
				if (tread - 1 >= lower) {
					piece.fill(level, bb, x, lower, z, x, tread - 1, z, wall);
				}
				if (j >= 2) {
					piece.set(level, bb, air, x, upper - 1, z);
					piece.set(level, bb, wall, x, upper, stair.barrierZ());
				}
				piece.set(level, bb, step, x, tread, z);
			}
		}
		int end = stair.xAt(LostCastleGrid.FLOOR_HEIGHT + 1);
		for (int r = 0; r < LostCastleGrid.STAIR_WIDTH; r++) {
			piece.set(level, bb, wall, end, upper, stair.z0() + r);
		}
	}

	// ---------------------------------------------------------------- the bottom floor

	/** The portal, a smaller kin of the tower's: a plinth and steps, a gate of tower frame, lamp pillars and soul braziers. */
	private static void portal(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final LostCastlePalette palette, final int floors) {
		int feet = LostCastleGrid.feet(floors, floors - 1);
		BlockState wall = palette.wall().defaultBlockState();
		BlockState frame = TowerBlocks.TOWER_FRAME.defaultBlockState();
		int x0 = PORTAL_X - 2;
		int x1 = PORTAL_X + 3;
		int z0 = PORTAL_Z - 1;
		int z1 = PORTAL_Z + 2;
		piece.fill(level, bb, x0, feet, z0, x1, feet, z1, wall);
		BlockState steps = palette.stairs().defaultBlockState().setValue(StairBlock.FACING, LostCastleGrid.label(0, -1));
		for (int x = PORTAL_X - 1; x <= PORTAL_X + 2; x++) {
			piece.set(level, bb, steps, x, feet, z1 + 1);
		}
		piece.fill(level, bb, PORTAL_X - 1, feet, PORTAL_Z, PORTAL_X + 2, feet, PORTAL_Z, frame);
		piece.fill(level, bb, PORTAL_X - 1, feet + 1, PORTAL_Z, PORTAL_X - 1, feet + 3, PORTAL_Z, frame);
		piece.fill(level, bb, PORTAL_X + 2, feet + 1, PORTAL_Z, PORTAL_X + 2, feet + 3, PORTAL_Z, frame);
		piece.fill(level, bb, PORTAL_X, feet + 3, PORTAL_Z, PORTAL_X + 1, feet + 3, PORTAL_Z, frame);
		for (int x : new int[] {x0, x1}) {
			piece.fill(level, bb, x, feet + 1, z0, x, feet + 2, z0, wall);
			piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), x, feet + 3, z0);
			piece.set(level, bb, Blocks.SOUL_SOIL.defaultBlockState(), x, feet, z1);
			piece.set(level, bb, Blocks.SOUL_FIRE.defaultBlockState(), x, feet + 1, z1);
		}
		for (int x : new int[] {PORTAL_X - 1, PORTAL_X + 2}) {
			piece.set(level, bb, Blocks.SOUL_LANTERN.defaultBlockState(), x, feet + 4, PORTAL_Z);
		}
		// The frame is laid before the portal blocks, which a missing frame block would break.
		BlockState lit = TowerBlocks.TOWER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
		for (int x = PORTAL_X; x <= PORTAL_X + 1; x++) {
			piece.set(level, bb, lit, x, feet + 1, PORTAL_Z);
			piece.set(level, bb, lit, x, feet + 2, PORTAL_Z);
		}
		piece.set(level, bb, Blocks.CRAFTING_TABLE.defaultBlockState(), x0 - 2, feet, PORTAL_Z - 1);
		piece.set(level, bb, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, LostCastleGrid.label(1, 0)), x0 - 2, feet, PORTAL_Z);
		piece.set(level, bb, Blocks.ANVIL.defaultBlockState(), x0 - 2, feet, PORTAL_Z + 1);
	}

	/**
	 * The hidden vault: a four by four chamber in a corner of the bottom floor, closed by the outer wall and two
	 * partitions. One partition is cracked stone brick, the tell; the chest behind it holds rare loot.
	 */
	private static void vault(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final LostCastlePalette palette, final int floors,
		final Vault vault) {
		int feet = LostCastleGrid.feet(floors, floors - 1);
		BlockState wall = palette.wall().defaultBlockState();
		BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		int xa = Math.min(vault.x0(), vault.wallX());
		int xb = Math.max(vault.x1(), vault.wallX());
		int za = Math.min(vault.z0(), vault.wallZ());
		int zb = Math.max(vault.z1(), vault.wallZ());
		int top = feet + Vault.INSIDE - 1;
		piece.fill(level, bb, xa, feet + Vault.INSIDE, za, xb, feet + Vault.INSIDE, zb, wall);
		piece.fill(level, bb, vault.wallX(), feet, za, vault.wallX(), top, zb, wall);
		piece.fill(level, bb, xa, feet, vault.wallZ(), xb, top, vault.wallZ(), wall);
		if (vault.crackedAlongX()) {
			piece.fill(level, bb, vault.wallX(), feet, vault.z0(), vault.wallX(), top, vault.z1(), cracked);
		} else {
			piece.fill(level, bb, vault.x0(), feet, vault.wallZ(), vault.x1(), top, vault.wallZ(), cracked);
		}
		Direction toward = vault.crackedAlongX()
			? LostCastleGrid.label(vault.lowX() ? 1 : -1, 0)
			: LostCastleGrid.label(0, vault.lowZ() ? 1 : -1);
		piece.vaultChest(level, bb, vault.chestX(), feet, vault.chestZ(), toward);
		piece.set(level, bb, TowerBlocks.TOWER_LAMP.defaultBlockState(), vault.chestX(), feet + Vault.INSIDE, vault.chestZ());
	}

	// ---------------------------------------------------------------- the surface

	private static boolean inPit(final int x, final int z) {
		return x >= LostCastleGrid.MID - 2 && x <= LostCastleGrid.MID + 3 && z >= LostCastleGrid.ENTRY_Z - 2 && z <= LostCastleGrid.ENTRY_Z + 5;
	}

	/** The ring of ruins: a yard, a worn bench, and a broken wall with four gates, green, snowy or sandy by palette. */
	private static void ring(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final long seed, final LostCastlePalette palette,
		final int floors, final int drop) {
		int surface = LostCastleGrid.surface(floors, drop);
		int bottom = LostCastleGrid.topY(floors) + 1;
		BlockState ground = palette.ground().defaultBlockState();
		BlockState wall = palette.wall().defaultBlockState();
		BlockState worn = palette.worn().defaultBlockState();
		double middle = LostCastleGrid.SIZE / 2.0;
		int reach = (int) Math.ceil(LostCastleGrid.WALL_RADIUS) + 1;
		for (int x = LostCastleGrid.MID - reach; x <= LostCastleGrid.MID + 1 + reach; x++) {
			for (int z = LostCastleGrid.MID - reach; z <= LostCastleGrid.MID + 1 + reach; z++) {
				double dx = x + 0.5 - middle;
				double dz = z + 0.5 - middle;
				double radius = Math.hypot(dx, dz);
				if (radius > LostCastleGrid.WALL_RADIUS) {
					continue;
				}
				groundColumn(piece, level, bb, radius > LostCastleGrid.BENCH_RADIUS ? wall : ground, x, surface - 1, z, bottom);
				int roll = Math.floorMod(hash(seed ^ RING_SALT, x, surface, z), 100);
				if (radius <= LostCastleGrid.ARENA_RADIUS) {
					if (!inPit(x, z)) {
						yardDecor(piece, level, bb, palette, roll, x, surface, z);
					}
				} else if (radius <= LostCastleGrid.BENCH_RADIUS) {
					if (roll < 60) {
						piece.set(level, bb, roll < 12 ? worn : wall, x, surface, z);
					}
				} else {
					ringWall(piece, level, bb, seed, palette, dx, dz, roll, x, surface, z);
				}
			}
		}
	}

	/** The ground of a ring column, held up on whatever is empty under it, down to the cover over the top floor. */
	private static void groundColumn(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final BlockState block, final int x,
		final int top, final int z, final int bottom) {
		piece.set(level, bb, block, x, top, z);
		for (int y = top - 1; y >= bottom; y--) {
			BlockState below = piece.get(level, bb, x, y, z);
			if (!below.isAir() && below.getFluidState().isEmpty() && !below.canBeReplaced()) {
				break;
			}
			piece.set(level, bb, Blocks.DIRT.defaultBlockState(), x, y, z);
		}
	}

	private static void yardDecor(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final LostCastlePalette palette, final int roll,
		final int x, final int y, final int z) {
		BlockState decor = switch (palette) {
			case GREEN -> roll < 10 ? flower(roll) : roll < 26 ? Blocks.SHORT_GRASS.defaultBlockState() : roll < 30 ? Blocks.FERN.defaultBlockState() : null;
			case SNOW -> roll < 35 ? Blocks.SNOW.defaultBlockState() : null;
			case SANDSTONE -> roll < 4 ? Blocks.DEAD_BUSH.defaultBlockState() : null;
		};
		if (decor != null) {
			piece.set(level, bb, decor, x, y, z);
		}
	}

	private static BlockState flower(final int roll) {
		return switch (roll % 5) {
			case 0 -> Blocks.POPPY.defaultBlockState();
			case 1 -> Blocks.DANDELION.defaultBlockState();
			case 2 -> Blocks.AZURE_BLUET.defaultBlockState();
			case 3 -> Blocks.CORNFLOWER.defaultBlockState();
			default -> Blocks.OXEYE_DAISY.defaultBlockState();
		};
	}

	/** A segment of the ring wall: four gates, a taller pillar every third segment, and a ragged top. */
	private static void ringWall(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final long seed, final LostCastlePalette palette,
		final double dx, final double dz, final int roll, final int x, final int surface, final int z) {
		int segments = 24;
		int segment = Math.floorMod((int) Math.floor(Math.atan2(dz, dx) / (2.0 * Math.PI) * segments), segments);
		if (segment % 6 == 0) {
			return;
		}
		boolean pillar = segment % 3 == 1;
		int base = 2 + Math.floorMod(hash(seed ^ RING_SALT, segment, 7, 7), 4);
		int height = pillar ? base + 2 : Math.max(1, base - Math.floorMod(hash(seed ^ RING_SALT, x, 3, z), 3));
		BlockState wall = palette.wall().defaultBlockState();
		BlockState worn = palette.worn().defaultBlockState();
		for (int h = 0; h < height; h++) {
			piece.set(level, bb, roll < 14 + h * 3 ? worn : wall, x, surface + h, z);
		}
		BlockState cap = switch (palette) {
			case GREEN -> roll % 4 == 0 ? Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true) : null;
			case SNOW -> roll % 3 != 0 ? Blocks.SNOW.defaultBlockState() : null;
			case SANDSTONE -> null;
		};
		if (cap != null) {
			piece.set(level, bb, cap, x, surface + height, z);
		}
	}

	/**
	 * The stair from the ring's middle down to the top floor, two wide. The first steps lie in an open trench
	 * between low walls with a soul torch on each wall; past the ring the trench is roofed. The steps fall one
	 * block a column, so the last is on the top floor's slab and the landing beyond it is the floor.
	 */
	private static void entrance(final LostRoomPiece piece, final WorldGenLevel level, final BoundingBox bb, final LostCastlePalette palette, final int floors,
		final int drop, final BlockState air) {
		int surface = LostCastleGrid.surface(floors, drop);
		int roof = LostCastleGrid.topY(floors);
		int lowFloor = LostCastleGrid.feet(floors, 0);
		BlockState wall = palette.wall().defaultBlockState();
		BlockState step = palette.stairs().defaultBlockState().setValue(StairBlock.FACING, LostCastleGrid.label(0, -1));
		int west = LostCastleGrid.MID - 1;
		int east = LostCastleGrid.MID + 2;
		// The head of the stair: a post each side with a soul torch on it.
		for (int side : new int[] {west, east}) {
			piece.fill(level, bb, side, surface - 1, LostCastleGrid.ENTRY_Z, side, surface, LostCastleGrid.ENTRY_Z, wall);
			piece.set(level, bb, Blocks.SOUL_TORCH.defaultBlockState(), side, surface + 1, LostCastleGrid.ENTRY_Z);
		}
		for (int j = 1; j <= drop; j++) {
			int z = LostCastleGrid.ENTRY_Z + j;
			int tread = surface - j;
			int airTop = Math.max(tread + 3, roof - 1);
			for (int x = LostCastleGrid.MID; x <= LostCastleGrid.MID + 1; x++) {
				// Inside the top floor the steps stand on stone; above its ceiling the ground holds them.
				if (tread < roof && tread - 1 >= lowFloor) {
					piece.fill(level, bb, x, lowFloor, z, x, tread - 1, z, wall);
				}
				piece.fill(level, bb, x, tread + 1, z, x, airTop, z, air);
				piece.set(level, bb, step, x, tread, z);
				boolean roofed = tread + 4 >= roof && tread + 4 <= surface - 1;
				if (roofed) {
					boolean lamp = j % 4 == 0 && x == LostCastleGrid.MID;
					piece.set(level, bb, lamp ? TowerBlocks.TOWER_LAMP.defaultBlockState() : wall, x, tread + 4, z);
				}
			}
			if (tread + 4 >= roof) {
				for (int side : new int[] {west, east}) {
					int low = Math.max(tread, roof);
					int high = Math.min(tread + 4, surface);
					piece.fill(level, bb, side, low, z, side, high, z, wall);
					if (j == 2) {
						piece.set(level, bb, Blocks.SOUL_TORCH.defaultBlockState(), side, surface + 1, z);
					}
				}
			}
		}
	}
}
