package com.stella.end;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

/** The courtyard and the rest area are safe ground: neither the floor spawner nor the vanilla spawner puts a mob there. */
public class CastleSpawnGameTest {
	private static boolean inCourtyard(final BlockPos at) {
		CastleLayout.Rect court = CastleLayout.COURTYARD;
		return at.getX() >= EndCastle.MIN_X + court.x0() && at.getX() <= EndCastle.MIN_X + court.x1()
			&& at.getZ() >= EndCastle.MIN_Z + court.z0() && at.getZ() <= EndCastle.MIN_Z + court.z1()
			&& at.getY() > EndCastle.slabY(1) && at.getY() < EndCastle.slabY(4);
	}

	private static boolean inRestArea(final BlockPos at) {
		CastleLayout.Rect room = CastleLayout.REST_ROOM;
		return at.getX() >= EndCastle.MIN_X + room.x0() && at.getX() <= EndCastle.MIN_X + room.x1()
			&& at.getZ() >= EndCastle.MIN_Z + room.z0() && at.getZ() <= EndCastle.MIN_Z + room.z1()
			&& at.getY() > EndCastle.REST_Y && at.getY() <= EndCastle.REST_Y + 6;
	}

	@GameTest(environment = "mod-stella-end-test:spawn_play", maxTicks = FloorSpawnerGameTest.FILL_TICKS + 200)
	public void noMobSpawnsInTheCourtyardOrTheRestAreaWhileFloorsFillAndTheVanillaSpawnerTries(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.joinOnFloor(helper, end, 1);
		EndGameTestInit.SPAWNS.clear();
		Set<UUID> before = new HashSet<>();
		for (Entity entity : EndTestSupport.entities(end)) {
			before.add(entity.getUUID());
		}
		BlockPos courtyard = new BlockPos(EndCastle.MIN_X + 16, EndCastle.standY(1), EndCastle.MIN_Z + 12);
		BlockPos rest = new BlockPos(EndCastle.MIN_X + 14, EndCastle.REST_Y + 1, EndCastle.MIN_Z + 10);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(FloorSpawner.hostiles(end, 1).size() == FloorSpawner.CAP && FloorSpawner.hostiles(end, 2).size() == FloorSpawner.CAP,
				"floors 1 and 2 are not full yet"))
			.thenExecute(() -> {
				for (int attempt = 0; attempt < 300; attempt++) {
					NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, end, courtyard);
					NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, end, rest);
				}
				int spawnsInside = 0;
				for (EndGameTestInit.Spawn spawn : EndGameTestInit.SPAWNS) {
					spawnsInside += inCourtyard(spawn.at()) || inRestArea(spawn.at()) ? 1 : 0;
				}
				int naturalInside = 0;
				for (Entity entity : EndTestSupport.entities(end)) {
					// A castle mob, a mount included, may walk in through a doorway; only a mob of no castle side is a natural spawn.
					naturalInside += entity instanceof Mob mob && !before.contains(entity.getUUID()) && !Factions.isCastle(mob)
						&& (inCourtyard(entity.blockPosition()) || inRestArea(entity.blockPosition())) ? 1 : 0;
				}
				EndTestSupport.result("spawnPlay", "castleSpawns=" + EndGameTestInit.SPAWNS.size() + " spawnsInside=" + spawnsInside + " mobsInside=" + naturalInside);
				EndTestSupport.fresh(helper);
				helper.assertTrue(EndGameTestInit.SPAWNS.size() >= FloorSpawner.CAP, "only " + EndGameTestInit.SPAWNS.size() + " spawns were seen");
				helper.assertTrue(spawnsInside == 0, spawnsInside + " castle mobs spawned in the courtyard or the rest area");
				helper.assertTrue(naturalInside == 0, naturalInside + " natural spawns stand in the courtyard or the rest area");
			})
			.thenSucceed();
	}
}
