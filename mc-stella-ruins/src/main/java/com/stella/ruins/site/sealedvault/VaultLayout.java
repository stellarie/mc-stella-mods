package com.stella.ruins.site.sealedvault;

import com.stella.ruins.site.sealedvault.VaultPlan.Cell;
import com.stella.ruins.site.sealedvault.VaultPlan.Trap;
import com.stella.ruins.site.sealedvault.VaultPlan.TrapKind;
import com.stella.ruins.world.LayoutContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the Sealed Vault. The whole box is a mass of vault wall. The layout then carves two levels of rooms and
 * passages out of it, dresses each room for its role, builds the stairs and the traps, and places the core above
 * the inner vault.
 * <p>
 * A level 1 room has its floor at y8 and its air from y9 to y14. A level 0 room has its floor at y1 and its air
 * from y2 to y7. Every spot below is a local offset inside one 6 x 6 room: u along x, v along z.
 */
public final class VaultLayout {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
	private static final BlockState PLINTH = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	private static final BlockState CRACKED = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	private static final BlockState LANTERN = Blocks.SOUL_LANTERN.defaultBlockState();
	private static final BlockState HANGING = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
	private static final BlockState RAIL = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();

	/** The top air layer of a level. */
	public static final int TOP_0 = 7;
	public static final int TOP_1 = 14;
	/** The floor layer between the levels. */
	public static final int SLAB = 8;
	/** The layers a doorway is clear: 5 high. */
	public static final int DOOR_HEIGHT = 5;

	private VaultLayout() {
	}

	private static int top(final int level) {
		return level == 0 ? TOP_0 : TOP_1;
	}

	public static void draw(final LayoutContext real) {
		Grid ctx = new Grid(real);
		VaultPlan plan = VaultPlan.derive(real.random());
		BlockState wall = VaultBlocks.WALL.defaultBlockState();
		BlockState floor = VaultBlocks.FLOOR.defaultBlockState();
		int mx = VaultPlan.SIZE_X - 1;
		int mz = VaultPlan.SIZE_Z - 1;
		ctx.fill(0, 0, 0, mx, VaultPlan.SIZE_Y - 1, mz, wall);
		ctx.fill(0, 0, 0, mx, 1, mz, floor);
		ctx.fill(0, SLAB, 0, mx, SLAB, mz, floor);
		for (int level = 0; level < VaultPlan.LEVELS; level++) {
			for (int c = 0; c < VaultPlan.CELLS; c++) {
				if (plan.isOpen(level, c)) {
					carveRoom(ctx, plan, level, c);
				}
			}
		}
		carveRoom(ctx, plan, 0, VaultPlan.CENTER_CELL);
		carveCenterDoor(ctx, plan);
		carveDome(ctx);
		dressCandidates(ctx, plan);
		dressLanterns(ctx, plan);
		dressTreasures(ctx, plan);
		for (int stair : plan.stairs()) {
			buildSwitchback(ctx, stair);
		}
		buildCoreRoom(ctx);
		buildVaultCell(ctx);
		for (Trap trap : plan.traps()) {
			buildTrap(ctx, trap);
		}
		buildGate(ctx, plan);
		markRoster(ctx, plan);
		ctx.emit();
	}

	// ---- rooms and passages ----

	private static void carveRoom(final Grid ctx, final VaultPlan plan, final int level, final int cell) {
		int ax = VaultPlan.cellMinX(cell % VaultPlan.GRID);
		int az = VaultPlan.cellMinZ(cell / VaultPlan.GRID);
		int y = VaultPlan.stand(level);
		ctx.fill(ax, y, az, ax + 5, top(level), az + 5, AIR);
		if (cell % VaultPlan.GRID < VaultPlan.GRID - 1 && plan.hasLink(level, cell, 0) && plan.isOpen(level, cell + 1)) {
			ctx.fill(ax + 6, y, az + 2, ax + 6, y + DOOR_HEIGHT - 1, az + 4, AIR);
		}
		if (cell / VaultPlan.GRID < VaultPlan.GRID - 1 && plan.hasLink(level, cell, 1) && plan.isOpen(level, cell + VaultPlan.GRID)) {
			ctx.fill(ax + 2, y, az + 6, ax + 4, y + DOOR_HEIGHT - 1, az + 6, AIR);
		}
	}

