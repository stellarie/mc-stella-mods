package com.stella.tower;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** The chests of a built lost castle: how many, whether each is stocked, and whether each double is whole. */
public class TowerLostCastleChestGameTest {
	/** Castles read for each variant and facing. */
	private static final int SEEDS = 2;

	private interface Reading {
		String read(TowerLostRoomGameTest.Arena arena);
	}

	/** Builds a castle of every variant and facing, a few seeds each, and returns the first fault a reading finds. */
	private static String overCastles(final GameTestHelper helper, final Reading reading) {
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				for (int seed = 0; seed < SEEDS; seed++) {
					TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, variant, facing);
					String fault = reading.read(arena);
					if (!fault.isEmpty()) {
						return variant + " " + facing + " seed " + seed + ": " + fault;
					}
				}
			}
		}
		return "";
	}

	private static Iterable<BlockPos> box(final LostRoomPiece piece) {
		BoundingBox box = piece.getBoundingBox();
		return BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	/** Whether every storey holds the chest blocks it is given, the castle holds eight, and each has loot in it. */
	static String count(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int originY = piece.getBoundingBox().minY();
		int[] byStorey = new int[TowerHex.STOREYS];
		int doubles = 0;
		for (BlockPos at : box(piece)) {
			BlockState state = level.getBlockState(at);
			if (!(state.getBlock() instanceof ChestBlock)) {
				continue;
			}
			if (!(level.getBlockEntity(at) instanceof ChestBlockEntity chest) || chest.isEmpty()) {
				return "an empty or missing chest at " + at;
			}
			doubles += state.getValue(ChestBlock.TYPE) == ChestType.LEFT ? 1 : 0;
			byStorey[Math.min(TowerHex.STOREYS - 1, (at.getY() - originY) / TowerHex.STOREY)]++;
		}
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			if (byStorey[storey] != LostCastleHex.chestBlocksAt(storey)) {
				return "storey " + storey + " holds " + byStorey[storey] + " chest blocks, not " + LostCastleHex.chestBlocksAt(storey);
			}
		}
		if (doubles < LostCastleHex.MIN_DOUBLES) {
			return "holds " + doubles + " whole double chests";
		}
		return "";
	}

	@GameTest(maxTicks = 400)
	public void aCastleHoldsExactlyEightStockedChestBlocks(final GameTestHelper helper) {
		String fault = overCastles(helper, TowerLostCastleChestGameTest::count);
		TowerTestSupport.result("castleChests", "wanted=" + LostCastleHex.castleChestBlocks() + " fault=" + fault);
		helper.assertTrue(LostCastleHex.castleChestBlocks() == 8, "the shipped chest budget is not eight");
		helper.assertTrue(fault.isEmpty(), fault);
		helper.succeed();
	}

	/** Whether each double chest joins its partner, opens as one 54-slot container, faces a clear front and rests on a wall. */
	static String whole(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		// Every double the plan names must stand as a joined pair, so a plan whose halves were written single is caught.
		for (LostCastlePlan.FloorPlan floor : piece.plan().floors()) {
			for (LostCastlePlan.Chest site : floor.chests()) {
				BlockPos half = piece.worldPos(site.x(), TowerHex.floorAt(floor.storey()) + 1, site.z());
				boolean joined = level.getBlockState(half).getBlock() instanceof ChestBlock && level.getBlockState(half).getValue(ChestBlock.TYPE) != ChestType.SINGLE;
				if (site.isDouble() != joined) {
					return "the chest site at " + half + " is " + (site.isDouble() ? "a double written single" : "a single written joined");
				}
			}
		}
		for (BlockPos at : box(piece)) {
			BlockState state = level.getBlockState(at);
			if (!(state.getBlock() instanceof ChestBlock block) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
				continue;
			}
			BlockPos partner = ChestBlock.getConnectedBlockPos(at, state);
			BlockState other = level.getBlockState(partner);
			if (!(other.getBlock() instanceof ChestBlock) || other.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)
				|| other.getValue(ChestBlock.TYPE) != state.getValue(ChestBlock.TYPE).getOpposite()) {
				return "the half at " + at + " has no matching partner";
			}
			Container container = ChestBlock.getContainer(block, state, level.getLevel(), at, true);
			if (container == null || container.getContainerSize() != 54) {
				return "the double at " + at + " opens as " + (container == null ? "nothing" : container.getContainerSize() + " slots");
			}
			Direction facing = state.getValue(ChestBlock.FACING);
			if (!level.getBlockState(at.relative(facing)).isAir() || level.getBlockState(at.relative(facing.getOpposite())).isAir()) {
				return "the half at " + at + " has no clear front or no wall behind";
			}
		}
		return "";
	}

	@GameTest(maxTicks = 400)
	public void everyDoubleChestIsWhole(final GameTestHelper helper) {
		String fault = overCastles(helper, TowerLostCastleChestGameTest::whole);
		TowerTestSupport.result("castleDoubles", "fault=" + fault);
		helper.assertTrue(fault.isEmpty(), fault);
		helper.succeed();
	}

	/** Whether every sealed pocket column is stone through all of its storeys. */
	static String pockets(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int pockets = 0;
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				if (!TowerHex.isPocket(x, z)) {
					continue;
				}
				pockets++;
				for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
					for (int y = TowerHex.floorAt(storey) + 1; y < TowerHex.ceilingAt(storey); y++) {
						if (level.getBlockState(piece.worldPos(x, y, z)).isAir()) {
							return "the pocket column " + x + "," + z + " is open at " + y;
						}
					}
				}
			}
		}
		return pockets == 0 ? "the hexagon holds no pocket columns" : "";
	}

	@GameTest(maxTicks = 400)
	public void pocketsAreSolid(final GameTestHelper helper) {
		String fault = overCastles(helper, TowerLostCastleChestGameTest::pockets);
		TowerTestSupport.result("castlePockets", "fault=" + fault);
		helper.assertTrue(fault.isEmpty(), fault);
		helper.succeed();
	}
}
