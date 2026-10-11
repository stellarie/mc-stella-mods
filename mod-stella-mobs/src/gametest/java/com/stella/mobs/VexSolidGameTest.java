package com.stella.mobs;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/** A vex shut in a glass box with its target outside stays inside: it can no longer pass through blocks. */
public class VexSolidGameTest {
	@GameTest(maxTicks = 200)
	public void aVexCannotFlyThroughAWallToItsTarget(final GameTestHelper helper) {
		for (int x = 0; x <= 4; x++) {
			for (int y = 1; y <= 5; y++) {
				for (int z = 0; z <= 4; z++) {
					boolean shell = x == 0 || x == 4 || y == 1 || y == 5 || z == 0 || z == 4;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		Vex vex = helper.spawn(EntityTypes.VEX, new BlockPos(2, 2, 2));
		Villager target = helper.spawn(EntityTypes.VILLAGER, new BlockPos(8, 2, 2));
		target.setNoAi(true);
		vex.setTarget(target);
		helper.runAfterDelay(160, () -> {
			// Absolute bounds: the test may run rotated, so relative positions do not line up with the box.
			BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1));
			BlockPos b = helper.absolutePos(new BlockPos(3, 4, 3));
			BlockPos at = vex.blockPosition();
			boolean inside = at.getX() >= Math.min(a.getX(), b.getX()) && at.getX() <= Math.max(a.getX(), b.getX())
				&& at.getY() >= Math.min(a.getY(), b.getY()) && at.getY() <= Math.max(a.getY(), b.getY())
				&& at.getZ() >= Math.min(a.getZ(), b.getZ()) && at.getZ() <= Math.max(a.getZ(), b.getZ());
			StellaMobs.LOGGER.info("[BEHAVIOR] test=vexSolid RESULT at={} inside={} noPhysics={}", at, inside, vex.noPhysics);
			helper.assertTrue(inside, "the vex left its glass box to " + at);
			helper.succeed();
		});
	}
}