	/**
	 * The doorway from the inner vault to its neighbour on level 0, three wide and five high like every other, so the
	 * bottom floor is reached by a walk along its own level as well as by the spiral.
	 */
	private static void carveCenterDoor(final Grid ctx, final VaultPlan plan) {
		int ax = VaultPlan.cellMinX(VaultPlan.CENTER_CELL % VaultPlan.GRID);
		int az = VaultPlan.cellMinZ(VaultPlan.CENTER_CELL / VaultPlan.GRID);
		int y = VaultPlan.stand(0);
		switch (plan.centerDoor()) {
			case 0 -> ctx.fill(ax + 6, y, az + 2, ax + 6, y + DOOR_HEIGHT - 1, az + 4, AIR);
			case 1 -> ctx.fill(ax + 2, y, az + 6, ax + 4, y + DOOR_HEIGHT - 1, az + 6, AIR);
			case 2 -> ctx.fill(ax - 1, y, az + 2, ax - 1, y + DOOR_HEIGHT - 1, az + 4, AIR);
			default -> ctx.fill(ax + 2, y, az - 1, ax + 4, y + DOOR_HEIGHT - 1, az - 1, AIR);
		}
	}

	private static void carveDome(final Grid ctx) {
		int ax = VaultPlan.cellMinX(3);
		int az = VaultPlan.cellMinZ(3);
		ctx.fill(ax + 1, 15, az + 1, ax + 4, 17, az + 4, AIR);
		ctx.fill(ax + 2, 18, az + 2, ax + 3, 20, az + 3, AIR);
	}

	private static void buildGate(final Grid ctx, final VaultPlan plan) {
		int ax = VaultPlan.cellMinX(plan.entrance());
		int gateZ = VaultPlan.EXTENSION + VaultPlan.OFFSET;
		ctx.fill(ax + 2, 9, gateZ, ax + 4, 13, gateZ, AIR);
		ctx.fill(ax + 1, 9, gateZ, ax + 1, 13, gateZ, CRYING);
		ctx.fill(ax + 5, 9, gateZ, ax + 5, 13, gateZ, CRYING);
		ctx.fill(ax + 1, 14, gateZ, ax + 5, 14, gateZ, CRYING);
		ctx.fill(ax + 3, 9, 0, ax + 3, 11, gateZ - 1, AIR);
		ctx.set(ax + 2, 9, gateZ + 1, LANTERN);
		ctx.set(ax + 4, 9, gateZ + 1, LANTERN);
		ctx.marker(VaultMarkers.ENTRANCE, ax + 3, 9, 0);
	}

	// ---- dressing ----

	private static void dressCandidates(final Grid ctx, final VaultPlan plan) {
		int k = 0;
		for (Cell cell : plan.candidates()) {
			int ax = VaultPlan.cellMinX(cell.i());
			int az = VaultPlan.cellMinZ(cell.j());
			int y = VaultPlan.stand(cell.level());
			for (int[] corner : new int[][] {{0, 0}, {5, 0}, {0, 5}, {5, 5}}) {
				ctx.fill(ax + corner[0], y, az + corner[1], ax + corner[0], y + 1, az + corner[1], CRYING);
				ctx.set(ax + corner[0], y + 2, az + corner[1], LANTERN);
			}
			ctx.fill(ax + 2, y, az + 2, ax + 3, y, az + 3, PLINTH);
			if (plan.holdsSeal(k)) {
				ctx.set(ax + 2, y + 1, az + 2, VaultBlocks.SEAL.defaultBlockState());
				ctx.marker(VaultMarkers.SEAL, ax + 2, y + 1, az + 2);
			} else {
				ctx.set(ax + 2, y, az + 2, CRACKED);
			}
			k++;
		}
	}

	/** A chest of the treasure table in a corner of each treasure room, facing the room. */
	private static void dressTreasures(final Grid ctx, final VaultPlan plan) {
		for (Cell cell : plan.treasures()) {
			ctx.placeChest(VaultPlan.cellMinX(cell.i()) + 4, VaultPlan.stand(cell.level()), VaultPlan.cellMinZ(cell.j()) + 1, Direction.NORTH, VaultMobs.TREASURE_LOOT);
		}
	}

