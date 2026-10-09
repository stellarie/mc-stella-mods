package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Every floor's portal is lit when the floor is built, and nothing but a creative player takes it down. */
public class TowerPortalHardeningGameTest {
	private static final int CELLS = 6;

	private static int lit(final ServerLevel tower, final int floor) {
		int lit = 0;
		for (BlockPos inside : TowerLayout.portalInside(tower, floor)) {
			BlockState state = tower.getBlockState(inside);
			if (state.is(TowerBlocks.TOWER_PORTAL) && state.getValue(NetherPortalBlock.AXIS) == Direction.Axis.Z) {
				lit++;
			}
		}
		return lit;
	}

	private static int frame(final ServerLevel tower, final int floor) {
		int held = 0;
		for (BlockPos pos : TowerPlan.at(tower, floor).portalFrame()) {
			held += tower.getBlockState(pos).is(TowerBlocks.TOWER_FRAME) ? 1 : 0;
		}
		return held;
	}

	/** Catches a built floor whose portal waits for fire: an ordinary, an elite and a boss floor stand lit. */
	@GameTest(maxTicks = 200)
	public void everyBuiltFloorsPortalIsLitWithNoFire(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int[] floors = {
			TowerTestSupport.freshFloorWhere(floor -> floor % 5 != 0),
			TowerTestSupport.freshFloorWhere(floor -> floor % 5 == 0 && floor % 10 != 0),
			TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor)
		};
		for (int floor : floors) {
			TowerFloors.ensure(tower, floor);
			boolean fire = TowerLayout.portalInside(tower, floor).stream().anyMatch(pos -> tower.getBlockState(pos).is(Blocks.FIRE));
			int cells = lit(tower, floor);
			TowerTestSupport.result("portalLitAtBuild", "floor=" + floor + " lit=" + cells + "/" + CELLS + " fire=" + fire);
			helper.assertTrue(cells == CELLS && !fire, "floor " + floor + " holds " + cells + " of " + CELLS + " lit portal blocks, fire=" + fire);
		}
		helper.runAfterDelay(100, () -> {
			for (int floor : floors) {
				helper.assertTrue(lit(tower, floor) == CELLS, "floor " + floor + " lost its lit portal after 100 ticks");
			}
			helper.succeed();
		});
	}

	/** Catches a portal that a blast takes out: TNT, a mob's blast and a block's blast leave the 6 cells and the whole frame. */
	@GameTest(maxTicks = 100)
	public void aPortalAndItsFrameSurviveEveryKindOfBlast(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		List<BlockPos> inside = TowerLayout.portalInside(tower, floor);
		Vec3 at = Vec3.atCenterOf(inside.get(0));
		Level.ExplosionInteraction[] kinds = {Level.ExplosionInteraction.TNT, Level.ExplosionInteraction.MOB, Level.ExplosionInteraction.BLOCK};
		for (Level.ExplosionInteraction kind : kinds) {
			tower.explode(null, at.x, at.y, at.z, 12.0F, kind);
		}
		int cells = lit(tower, floor);
		int frame = frame(tower, floor);
		int frameBlocks = TowerPlan.at(tower, floor).portalFrame().size();
		TowerTestSupport.result("portalBlast", "floor=" + floor + " cells=" + cells + "/" + CELLS + " frame=" + frame + "/" + frameBlocks);
		helper.assertTrue(cells == CELLS, "a blast left " + cells + " of " + CELLS + " portal blocks");
		helper.assertTrue(frame == frameBlocks, "a blast left " + frame + " of " + frameBlocks + " frame blocks");
		helper.succeed();
	}

	/** Catches a portal a survival player can mine: it has no destroy time, so no break progress. */
	@GameTest(maxTicks = 100)
	public void aSurvivalPlayerCannotBreakAPortalBlock(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		BlockPos pos = TowerLayout.portalInside(tower, floor).get(0);
		BlockState state = tower.getBlockState(pos);
		float speed = state.getDestroySpeed(tower, pos);
		TowerTestSupport.result("portalBreak", "state=" + state + " destroySpeed=" + speed);
		helper.assertTrue(state.is(TowerBlocks.TOWER_PORTAL) && speed < 0.0F, "the portal block is " + state + " with destroy speed " + speed);
		helper.succeed();
	}
}
