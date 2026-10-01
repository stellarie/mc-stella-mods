package com.stella.villages;

import com.stella.villages.patrol.OutdoorSpot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * Guards and the mage appear under open sky at street level: never under a roof, never on one. The scene sits
 * outside the test area, because the test framework roofs every test area with barriers.
 */
public final class OutdoorSpotGameTest {
	private static final int STREET = 2;
	private static final int ROOF = 5;
	private static final int OFFSET = 96;

	@GameTest
	public void rules(final GameTestHelper helper) {
		BlockPos base = build(helper, 0);
		ServerLevel level = helper.getLevel();
		BlockPos openSpot = base.offset(7, STREET, 1);
		int street = openSpot.getY();
		boolean open = OutdoorSpot.isOutdoor(level, openSpot, street);
		boolean underRoof = OutdoorSpot.isOutdoor(level, base.offset(4, STREET, 4), street);
		boolean onRoof = OutdoorSpot.isOutdoor(level, base.offset(4, ROOF + 1, 4), street);
		PatrolTestSupport.result("outdoorRules", "open=" + open + " underRoof=" + underRoof + " onRoof=" + onRoof);
		clear(level, base);
		helper.assertTrue(open && !underRoof && !onRoof, "open=" + open + " underRoof=" + underRoof + " onRoof=" + onRoof);
		helper.succeed();
	}

	@GameTest
	public void mageOutdoors(final GameTestHelper helper) {
		BlockPos base = build(helper, 32);
		ServerLevel level = helper.getLevel();
		BlockPos bell = base.offset(4, STREET, 4);
		Mob mage = EntityTypes.WITCH.create(level, EntitySpawnReason.EVENT);
		BlockPos spot = OutdoorSpot.near(level, bell, mage);
		boolean outdoor = spot != null && OutdoorSpot.isOutdoor(level, spot, bell.getY());
		boolean onStreet = spot != null && spot.getY() == bell.getY();
		boolean clearOfRoof = spot != null && Math.max(Math.abs(spot.getX() - bell.getX()), Math.abs(spot.getZ() - bell.getZ())) >= 2;
		PatrolTestSupport.result("mageOutdoors", "bell=" + bell + " spot=" + spot + " outdoor=" + outdoor + " onStreet=" + onStreet);
		clear(level, base);
		helper.assertTrue(outdoor && onStreet && clearOfRoof, "the mage spot " + spot + " is not on the street next to the roof");
		helper.succeed();
	}

	/** A 9 by 9 street floor with a 3 by 3 roof over its centre, outside the test area. */
	private static BlockPos build(final GameTestHelper helper, final int shift) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BlockPos.ZERO).offset(OFFSET + shift, 0, OFFSET);
		level.getChunkAt(base);
		level.getChunkAt(base.offset(8, 0, 8));
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				level.setBlockAndUpdate(base.offset(x, STREET - 1, z), Blocks.STONE.defaultBlockState());
			}
		}
		for (int x = 3; x <= 5; x++) {
			for (int z = 3; z <= 5; z++) {
				level.setBlockAndUpdate(base.offset(x, ROOF, z), Blocks.OAK_PLANKS.defaultBlockState());
			}
		}
		return base;
	}

	private static void clear(final ServerLevel level, final BlockPos base) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				level.setBlockAndUpdate(base.offset(x, STREET - 1, z), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(base.offset(x, ROOF, z), Blocks.AIR.defaultBlockState());
			}
		}
	}
}
