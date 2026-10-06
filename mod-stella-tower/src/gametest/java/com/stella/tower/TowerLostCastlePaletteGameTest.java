package com.stella.tower;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Every castle variant carries the tower environment its blocks are drawn from: one environment per variant,
 * each used once, its wall stone over the castle's own walls and its rubble stone on its ground. The palette
 * is read from the constant the variant ships, so moving a variant's environment moves its guard with it.
 */
public class TowerLostCastlePaletteGameTest {

	@GameTest(maxTicks = 40)
	public void theCastleVariantCarriesItsOwnEnvironment(final GameTestHelper helper) {
		check(helper, LostCastleVariant.CASTLE);
	}

	@GameTest(maxTicks = 40)
	public void theKeepVariantCarriesItsOwnEnvironment(final GameTestHelper helper) {
		check(helper, LostCastleVariant.KEEP);
	}

	@GameTest(maxTicks = 40)
	public void theMazeVariantCarriesItsOwnEnvironment(final GameTestHelper helper) {
		check(helper, LostCastleVariant.MAZE);
	}

	@GameTest(maxTicks = 40)
	public void theFloodedVariantCarriesItsOwnEnvironment(final GameTestHelper helper) {
		check(helper, LostCastleVariant.FLOODED);
	}

	@GameTest(maxTicks = 40)
	public void theCryptVariantCarriesItsOwnEnvironment(final GameTestHelper helper) {
		check(helper, LostCastleVariant.CRYPT);
	}

	/** Builds one castle of a variant and reads its own walls, its own ground and its own rubble back out. */
	static void check(final GameTestHelper helper, final LostCastleVariant variant) {
		LostCastlePalette palette = variant.palette();
		EnumSet<TrapTheme> shipped = EnumSet.noneOf(TrapTheme.class);
		for (LostCastleVariant other : LostCastleVariant.values()) {
			shipped.add(other.palette().environment());
		}
		ServerLevel level = TowerTestSupport.tower(helper);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		List<String> faults = new ArrayList<>();
		Set<Block> walls = new HashSet<>();
		Set<Block> floor = new HashSet<>();
		int rubbleBlocks = 0;
		try {
			TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, variant, net.minecraft.core.Direction.NORTH);
			LostRoomPiece piece = arena.piece();
			int low = piece.lowest();
			int high = LostRoomPiece.HEIGHT - 1;
			for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
				for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
					for (int y = low; y <= high; y++) {
						BlockPos at = piece.worldPos(x, y, z);
						Block block = level.getBlockState(at).getBlock();
						if (y == 0) {
							floor.add(block);
						} else {
							walls.add(block);
						}
						if (block == palette.rubble()) {
							rubbleBlocks++;
						}
					}
				}
			}
			boolean floorKept = floor.contains(palette.wall());
			boolean wallKept = walls.contains(palette.wall());
			boolean rubbleKept = rubbleBlocks > 0;
			TowerTestSupport.result("castlePalette", "variant=" + variant + " environment=" + palette.environment() + " of " + shipped
				+ " wall=" + palette.wall() + " walls=" + wallKept + " floor=" + palette.wall() + " floorKept=" + floorKept
				+ " rubble=" + palette.rubble() + " rubbleBlocks=" + rubbleBlocks + " distinctEnvironments=" + shipped.size());
			if (!wallKept) {
				faults.add(variant + " stands no " + palette.wall() + " on its own walls");
			}
			if (!floorKept) {
				faults.add(variant + " stands no " + palette.wall() + " in its floor");
			}
			if (!rubbleKept) {
				faults.add(variant + " builds its rubble of no " + palette.rubble() + " at all");
			}
			if (!shipped.contains(palette.environment()) || shipped.size() != LostCastleVariant.values().length) {
				faults.add(variant + " does not name one of the five tower environments: " + palette.environment() + " of " + shipped);
			}
		} finally {
			TowerTestSupport.leave(helper, player);
		}
		helper.assertTrue(faults.isEmpty(), String.join("; ", faults));
		helper.succeed();
	}
}
