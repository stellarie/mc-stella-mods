package com.stella.nether;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The raid's own spawns: blazes out of lava in either mode, wither skeletons anywhere with room in hard. */
public class RaidSpawnsGameTest {
	/** A room well off the hall: a stone floor, and air to the given height. */
	static BlockPos room(final ServerLevel raid, final BlockPos centre, final int height) {
		BlazeSovereignGameTest.hold(raid, centre);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				raid.setBlock(centre.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int y = 0; y < 6; y++) {
					raid.setBlock(centre.offset(x, y, z), y < height ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		return centre;
	}

	@GameTest(environment = "mod-stella-nether-test:spawn_lava", maxTicks = 100)
	public void aBlazeRisesOutOfLavaAndNotOutOfStone(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		ServerLevel raid = (ServerLevel) player.level();
		BlockPos centre = room(raid, RaidInstance.arrival(raid.getServer(), RaidMode.NORMAL).offset(-40, 0, 0), 6);
		raid.setBlock(centre.below(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
		// Searched only through the room, so the run's own lava below cannot answer for the pool.
		Mob fromLava = RaidSpawns.blazeAt(raid, centre.above(2), 0, 3);
		Mob fromStone = RaidSpawns.blazeAt(raid, centre.offset(2, 2, 2), 0, 3);
		boolean onLava = fromLava instanceof Blaze && fromLava.blockPosition().equals(centre);
		NetherTestSupport.result("lavaBlaze", "fromLava=" + (fromLava == null ? null : fromLava.blockPosition()) + " onLava=" + onLava + " fromStone=" + fromStone);
		for (Mob mob : new Mob[] {fromLava, fromStone}) {
			if (mob != null) {
				mob.discard();
			}
		}
		raid.setBlock(centre.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(onLava && fromStone == null, "a blaze from lava=" + onLava + ", one from stone=" + fromStone);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:spawn_wither", maxTicks = 100)
	public void aWitherSkeletonStandsAnywhereWithRoomAndNotInAGap(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		ServerLevel raid = (ServerLevel) player.level();
		BlockPos roomy = room(raid, RaidInstance.arrival(raid.getServer(), RaidMode.NORMAL).offset(-40, 0, 20), 6);
		BlockPos low = room(raid, RaidInstance.arrival(raid.getServer(), RaidMode.NORMAL).offset(-40, 0, 40), 2);
		// Each searched only through its own room.
		Mob inRoom = RaidSpawns.witherAt(raid, roomy.above(2), 0, 4);
		Mob inGap = RaidSpawns.witherAt(raid, low.above(1), 0, 3);
		boolean inGapHere = inGap != null;
		boolean stands = inRoom instanceof WitherSkeleton && inRoom.blockPosition().equals(roomy);
		NetherTestSupport.result("witherAnywhere", "inRoom=" + (inRoom == null ? null : inRoom.blockPosition()) + " stands=" + stands + " inGap=" + inGapHere);
		for (Mob mob : new Mob[] {inRoom, inGap}) {
			if (mob != null) {
				mob.discard();
			}
		}
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(stands && !inGapHere, "a wither skeleton stands in the room=" + stands + ", in the gap=" + inGapHere);
		helper.succeed();
	}

	/** The rate in play: attempts around the arrival on the run's own terrain, as the server tick makes them. */
	static void measure(final GameTestHelper helper, final RaidMode mode, final String name) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, mode);
		ServerLevel raid = (ServerLevel) player.level();
		BlockPos around = RaidInstance.arrival(raid.getServer(), mode);
		for (int x = -RaidSpawns.MAX_DISTANCE; x <= RaidSpawns.MAX_DISTANCE; x += 16) {
			for (int z = -RaidSpawns.MAX_DISTANCE; z <= RaidSpawns.MAX_DISTANCE; z += 16) {
				BlazeSovereignGameTest.hold(raid, around.offset(x, 0, z));
			}
		}
		RandomSource random = RandomSource.create(41L);
		int attempts = 50;
		int blazes = 0;
		int withers = 0;
		List<Entity> spawned = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			for (Mob mob : RaidSpawns.attempt(raid, around, mode, random)) {
				blazes += mob instanceof Blaze ? 1 : 0;
				withers += mob instanceof WitherSkeleton ? 1 : 0;
				spawned.add(mob);
				// Taken away at once, so the local cap never stops an attempt being measured.
				mob.discard();
			}
		}
		NetherTestSupport.result(name, "mode=" + mode + " attempts=" + attempts + " blazes=" + blazes + " withers=" + withers
			+ " everySeconds=" + RaidSpawns.SPAWN_EVERY_TICKS / 20);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(mode == RaidMode.HARD ? withers > 0 : withers == 0, mode + ": " + withers + " wither skeletons in " + attempts + " attempts");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:spawn_rate_normal", maxTicks = 100)
	public void inNormalNoWitherSkeletonSpawnsOutsideAFortress(final GameTestHelper helper) {
		measure(helper, RaidMode.NORMAL, "spawnRateNormal");
	}

	@GameTest(environment = "mod-stella-nether-test:spawn_rate_hard", maxTicks = 100)
	public void inHardWitherSkeletonsSpawnAroundAPlayer(final GameTestHelper helper) {
		measure(helper, RaidMode.HARD, "spawnRateHard");
	}
}
