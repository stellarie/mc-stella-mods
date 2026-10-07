package com.stella.tower;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Every castle is the same hexagonal maze: a six-sided footprint, {@link TowerHex#STOREYS} storeys of ways
 * {@link TowerMaze#PATH} blocks wide, at least {@link LostCastleHex#CHESTS_PER_STOREY} stocked chests on each of
 * them, a breached south wall for its way in, the lost room's portal on the top storey, and no lava anywhere.
 * Every bound is read from the constant the castle ships with, so moving a constant moves its guard.
 */
public class TowerLostCastleHexGameTest {
	/** Castles read for each variant's guards: each a fresh seed, the facings in turn. */
	public static final int SEEDS = 4;

	@GameTest(maxTicks = 40)
	public void theCastleVariantStandsAsAHexagon(final GameTestHelper helper) {
		check(helper, LostCastleVariant.CASTLE, "castleHexFootprint");
	}

	@GameTest(maxTicks = 40)
	public void theKeepVariantStandsAsAHexagon(final GameTestHelper helper) {
		check(helper, LostCastleVariant.KEEP, "castleHexFootprint");
	}

	@GameTest(maxTicks = 40)
	public void theMazeVariantStandsAsAHexagon(final GameTestHelper helper) {
		check(helper, LostCastleVariant.MAZE, "castleHexFootprint");
	}

	@GameTest(maxTicks = 40)
	public void theFloodedVariantStandsAsAHexagon(final GameTestHelper helper) {
		check(helper, LostCastleVariant.FLOODED, "castleHexFootprint");
	}

	@GameTest(maxTicks = 40)
	public void theCryptVariantStandsAsAHexagon(final GameTestHelper helper) {
		check(helper, LostCastleVariant.CRYPT, "castleHexFootprint");
	}

	/** The footprint, the storeys, the six-wide ways and the chests of one castle of each variant. */
	static void check(final GameTestHelper helper, final LostCastleVariant variant, final String test) {
		List<String> faults = new ArrayList<>();
		int sixWide = 0;
		int chested = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(seed * 31L + variant.ordinal()));
			TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, variant, facing);
			if (seed == 0 && variant == LostCastleVariant.CASTLE) {
				TowerLostRoomGameTest.dump(helper, arena, "lost_castle_hex.txt");
			}
			faults.addAll(footprint(arena));
			faults.addAll(storeys(arena));
			String width = ways(arena);
			if (width.isEmpty()) {
				sixWide++;
			} else {
				faults.add("seed " + seed + " " + width);
			}
			String chests = chests(arena);
			if (chests.isEmpty()) {
				chested++;
			} else {
				faults.add("seed " + seed + " " + chests);
			}
		}
		TowerTestSupport.result(test, "variant=" + variant + " seeds=" + SEEDS + " sixWide=" + sixWide + " chested=" + chested + " faults=" + faults);
		helper.assertTrue(faults.isEmpty(), variant + " castles fail: " + String.join("; ", faults));
		helper.succeed();
	}

	/**
	 * Whether the castle stands as the hexagon and no other shape. Every column of the piece is read at the
	 * ground storey's feet and compared with {@link TowerHex#kind}: a way is open, anything inside the
	 * hexagon is a block, and anything outside it is open. The rows read back must also be the rows
	 * {@link TowerHex#widthAt} draws, and that shape must be symmetric about the piece's middle.
	 */
	static List<String> footprint(final TowerLostRoomGameTest.Arena arena) {
		List<String> faults = new ArrayList<>();
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int feet = TowerHex.floorAt(0) + 1;
		int open = 0;
		int solid = 0;
		int outsideHeld = 0;
		List<String> blocked = new ArrayList<>();
		for (int x = -2; x <= LostRoomPiece.LENGTH + 1; x++) {
			for (int z = -2; z <= LostRoomPiece.WIDTH + 1; z++) {
				boolean held = !level.getBlockState(piece.worldPos(x, feet, z)).isAir();
				TowerHex.Part part = TowerHex.kind(x, z);
				if (part == TowerHex.Part.OUT) {
					outsideHeld += held ? 1 : 0;
				} else if (part == TowerHex.Part.WAY) {
					open += held ? 0 : 1;
					if (held) {
						blocked.add(x + "," + z);
					}
				} else {
					solid += held ? 1 : 0;
				}
			}
		}
		int rows = 0;
		int sway = 0;
		for (int z = 0; z <= LostRoomPiece.WIDTH; z++) {
			int width = TowerHex.widthAt(z);
			int low = TowerHex.middleX() - width / 2;
			int held = 0;
			for (int x = low; x < low + width; x++) {
				held += !level.getBlockState(piece.worldPos(x, feet, z)).isAir() ? 1 : 0;
			}
			rows += held > 0 ? 1 : 0;
			if (low != TowerHex.middleX() - TowerHex.widthAt(LostRoomPiece.WIDTH - z) / 2) {
				sway++;
			}
		}
		int top = TowerHex.ceilingAt(TowerHex.STOREYS - 1);
		int outside = 0;
		for (int x = -1; x <= LostRoomPiece.LENGTH; x++) {
			for (int z = -1; z <= LostRoomPiece.WIDTH; z++) {
				outside += TowerHex.kind(x, z) == TowerHex.Part.OUT && !level.getBlockState(piece.worldPos(x, top, z)).isAir() ? 1 : 0;
			}
		}
		if (outsideHeld != 0) {
			faults.add("holds " + outsideHeld + " blocks outside the hexagon");
		}
		if (open != 0) {
			faults.add("walls off " + open + " blocks of a way at " + blocked);
		}
		if (rows != LostRoomPiece.WIDTH) {
			faults.add("stands nothing on " + (LostRoomPiece.WIDTH - rows) + " rows of the frame");
		}
		if (sway != 0) {
			faults.add("the footprint is not symmetric about its middle: " + sway + " rows");
		}
		if (outside != 0) {
			faults.add("stands " + outside + " blocks outside the hexagon on its own roof");
		}
		TowerTestSupport.result("castleHexRows", "rows=" + rows + "/" + LostRoomPiece.WIDTH + " open=" + open + " solid=" + solid + " outside="
			+ outsideHeld + " roofOutside=" + outside);
		return faults;
	}

	/**
	 * Whether the castle is {@link TowerHex#STOREYS} storeys on foot: every storey's cell middles are
	 * standable and reached from the breach, every storey below the top one reaches the storey above it, and
	 * the lost room's portal stands on the top storey, lit, with the workstations beside it.
	 */
	static List<String> storeys(final TowerLostRoomGameTest.Arena arena) {
		List<String> faults = new ArrayList<>();
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		BoundingBox box = piece.getBoundingBox();
		int originY = box.minY();
		int top = TowerHex.STOREYS - 1;
		for (int storey = 0; storey <= top; storey++) {
			int y = originY + TowerHex.floorAt(storey) + 1;
			BlockPos first = null;
			for (TowerHex.Cell cell : TowerHex.cells()) {
				BlockPos at = piece.worldPos(cell.middle().getX(), 0, cell.middle().getZ()).atY(y);
				if (first == null) {
					first = at;
				}
				if (!TowerLostCastleVariantGameTest.standable(level, at)) {
					faults.add("storey " + storey + " cell " + cell.cx() + ',' + cell.cz() + " is not standable at " + at);
				}
			}
			if (storey == 0) {
				// The breach leads onto the ground storey, and every way of it is reached from there.
				Set<BlockPos> fromBreach = TowerLostCastleVariantGameTest.walk(level, box, originY - 1, piece.gateInside());
				StringBuilder probes = new StringBuilder();
				int through = TowerHex.cellAt(0) - 1;
				for (int dx = 0; dx < 2; dx++) {
					for (int dz = through; dz < through + 4; dz++) {
						BlockPos at = piece.worldPos(LostCastleHex.breachLow() + dx, 1, dz);
						probes.append(' ').append(at.getX()).append(',').append(at.getZ()).append('=')
							.append(level.getBlockState(at).getBlock().getName().getString()).append('/')
							.append(level.getBlockState(at.below()).getBlock().getName().getString()).append(" stand=")
							.append(TowerLostCastleVariantGameTest.standable(level, at)).append(" seen=").append(fromBreach.contains(at));
					}
				}
				TowerTestSupport.result("castleHexProbe", "gate=" + piece.gateInside() + " reached=" + fromBreach.size() + probes);
				for (TowerHex.Cell cell : TowerHex.cells()) {
					BlockPos at = piece.worldPos(cell.middle().getX(), 0, cell.middle().getZ()).atY(y);
					if (!fromBreach.contains(at)) {
						faults.add("the breach does not lead to cell " + cell.cx() + ',' + cell.cz());
					}
				}
			}
			if (storey > 0) {
				Set<BlockPos> fromBelow = TowerLostCastleVariantGameTest.walk(level, box, originY - 1,
					piece.worldPos(TowerHex.cells().get(0).middle().getX(), 0, TowerHex.cells().get(0).middle().getZ())
						.atY(originY + TowerHex.floorAt(storey - 1) + 1));
				if (!fromBelow.contains(first)) {
					faults.add("storey " + (storey - 1) + " does not reach storey " + storey + " on foot");
				}
			}
		}
		BlockPos portal = piece.portalPos();
		boolean lit = level.getBlockState(portal).is(TowerBlocks.TOWER_PORTAL) && level.getBlockState(portal.above()).is(TowerBlocks.TOWER_PORTAL)
			&& level.getBlockState(portal.below()).is(TowerBlocks.TOWER_FRAME) && level.getBlockState(portal.above(2)).is(TowerBlocks.TOWER_FRAME);
		int portalStorey = (portal.getY() - originY - 1) / TowerHex.STOREY;
		Set<Block> nearPortal = new LinkedHashSet<>();
		for (BlockPos at : BlockPos.betweenClosed(portal.offset(-3, -1, -3), portal.offset(3, 2, 3))) {
			nearPortal.add(level.getBlockState(at).getBlock());
		}
		boolean stations = nearPortal.contains(Blocks.CRAFTING_TABLE) && nearPortal.contains(Blocks.FURNACE) && nearPortal.contains(Blocks.CANDLE);
		TowerTestSupport.result("castleHexStoreys", "storeys=" + TowerHex.STOREYS + " portalStorey=" + portalStorey + " lit=" + lit
			+ " stations=" + stations);
		if (portalStorey != top) {
			faults.add("the lost room's portal stands on storey " + portalStorey + ", not the top one " + top);
		}
		if (!lit || !stations) {
			faults.add("the portal is not lit or its workstations are missing: lit=" + lit + " stations=" + stations);
		}
		return faults;
	}

	/** Whether every way of every storey is a path {@link TowerMaze#PATH} wide, measured at its own middle. */
	static String ways(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int narrow = 0;
		int wide = 0;
		int measured = 0;
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			for (TowerHex.Cell cell : TowerHex.cells()) {
				int y = piece.getBoundingBox().minY() + TowerHex.floorAt(storey) + 1;
				BlockPos at = piece.worldPos(cell.middle().getX(), 0, cell.middle().getZ()).atY(y);
				int span = Math.min(openSpan(level, at, Direction.Axis.X), openSpan(level, at, Direction.Axis.Z));
				measured++;
				narrow += span < TowerMaze.PATH ? 1 : 0;
				wide += span > TowerMaze.PATH ? 1 : 0;
			}
		}
		if (narrow != 0) {
			return narrow + " of " + measured + " ways are under the " + TowerMaze.PATH + " wide path";
		}
		if (wide != 0) {
			return wide + " of " + measured + " ways are wider than the " + TowerMaze.PATH + " wide path";
		}
		return "";
	}

	/** How many open blocks a line holds from a spot along an axis, the spot itself counted. */
	static int openSpan(final ServerLevel level, final BlockPos at, final Direction.Axis axis) {
		Direction way = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		int span = 1;
		for (int step = 1; step <= LostRoomPiece.LENGTH; step++) {
			if (!TowerLostCastleVariantGameTest.passable(level, at.relative(way, step), true)) {
				break;
			}
			span++;
		}
		for (int step = 1; step <= LostRoomPiece.LENGTH; step++) {
			if (!TowerLostCastleVariantGameTest.passable(level, at.relative(way, -step), true)) {
				break;
			}
			span++;
		}
		return span;
	}

	/** Whether every storey holds at least {@link LostCastleHex#CHESTS_PER_STOREY} chests, each with loot in it. */
	static String chests(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int originY = piece.getBoundingBox().minY();
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			int y = originY + TowerHex.floorAt(storey) + 1;
			int stocked = 0;
			for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
				for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
					if (level.getBlockEntity(piece.worldPos(x, 0, z).atY(y)) instanceof ChestBlockEntity chest && !chest.isEmpty()) {
						stocked++;
					}
				}
			}
			if (stocked < LostCastleHex.CHESTS_PER_STOREY) {
				return "storey " + storey + " holds " + stocked + " stocked chests, not " + LostCastleHex.CHESTS_PER_STOREY;
			}
		}
		return "";
	}

	/** Whether any block of the castle is lava: none may be, on any storey or below the floor. */
	static String lava(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int lava = 0;
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				for (int y = piece.lowest() - 2; y < LostRoomPiece.HEIGHT; y++) {
					BlockState state = level.getBlockState(piece.worldPos(x, y, z));
					lava += state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) ? 1 : 0;
				}
			}
		}
		return lava == 0 ? "" : "holds " + lava + " blocks of lava";
	}

	@GameTest(maxTicks = 40)
	public void noCastleHoldsLava(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, variant, Direction.NORTH);
			String fault = lava(arena);
			if (!fault.isEmpty()) {
				faults.add(variant + " " + fault);
			}
		}
		TowerTestSupport.result("castleNoLava", "variants=" + LostCastleVariant.values().length + " faults=" + faults);
		helper.assertTrue(faults.isEmpty(), "a castle holds lava: " + faults);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theBrokenWallEntranceIsOpen(final GameTestHelper helper) {
		TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, LostCastleVariant.CASTLE, Direction.NORTH);
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		BoundingBox box = BoundingBox.infinite();
		int z = TowerHex.cellAt(0) - 1;
		// Rubble dropped in the breach must not close it.
		for (int x = LostCastleHex.breachLow(); x < LostCastleHex.breachLow() + LostCastleHex.BREACH; x++) {
			for (int y = 1; y <= LostCastleHex.BREACH_HEIGHT; y++) {
				piece.set(level, box, Blocks.AIR.defaultBlockState(), x, y, z);
				piece.placeRubble(level, box, RandomSource.create(x), x, y, z);
			}
		}
		BlockPos gate = piece.gateInside();
		boolean open = TowerLostCastleVariantGameTest.standable(level, gate)
			&& TowerLostCastleVariantGameTest.standable(level, gate.relative(Direction.EAST));
		int clear = 0;
		for (int x = LostCastleHex.breachLow(); x < LostCastleHex.breachLow() + LostCastleHex.BREACH; x++) {
			for (int y = 1; y <= LostCastleHex.BREACH_HEIGHT; y++) {
				clear += TowerLostCastleVariantGameTest.passable(level, piece.worldPos(x, y, z), true) ? 1 : 0;
			}
		}
		TowerTestSupport.result("castleBreach", "middle=" + LostCastleHex.breachMiddle() + " z=" + z + " span=" + LostCastleHex.BREACH
			+ " high=" + LostCastleHex.BREACH_HEIGHT + " open=" + open + " clear=" + clear + "/" + (LostCastleHex.BREACH * LostCastleHex.BREACH_HEIGHT));
		helper.assertTrue(open, "rubble blocked the castle's broken wall entrance");
		helper.assertTrue(clear >= LostCastleHex.BREACH * LostCastleHex.BREACH_HEIGHT - LostCastleHex.BREACH_RUBBLE,
			"the breach is walled off: only " + clear + " of its blocks are open");
		helper.succeed();
	}

	/** The shape the geometry draws, printed into the run log: the record the footprint is read against. */
	@GameTest(maxTicks = 20)
	public void theGeometryPrintsItsOwnShapeForTheRecord(final GameTestHelper helper) {
		StringBuilder out = new StringBuilder();
		int ways = 0;
		for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
			for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
				TowerHex.Part part = TowerHex.kind(x, z);
				out.append(part == TowerHex.Part.OUT ? '.' : part == TowerHex.Part.WAY ? 'o' : '#');
				ways += part == TowerHex.Part.WAY ? 1 : 0;
			}
			out.append('\n');
		}
		TowerTestSupport.result("castleShapeDump", "ways=" + ways + " cells=" + TowerHex.cells().size() + " middle=" + TowerHex.middleX() + ','
			+ TowerHex.middleZ() + "\n" + out);
		helper.succeed();
	}
}