	private static void dressLanterns(final Grid ctx, final VaultPlan plan) {
		for (int level = 0; level < VaultPlan.LEVELS; level++) {
			for (int c = 0; c < VaultPlan.CELLS; c++) {
				int i = c % VaultPlan.GRID;
				int j = c / VaultPlan.GRID;
				if (!plan.isOpen(level, c) || (i + j) % 2 != 0 || c == VaultPlan.CENTER_CELL || plan.isStair(c) || isCandidate(plan, level, c)) {
					continue;
				}
				ctx.set(VaultPlan.cellMinX(i), top(level), VaultPlan.cellMinZ(j), HANGING);
			}
		}
	}

	private static boolean isCandidate(final VaultPlan plan, final int level, final int cell) {
		for (Cell candidate : plan.candidates()) {
			if (candidate.level() == level && candidate.cell() == cell) {
				return true;
			}
		}
		return false;
	}

	// ---- stairs ----

	private static BlockState stair(final Direction ascend) {
		return Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, ascend);
	}

	/**
	 * A stair cell: six steps climb along the west lane toward +z, a seventh turns east at the top. The slab is open
	 * over the whole lane, so the Warden clears the ceiling. A low rail keeps a walker on level 1 out of the
	 * stairwell. The plan keeps the west wall of a stair cell closed, so no doorway opens onto the lane.
	 */
	private static void buildSwitchback(final Grid ctx, final int cell) {
		int ax = VaultPlan.cellMinX(cell % VaultPlan.GRID);
		int az = VaultPlan.cellMinZ(cell / VaultPlan.GRID);
		ctx.fill(ax, SLAB, az, ax + 1, SLAB, az + 5, AIR);
		for (int s = 0; s <= 5; s++) {
			for (int u = 0; u <= 1; u++) {
				if (s > 0) {
					ctx.fill(ax + u, 2, az + s, ax + u, 1 + s, az + s, BRICKS);
				}
				ctx.set(ax + u, 2 + s, az + s, stair(Direction.SOUTH));
			}
		}
		for (int v = 4; v <= 5; v++) {
			ctx.fill(ax + 2, 2, az + v, ax + 2, 7, az + v, BRICKS);
			ctx.set(ax + 2, SLAB, az + v, stair(Direction.EAST));
		}
		ctx.fill(ax + 2, 9, az, ax + 2, 9, az + 3, RAIL);
	}

	/**
	 * The spiral from the core room down to the inner vault, in the south-west corner of both rooms. Seven steps
	 * wind round a 2 x 2 well. The slab is open over two of its cells, and the last step sits in the slab layer.
	 */
	private static void spiral(final Grid ctx, final int ax, final int az) {
		int[][] ring = {{1, 0}, {1, 1}, {0, 1}, {0, 0}};
		Direction[] ascend = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
		for (int s = 0; s <= 6; s++) {
			int[] at = ring[s % 4];
			if (s < 4 && s > 0) {
				ctx.fill(ax + at[0], 2, az + at[1], ax + at[0], 1 + s, az + at[1], BRICKS);
			}
			ctx.set(ax + at[0], 2 + s, az + at[1], stair(ascend[s % 4]));
		}
		ctx.set(ax + 1, SLAB, az, AIR);
		ctx.set(ax + 1, SLAB, az + 1, AIR);
		ctx.set(ax + 2, 9, az, RAIL);
		ctx.set(ax + 2, 9, az + 1, RAIL);
		ctx.set(ax + 1, 9, az + 2, RAIL);
	}

	// ---- the core room and the inner vault ----

	private static void buildCoreRoom(final Grid ctx) {
		int ax = VaultPlan.cellMinX(3);
		int az = VaultPlan.cellMinZ(3);
		ctx.fill(ax + 2, 9, az + 2, ax + 3, 9, az + 3, PLINTH);
		ctx.placeCore(ax + 2, 10, az + 2);
		ctx.set(ax + 1, 9, az + 2, CRYING);
		ctx.set(ax + 2, 9, az + 4, CRYING);
		ctx.set(ax + 4, 9, az + 3, CRYING);
		ctx.set(ax + 5, 9, az + 5, CRYING);
		ctx.set(ax + 5, 10, az + 5, LANTERN);
		ctx.set(ax, 9, az + 5, CRYING);
		ctx.set(ax, 10, az + 5, LANTERN);
		spiral(ctx, ax, az);
	}

	private static void buildVaultCell(final Grid ctx) {
		int ax = VaultPlan.cellMinX(3);
		int az = VaultPlan.cellMinZ(3);
		ctx.placeVault(ax + 2, 2, az + 5);
		for (int[] corner : new int[][] {{5, 0}, {0, 5}, {5, 5}}) {
			ctx.fill(ax + corner[0], 2, az + corner[1], ax + corner[0], 3, az + corner[1], CRYING);
			ctx.set(ax + corner[0], 4, az + corner[1], LANTERN);
		}
	}

	// ---- trap corridors ----

	/**
	 * An arrow corridor has a row of plates across its middle and four vents in its walls. A drop corridor has a
	 * row of plates over a pit of covers, 6 wide and 3 long, and the level 0 dead end below holds a catch dais.
	 */
	private static void buildTrap(final Grid ctx, final Trap trap) {
		int ax = VaultPlan.cellMinX(trap.at().i());
		int az = VaultPlan.cellMinZ(trap.at().j());
		int y = VaultPlan.stand(trap.level());
		BlockState plate = (trap.kind() == TrapKind.ARROW ? VaultBlocks.ARROW_PLATE : VaultBlocks.DROP_PLATE).defaultBlockState();
		if (trap.alongX()) {
			for (int v = 0; v <= 5; v++) {
				ctx.set(ax + 3, y, az + v, plate);
			}
		} else {
			for (int u = 0; u <= 5; u++) {
				ctx.set(ax + u, y, az + 3, plate);
			}
		}
		ctx.marker(VaultMarkers.PLATE, ax + 3, y, az + 3);
		if (trap.kind() == TrapKind.ARROW) {
			buildVents(ctx, trap, ax, az, y);
			return;
		}
		BlockState cover = VaultBlocks.PIT_COVER.defaultBlockState();
		BlockState floor = VaultBlocks.FLOOR.defaultBlockState();
		if (trap.alongX()) {
			ctx.fill(ax + 2, SLAB, az, ax + 4, SLAB, az + 5, cover);
			ctx.fill(ax + 2, 2, az, ax + 4, 5, az + 5, floor);
		} else {
			ctx.fill(ax, SLAB, az + 2, ax + 5, SLAB, az + 4, cover);
			ctx.fill(ax, 2, az + 2, ax + 5, 5, az + 4, floor);
		}
	}

	private static void buildVents(final Grid ctx, final Trap trap, final int ax, final int az, final int y) {
		BlockState vent = VaultBlocks.VENT.defaultBlockState();
		int[] along = {1, 4};
		for (int a : along) {
			if (trap.alongX()) {
				ctx.set(ax + a, y + 1, az - 1, vent.setValue(VaultBlocks.VentBlock.FACING, Direction.SOUTH));
				ctx.set(ax + a, y + 1, az + 6, vent.setValue(VaultBlocks.VentBlock.FACING, Direction.NORTH));
			} else {
				ctx.set(ax - 1, y + 1, az + a, vent.setValue(VaultBlocks.VentBlock.FACING, Direction.EAST));
				ctx.set(ax + 6, y + 1, az + a, vent.setValue(VaultBlocks.VentBlock.FACING, Direction.WEST));
			}
		}
	}

	// ---- the roster ----

	private static void markRoster(final Grid ctx, final VaultPlan plan) {
		for (Cell candidate : plan.candidates()) {
			int ax = VaultPlan.cellMinX(candidate.i());
			int az = VaultPlan.cellMinZ(candidate.j());
			ctx.marker(VaultMarkers.GUARDIAN, ax + 1, VaultPlan.stand(candidate.level()), az + 1);
		}
		int cx = VaultPlan.cellMinX(3);
		int cz = VaultPlan.cellMinZ(3);
		ctx.marker(VaultMarkers.GUARDIAN, cx, 9, cz + 4);
		ctx.marker(VaultMarkers.GUARDIAN, cx + 5, 9, cz + 4);
		for (Cell guardian : plan.guardians()) {
			ctx.marker(VaultMarkers.GUARDIAN, VaultPlan.cellMinX(guardian.i()) + 3, VaultPlan.stand(guardian.level()), VaultPlan.cellMinZ(guardian.j()) + 3);
		}
		Cell archer = plan.archer();
		ctx.marker(VaultMarkers.ARCHER, VaultPlan.cellMinX(archer.i()) + 3, VaultPlan.stand(archer.level()), VaultPlan.cellMinZ(archer.j()) + 3);
		ctx.marker(VaultMarkers.WARDEN, VaultPlan.cellMinX(plan.lair() % VaultPlan.GRID) + 3, 2, VaultPlan.cellMinZ(plan.lair() / VaultPlan.GRID) + 3);
	}

	/**
	 * The vault in memory. The layout builds the whole plan here, and {@link #emit} then writes each block once. A
	 * block of wall or floor that has solid wall on all six sides stays the rock the world already holds, so the
	 * layout writes a skin round every room, not the whole mass. A miner still has to break the skin to get in.
	 */
	static final class Grid {
		private static final int SX = VaultPlan.SIZE_X;
		private static final int SY = VaultPlan.SIZE_Y;
		private static final int SZ = VaultPlan.SIZE_Z;

		private final LayoutContext real;
		private final BlockState[] cells = new BlockState[SX * SY * SZ];
		private final java.util.List<int[]> core = new java.util.ArrayList<>();
		private final java.util.List<Chest> chests = new java.util.ArrayList<>();

		/** A chest to place when the grid is written. */
		private record Chest(int x, int y, int z, Direction facing, net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table) {
		}

		Grid(final LayoutContext real) {
			this.real = real;
		}

		private static int index(final int x, final int y, final int z) {
			return (x * SY + y) * SZ + z;
		}

		void set(final int x, final int y, final int z, final BlockState state) {
			if (x >= 0 && x < SX && y >= 0 && y < SY && z >= 0 && z < SZ) {
				this.cells[index(x, y, z)] = state;
			}
		}

		void fill(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final BlockState state) {
			for (int x = Math.max(0, Math.min(x1, x2)); x <= Math.min(SX - 1, Math.max(x1, x2)); x++) {
				for (int y = Math.max(0, Math.min(y1, y2)); y <= Math.min(SY - 1, Math.max(y1, y2)); y++) {
					for (int z = Math.max(0, Math.min(z1, z2)); z <= Math.min(SZ - 1, Math.max(z1, z2)); z++) {
						this.cells[index(x, y, z)] = state;
					}
				}
			}
		}

		void marker(final String name, final int x, final int y, final int z) {
			this.real.marker(name, x, y, z);
		}

		void placeCore(final int x, final int y, final int z) {
			this.core.add(new int[] {0, x, y, z});
		}

		void placeVault(final int x, final int y, final int z) {
			this.core.add(new int[] {1, x, y, z});
		}

		void placeChest(final int x, final int y, final int z, final Direction facing, final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table) {
			this.chests.add(new Chest(x, y, z, facing, table));
		}

		private boolean mass(final int x, final int y, final int z) {
			if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) {
				return true;
			}
			BlockState state = this.cells[index(x, y, z)];
			return state != null && (state.is(VaultBlocks.WALL) || state.is(VaultBlocks.FLOOR));
		}

		private boolean buried(final int x, final int y, final int z) {
			return mass(x - 1, y, z) && mass(x + 1, y, z) && mass(x, y - 1, z) && mass(x, y + 1, z) && mass(x, y, z - 1) && mass(x, y, z + 1);
		}

		void emit() {
			for (int x = 0; x < SX; x++) {
				for (int y = 0; y < SY; y++) {
					for (int z = 0; z < SZ; z++) {
						BlockState state = this.cells[index(x, y, z)];
						if (state == null) {
							continue;
						}
						if ((state.is(VaultBlocks.WALL) || state.is(VaultBlocks.FLOOR)) && buried(x, y, z)) {
							continue;
						}
						this.real.set(x, y, z, state);
					}
				}
			}
			for (int[] placed : this.core) {
				if (placed[0] == 0) {
					this.real.placeCore(placed[1], placed[2], placed[3]);
				} else {
					this.real.placeVault(placed[1], placed[2], placed[3]);
				}
			}
			for (Chest chest : this.chests) {
				this.real.placeChest(chest.x(), chest.y(), chest.z(), chest.facing(), chest.table());
			}
		}
	}
}
